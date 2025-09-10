import asyncio
import json
import logging
import os
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, Optional, List

from fastapi import Body, FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
from starlette import status
from starlette.middleware.cors import CORSMiddleware

from app.ai.agent import InterviewAgent
from app.db import check_db
from app.repositories.storage import save_video_and_json_refs
from app.s3 import check_s3, upload_file
from app.video_processing.process import (
    finalize_video,
    load_models,
    process_video_balanced,
    save_video_chunk,
)

# --------- логирование и версия мока ----------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("app")

# Пул потоков для блокирующих операций
executor = ThreadPoolExecutor(max_workers=4)

# --------- Vosk STT globals ----------
try:
    from vosk import Model as VoskModel, KaldiRecognizer, SetLogLevel as VoskSetLogLevel
except Exception:
    VoskModel = None  # type: ignore
    KaldiRecognizer = None  # type: ignore


    def VoskSetLogLevel(_: int):
        pass

VOSK_SAMPLE_RATE = 16000
_VOSK_MODEL_CACHE = None


def get_vosk_model():
    global _VOSK_MODEL_CACHE
    if _VOSK_MODEL_CACHE is None:
        # silence Vosk logs
        try:
            VoskSetLogLevel(0)
        except Exception:
            pass
        # resolve model path
        model_path_env = os.getenv("VOSK_MODEL_PATH")
        candidates = [
            model_path_env,
            "/app/models/vosk/ru",
            "./app/models/vosk/ru",
            str(Path(__file__).parent / "app" / "models" / "vosk" / "ru"),
        ]
        model_path = next((p for p in candidates if p and os.path.isdir(p)), None)
        if not model_path:
            raise RuntimeError("Vosk model path not found. Set VOSK_MODEL_PATH or add model to app/models/vosk/ru")
        if VoskModel is None:
            raise RuntimeError("vosk library not available. Ensure it is installed.")
        logger.info(f"Loading Vosk model from {model_path} ...")
        _VOSK_MODEL_CACHE = VoskModel(model_path)
        logger.info("Vosk model loaded")
    return _VOSK_MODEL_CACHE


def _make_envelope(header: dict, payload: bytes) -> bytes:
    """
    Упаковывает бинарный фрейм: 256 байт JSON-заголовка (utf-8, подбит нулями) + payload.
    Совместимо с фронтовым parseBinaryEnvelopeSmart().
    """
    try:
        h = json.dumps(header, ensure_ascii=False).encode("utf-8")
    except Exception:
        h = b"{}"
    if len(h) > 256:
        h = h[:256]
    padded = h.ljust(256, b"\x00")
    return padded + payload


async def _piper_stream_tts(
        websocket: WebSocket,
        text: str,
        *,
        model_file: str | None = None,
        config_file: str | None = None,
        sample_rate: int = 24000,
        channels: int = 1,
        chunk_ms: int = 240,
        cancel_event: asyncio.Event | None = None,
) -> None:
    """
    Стримовое TTS через pip-версию Piper: `python -m piper`.
    Отправляет tts.chunk (PCM16) в бинарных конвертах (256-байтный JSON заголовок + payload).
    Стартует ТОЛЬКО после финального STT (длинные фразы поддерживаются естественно — по паузе).
    """
    import sys

    if not text.strip():
        return

    try:
        logger.info(f"TTS speak: {text[:200]}")
    except Exception:
        pass

    # Берём пути из аргументов или из окружения
    model_file = model_file or os.getenv("PIPER_MODEL_PATH") or os.getenv("PIPER_MODEL_FILE")
    config_file = config_file or os.getenv("PIPER_CONFIG_PATH") or os.getenv("PIPER_CONFIG_FILE")

    # Модель обязательна; конфиг желателен, но можно и без него
    if not model_file or not os.path.isfile(model_file):
        try:
            await websocket.send_json({"type": "system", "event": "error", "message": "Piper model_file not set/found"})
        except Exception:
            pass
        return

    args = [
        sys.executable, "-m", "piper",
        "-m", model_file,
        "--output_raw", text
    ]
    if config_file and os.path.isfile(config_file):
        args += ["-c", config_file]

    # Заголовок для фронта (он уже умеет это играть)
    header = {
        "type": "tts.chunk",
        "codec": "pcm16",
        "sampleRate": sample_rate,
        "channels": channels,
    }
    bytes_per_ms = (sample_rate * channels * 2) // 1000
    chunk_bytes = max(bytes_per_ms * chunk_ms, 1)

    proc = await asyncio.create_subprocess_exec(
        *args,
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    assert proc.stdin is not None and proc.stdout is not None
    # Подаём текст и закрываем stdin — это триггерит синтез
    proc.stdin.write((text.strip() + "\n").encode("utf-8"))
    await proc.stdin.drain()
    proc.stdin.close()

    buf = b""
    try:
        while True:
            if cancel_event is not None and cancel_event.is_set():
                try:
                    proc.kill()
                except Exception:
                    pass
                break
            chunk = await proc.stdout.read(4096)
            if not chunk:
                break
            buf += chunk
            while len(buf) >= chunk_bytes:
                if cancel_event is not None and cancel_event.is_set():
                    try:
                        proc.kill()
                    except Exception:
                        pass
                    buf = b""
                    break
                payload = buf[:chunk_bytes]
                buf = buf[chunk_bytes:]
                # 256-байтный заголовок + payload
                h = json.dumps(header, ensure_ascii=False).encode("utf-8")[:256].ljust(256, b"\x00")
                await websocket.send_bytes(h + payload)

        if buf and not (cancel_event is not None and cancel_event.is_set()):
            h = json.dumps(header, ensure_ascii=False).encode("utf-8")[:256].ljust(256, b"\x00")
            await websocket.send_bytes(h + buf)

    finally:
        try:
            await proc.wait()
        except Exception:
            pass


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Инициализация сервисов на старте приложения
    try:
        load_models()  # подгружаем модели для видео
        await check_db()  # проверяем БД
        check_s3()  # проверяем доступ к S3
        logger.info("Startup checks passed: DB and S3 are reachable.")
        logger.info("MAIN FILE: %s", os.path.abspath(__file__))
    except Exception:
        logger.exception("Startup checks failed: DB or S3 are not reachable.")
        raise
    yield


app = FastAPI(lifespan=lifespan)

# Глобальный AI-агент (LangChain + OpenAI GPT-4o)
AI_AGENT = InterviewAgent()

# Разрешаем CORS для простоты мока
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Отдача статических файлов
app.mount("/static", StaticFiles(directory="static"), name="static")


# --------- менеджер WS-подключений (на будущее) ----------
class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def send_personal_message(self, message: str, websocket: WebSocket):
        await websocket.send_text(message)

    async def broadcast(self, message: str):
        for connection in list(self.active_connections):
            try:
                await connection.send_text(message)
            except Exception:
                pass


manager = ConnectionManager()

# --------- простая in-memory "БД" встреч ----------
MEETINGS: Dict[str, Dict[str, Any]] = {}


def now_iso() -> str:
    return datetime.utcnow().isoformat(timespec="milliseconds") + "Z"


def add_minutes(iso: str, minutes: int) -> str:
    t = datetime.fromisoformat(iso.replace("Z", "+00:00"))
    return (
        (t + timedelta(minutes=minutes))
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


# --------- REST: получение и управление встречей ----------
@app.get("/meetings/{code}")
async def get_meeting(code: str):
    if code not in MEETINGS:
        # Initialize a new meeting with minimal defaults (no hardcoded code/greeting)
        MEETINGS[code] = {
            "code": code,
            "status": "not_started",  # not_started | running | ended
            "candidateName": None,
            # UI pre-start message (not AI greeting)
            "greeting": "Добро пожаловать! Проверьте микрофон и камеру.\nКогда будете готовы - нажмите «Начать».",
            "interviewId": None,
            "token": None,
            "endAt": None,
        }
    return MEETINGS[code]


@app.post("/meetings/{code}/start")
async def start_meeting(
        code: str, payload: Optional[Dict[str, Any]] = Body(default=None)
):
    m = MEETINGS.get(code)
    if not m:
        # Автосоздание встречи для произвольного кода
        m = {
            "code": code,
            "status": "not_started",
            "candidateName": (payload or {}).get("candidateName") if payload else None,
            "interviewId": (payload or {}).get("interviewId") if payload else None,
            "token": None,
            "endAt": None,
        }
    else:
        # Обновляем имя кандидата, если пришло
        if payload and payload.get("candidateName"):
            m["candidateName"] = payload.get("candidateName")

    # Если уже идёт - возвращаем текущие поля
    if m.get("status") == "running":
        return {
            "startAt": m.get("startAt"),
            "endAt": m.get("endAt"),
            "interviewId": m.get("interviewId"),
            "token": m.get("token"),
        }

    # Иначе запускаем новую сессию
    duration = int(payload.get("durationMinutes", 30)) if payload else 30
    start_at = now_iso()
    end_at = add_minutes(start_at, duration)
    interview_id = f"iv_{hex(abs(hash(code + start_at)))[2:8]}"
    token = f"tok_{hex(abs(hash(interview_id + start_at)))[2:]}"

    m.update({"status": "running", "startAt": start_at, "endAt": end_at, "interviewId": interview_id, "token": token})

    # Инициализируем AI-сессию и заранее генерируем вопросы
    try:
        AI_AGENT.ensure_session(code, token, candidate_name=m.get("candidateName"))
    except Exception as e:
        logger.exception("Failed to init AI session")

    MEETINGS[code] = m
    return {"startAt": start_at, "endAt": end_at, "interviewId": interview_id, "token": token}


@app.post("/meetings/{code}/end")
async def end_meeting(code: str):
    m = MEETINGS.get(code)
    if not m:
        m = {
            "code": code,
            "status": "not_started",
            "candidateName": None,
            "interviewId": None,
            "token": None,
            "endAt": None,
        }
    if m.get("status") != "running":
        raise HTTPException(
            status_code=409,
            detail={"error": "not_running", "message": "Meeting not running"},
        )
    m.update({"status": "ended", "endAt": now_iso()})
    MEETINGS[code] = m
    return {"ok": True}


# --------- WebSocket: голосовой шлюз ----------
@app.websocket("/voice")
async def voice_gateway(websocket: WebSocket):
    await websocket.accept()
    # Send readiness event for UI to start streaming
    try:
        await websocket.send_json({"type": "system", "event": "ready"})
    except Exception:
        return

    # Prepare Vosk recognizer
    try:
        model = get_vosk_model()
        if KaldiRecognizer is None:
            raise RuntimeError("vosk library not available")
        rec = KaldiRecognizer(model, VOSK_SAMPLE_RATE)
        # enable words if available (won't break if missing)
        try:
            rec.SetWords(True)  # type: ignore[attr-defined]
        except Exception:
            pass
    except Exception as e:
        logger.exception("Failed to init Vosk recognizer")
        try:
            await websocket.send_json({"type": "system", "event": "error", "message": str(e)})
        finally:
            await websocket.close(code=1011)
        return

    # State
    pcm_queue: asyncio.Queue[bytes | None] = asyncio.Queue(maxsize=16)
    started = False
    ffmpeg = None
    ffmpeg_reader_task: Optional[asyncio.Task] = None
    recognizer_task: Optional[asyncio.Task] = None

    # AI session binding
    session_code: Optional[str] = None
    session_token: Optional[str] = None

    # Agent streaming and TTS control
    tts_cancel_event: asyncio.Event = asyncio.Event()
    agent_stream_task: Optional[asyncio.Task] = None

    bytes_processed = 0
    last_partial: str = ""
    last_final_to_ms = 0

    def _sentence_chunks():
        buf = ""
        enders = ".!?\n"
        while True:
            chunk = yield
            if chunk:
                buf += chunk
                # emit full sentences
                last_emit = 0
                for i, ch in enumerate(buf):
                    if ch in enders:
                        # include the ender
                        sent = buf[: i + 1].strip()
                        rest = buf[i + 1:]
                        if sent:
                            yield ("sentence", sent)
                        buf = rest.lstrip()
                        last_emit = i + 1
            # safe-guard: if buffer too long, split by space
            if len(buf) > 800:
                parts = buf.split(" ")
                head = " ".join(parts[:-1])
                buf = parts[-1]
                if head.strip():
                    yield ("sentence", head.strip())

    async def _stream_agent_and_tts(user_text: Optional[str]):
        nonlocal agent_stream_task
        if not session_code or not session_token:
            return
        # reset cancel flag
        tts_cancel_event.clear()

        async def runner():
            try:
                # streaming LLM tokens
                # send partial text to UI and TTS per sentence
                await websocket.send_json({"type": "ai.start"})
                sent_buffer = ""
                async for text_chunk in AI_AGENT.stream_reply(session_code, session_token, user_text):
                    # if user barged in — stop
                    if tts_cancel_event.is_set():
                        break
                    try:
                        await websocket.send_json({"type": "ai.partial", "text": text_chunk})
                    except Exception:
                        pass
                    sent_buffer += text_chunk
                    # sentence flush
                    flush_sents: List[str] = []
                    tmp = sent_buffer
                    last_split = 0
                    for i, ch in enumerate(tmp):
                        if ch in ".!?\n":
                            s = tmp[last_split: i + 1].strip()
                            if s:
                                flush_sents.append(s)
                            last_split = i + 1
                    sent_buffer = tmp[last_split:].lstrip()
                    for s in flush_sents:
                        if tts_cancel_event.is_set():
                            break
                        try:
                            await _piper_stream_tts(websocket, s, cancel_event=tts_cancel_event)
                        except Exception:
                            logger.exception("TTS (piper) failed for sentence")
                # flush tail
                if not tts_cancel_event.is_set() and sent_buffer.strip():
                    try:
                        await _piper_stream_tts(websocket, sent_buffer.strip(), cancel_event=tts_cancel_event)
                    except Exception:
                        logger.exception("TTS (piper tail) failed")
                if not tts_cancel_event.is_set():
                    try:
                        await websocket.send_json({"type": "ai.final"})
                    except Exception:
                        pass
            except Exception:
                logger.exception("Agent streaming failed")
            finally:
                # mark done
                pass

        agent_stream_task = asyncio.create_task(runner())

    async def recognizer_loop():
        """
        Читает PCM16 из pcm_queue, гоняет через Vosk/KaldiRecognizer,
        присылает stt.partial/stt.final в websocket.
        Управляет барж-ином: если пользователь говорит — останавливаем текущий TTS/агента.
        """
        nonlocal bytes_processed, last_partial, last_final_to_ms

        loop = asyncio.get_event_loop()

        def bytes_to_ms(n_bytes: int) -> int:
            # s16le mono @ 16kHz => 32000 bytes/sec
            return int((n_bytes / (2 * 1 * VOSK_SAMPLE_RATE)) * 1000)

        try:
            while True:
                chunk = await pcm_queue.get()
                if chunk is None:
                    break
                if not chunk:
                    continue

                bytes_processed += len(chunk)

                # Блокирующий KaldiRecognizer.AcceptWaveform гоняем в threadpool
                accepted = await loop.run_in_executor(executor, rec.AcceptWaveform, chunk)
                if accepted:
                    # Финальное распознавание
                    try:
                        res_raw = rec.Result()
                        data = json.loads(res_raw or "{}")
                        text = (data.get("text") or "").strip()
                    except Exception:
                        text = ""

                    current_ms = bytes_to_ms(bytes_processed)

                    if text:
                        logger.info(f"STT final: {text}")
                        # 1) отдаём финальный субтитр
                        try:
                            await websocket.send_json({
                                "type": "stt.final",
                                "text": text,
                                "fromMs": last_final_to_ms,
                                "toMs": current_ms,
                            })
                        except Exception:
                            pass

                        last_partial = ""
                        last_final_to_ms = current_ms

                        # Барж-ин: останавливаем текущий поток TTS/агента и запускаем новый ответ агента
                        try:
                            tts_cancel_event.set()
                            if agent_stream_task:
                                try:
                                    await asyncio.sleep(0)  # give a chance to cancel
                                except Exception:
                                    pass
                            tts_cancel_event.clear()
                            await _stream_agent_and_tts(text)
                        except Exception:
                            logger.exception("Agent stream failed to start")
                    else:
                        # Финал без текста — просто фиксируем таймлайн
                        last_partial = ""
                        last_final_to_ms = current_ms
                else:
                    # паршиалы
                    try:
                        p_raw = rec.PartialResult()
                        pdata = json.loads(p_raw or "{}")
                        ptxt = (pdata.get("partial") or "").strip()
                    except Exception:
                        ptxt = ""

                    if ptxt and ptxt != last_partial:
                        try:
                            await websocket.send_json({
                                "type": "stt.partial",
                                "text": ptxt,
                                "fromMs": last_final_to_ms,
                                "toMs": bytes_to_ms(bytes_processed),
                            })
                        except Exception:
                            pass
                        # Пользователь говорит — останавливаем TTS/агента (барж-ин)
                        try:
                            if agent_stream_task and not agent_stream_task.done():
                                tts_cancel_event.set()
                        except Exception:
                            pass
                        last_partial = ptxt
        finally:
            # Финальный дренаж распознавателя
            try:
                f_raw = rec.FinalResult()
                fdata = json.loads(f_raw or "{}")
                ftxt = (fdata.get("text") or "").strip()
                if ftxt:
                    cur_ms = bytes_to_ms(bytes_processed)
                    logger.info(f"STT final (flush): {ftxt}")
                    try:
                        await websocket.send_json({
                            "type": "stt.final",
                            "text": ftxt,
                            "fromMs": last_final_to_ms,
                            "toMs": cur_ms,
                        })
                    except Exception:
                        pass
                    # Передаём хвост в агента
                    try:
                        tts_cancel_event.set()
                        tts_cancel_event.clear()
                        await _stream_agent_and_tts(ftxt)
                    except Exception:
                        logger.exception("Agent (flush) failed")
            except Exception:
                pass

    async def ensure_ffmpeg():
        nonlocal ffmpeg, ffmpeg_reader_task
        if ffmpeg is not None:
            return ffmpeg
        ffmpeg = await asyncio.create_subprocess_exec(
            'ffmpeg',
            '-hide_banner', '-loglevel', 'error', '-fflags', '+discardcorrupt',
            '-f', 'webm', '-i', 'pipe:0',
            '-vn', '-ac', '1', '-ar', str(VOSK_SAMPLE_RATE),
            '-f', 's16le', 'pipe:1',
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )

        async def read_stdout():
            try:
                while True:
                    if ffmpeg.stdout is None:
                        break
                    data = await ffmpeg.stdout.read(4096)
                    if not data:
                        break
                    await pcm_queue.put(data)
            except Exception:
                pass
            finally:
                await pcm_queue.put(None)

        ffmpeg_reader_task = asyncio.create_task(read_stdout())
        return ffmpeg

    async def close_transcoder():
        nonlocal ffmpeg
        try:
            if ffmpeg and ffmpeg.stdin:
                try:
                    ffmpeg.stdin.close()
                except Exception:
                    pass
            if ffmpeg:
                try:
                    await ffmpeg.wait()
                except Exception:
                    pass
        finally:
            ffmpeg = None

    # Start recognizer consumer
    recognizer_task = asyncio.create_task(recognizer_loop())

    try:
        while True:
            data = await websocket.receive()
            if 'bytes' in data and data['bytes']:
                b = data['bytes']
                if len(b) < 256:
                    continue
                header_raw = b[:256]
                payload = b[256:]
                try:
                    header_str = header_raw.split(b'\x00', 1)[0].decode('utf-8', 'ignore')
                    hdr = json.loads(header_str or '{}')
                except Exception:
                    hdr = {}
                msg_type = (hdr.get('type') or '').lower()
                codec = (hdr.get('codec') or '').lower()
                if msg_type != 'audio.chunk':
                    continue
                if 'opus' in codec or 'webm' in codec:
                    await ensure_ffmpeg()
                    if ffmpeg and ffmpeg.stdin:
                        try:
                            ffmpeg.stdin.write(payload)
                            await ffmpeg.stdin.drain()
                        except Exception:
                            break
                elif 'pcm16' in codec:
                    # feed raw PCM16 directly
                    try:
                        await pcm_queue.put(payload)
                    except Exception:
                        break
                else:
                    # unsupported codec
                    try:
                        await websocket.send_json(
                            {"type": "system", "event": "error", "message": f"Unsupported codec: {codec}"})
                    except Exception:
                        pass
            elif 'text' in data and data['text']:
                try:
                    m = json.loads(data['text'])
                except Exception:
                    continue
                if m.get('type') == 'control':
                    action = m.get('action')
                    if action == 'start':
                        started = True
                        # Привязываем сессию (без дефолтов 'abc')
                        session_code = m.get('code') or m.get('meetingCode')
                        session_token = m.get('token')
                        if not session_code or not session_token:
                            try:
                                await websocket.send_json(
                                    {"type": "system", "event": "error", "message": "Missing meeting code or token"})
                            except Exception:
                                pass
                        else:
                            meeting = MEETINGS.get(session_code)
                            if not meeting or meeting.get("token") != session_token:
                                try:
                                    await websocket.send_json({"type": "system", "event": "error",
                                                               "message": "Invalid meeting code or token"})
                                except Exception:
                                    pass
                            else:
                                try:
                                    AI_AGENT.ensure_session(session_code, session_token,
                                                            candidate_name=meeting.get('candidateName'))
                                except Exception:
                                    logger.exception('Failed to ensure AI session on start')
                                # Поприветствовать кандидата
                                try:
                                    await _stream_agent_and_tts(None)
                                except Exception:
                                    logger.exception('Failed to stream greeting')
                    elif action == 'stop':
                        # graceful shutdown
                        break
                    elif action == 'ping':
                        try:
                            await websocket.send_json({"type": "system", "event": "ready"})
                        except Exception:
                            pass

    except WebSocketDisconnect:
        pass
    except Exception:
        logger.exception("Error in voice_gateway loop")
    finally:
        # Close ffmpeg and end queues
        try:
            await close_transcoder()
        except Exception:
            pass
        try:
            await pcm_queue.put(None)
        except Exception:
            pass
        if ffmpeg_reader_task:
            try:
                await ffmpeg_reader_task
            except Exception:
                pass
        if recognizer_task:
            try:
                await recognizer_task
            except Exception:
                pass
        try:
            await websocket.send_json({"type": "system", "event": "ended"})
        except Exception:
            pass
        try:
            await websocket.close()
        except Exception:
            pass


@app.get("/health")
async def health():
    # Проверяем доступность БД и S3
    db_ok = True
    s3_ok = True
    try:
        await check_db()
    except Exception:
        db_ok = False
    try:
        check_s3()
    except Exception:
        s3_ok = False

    ok = db_ok and s3_ok
    if not ok:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"db": db_ok, "s3": s3_ok},
        )
    return {"db": db_ok, "s3": s3_ok}


# --------- WS для видео-потока (как было) ----------
@app.websocket("/ws/stream/{session_id}")
async def websocket_video_endpoint(websocket: WebSocket, session_id: str):
    await websocket.accept()
    logger.info(f"Client connected to session: {session_id}")

    base_dir = Path(f"/tmp/interview_video/{session_id}")
    base_dir.mkdir(parents=True, exist_ok=True)
    filepath = base_dir / "recording.webm"
    if filepath.exists():
        filepath.unlink()

    try:
        chunk_count = 0
        while True:
            data = await websocket.receive()

            if data.get("bytes"):
                chunk = data["bytes"]
                chunk_count += 1

                loop = asyncio.get_event_loop()
                filepath = await loop.run_in_executor(
                    executor, save_video_chunk, chunk, session_id
                )

                logger.info(
                    f"Session {session_id}: Saved chunk {chunk_count} to {filepath}"
                )
                if websocket.application_state == "connected":
                    await websocket.send_text(
                        f"Received chunk {chunk_count}, saved to {filepath}"
                    )

            elif data.get("text"):
                try:
                    message = json.loads(data["text"])
                except json.JSONDecodeError:
                    continue

                if message.get("action") == "end_of_stream":
                    logger.info(f"Session {session_id}: Received end of stream signal")
                    break

    except WebSocketDisconnect:
        logger.warning(
            f"Session {session_id}: Client disconnected after {chunk_count} chunks"
        )
    except Exception:
        logger.exception(f"Session {session_id}: Error during streaming")
        if websocket.application_state == "connected":
            try:
                await websocket.close(code=1011)
            except Exception:
                pass

    # Финализация: видео + анализ + загрузка в S3 + обновление БД
    try:
        final_video_path = await finalize_video(session_id)
        logger.info(f"Session {session_id}: Final video ready at {final_video_path}")

        analysis_result = await process_video_balanced(final_video_path)
        logger.info(f"Session {session_id}: Analysis result: {analysis_result}")

        result_json_path = base_dir / f"{session_id}_analysis.json"
        with open(result_json_path, "w") as f:
            json.dump({"session_id": session_id, "analysis": analysis_result}, f)
        logger.info(f"Session {session_id}: Analysis JSON saved at {result_json_path}")

        # Загрузка видео и JSON в S3 параллельно
        video_task = asyncio.create_task(
            upload_file(final_video_path, f"{session_id}.webm")
        )
        json_task = asyncio.create_task(
            upload_file(result_json_path, f"{session_id}_analysis.json")
        )
        await asyncio.gather(video_task, json_task)

        # Сохраняем ссылки в БД
        await save_video_and_json_refs(session_id)

    except Exception:
        logger.exception(
            f"Session {session_id}: Failed to finalize/analyze/upload video"
        )
