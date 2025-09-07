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
from fastapi.responses import Response
from fastapi.staticfiles import StaticFiles
from starlette import status
from starlette.middleware.cors import CORSMiddleware

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
VERSION_TAG = "voice-mock-v3-vad"

# Пул потоков для блокирующих операций
executor = ThreadPoolExecutor(max_workers=4)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Инициализация сервисов на старте приложения
    try:
        load_models()  # подгружаем модели для видео
        await check_db()  # проверяем БД
        check_s3()  # проверяем доступ к S3
        logger.info("Startup checks passed: DB and S3 are reachable.")
        logger.info("MAIN FILE: %s", os.path.abspath(__file__))
        logger.info("VERSION_TAG: %s", VERSION_TAG)
    except Exception:
        logger.exception("Startup checks failed: DB or S3 are not reachable.")
        raise
    yield


app = FastAPI(lifespan=lifespan)

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


# Начальные данные
MEETINGS["abc"] = {
    "code": "abc",
    "candidateName": "Иван Петров",
    "greeting": "Добро пожаловать! Проверьте микрофон и камеру.\nКогда будете готовы - нажмите «Начать».",
    "status": "not_started",  # not_started | running | ended
    "interviewId": None,
    "token": None,
    "endAt": None,
}


# --------- REST: получение и управление встречей ----------
@app.get("/meetings/{code}")
async def get_meeting(code: str):
    m = MEETINGS.get(code)
    if not m:
        raise HTTPException(
            status_code=404,
            detail={"error": "not_found", "message": "Meeting not found"},
        )
    return m


@app.post("/meetings/{code}/start")
async def start_meeting(
        code: str, payload: Optional[Dict[str, Any]] = Body(default=None)
):
    m = MEETINGS.get(code)
    if not m:
        raise HTTPException(
            status_code=404,
            detail={"error": "not_found", "message": "Meeting not found"},
        )

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
    return {"startAt": start_at, "endAt": end_at, "interviewId": interview_id, "token": token}


@app.post("/meetings/{code}/end")
async def end_meeting(code: str):
    m = MEETINGS.get(code)
    if not m:
        raise HTTPException(
            status_code=404,
            detail={"error": "not_found", "message": "Meeting not found"},
        )
    if m.get("status") != "running":
        raise HTTPException(
            status_code=409,
            detail={"error": "not_running", "message": "Meeting not running"},
        )
    m.update({"status": "ended", "endAt": now_iso()})
    return {"ok": True}


# --------- утилиты: упаковка и генерация тона ----------
async def _pack_envelope(header: Dict[str, Any], payload_bytes: bytes) -> bytes:
    # Упаковываем 256-байтный JSON заголовок + бинарный payload
    header_json = json.dumps(header, ensure_ascii=False)
    header_bytes = header_json.encode("utf-8")
    padded = bytearray(256)
    padded[: min(len(header_bytes), 256)] = header_bytes[:256]
    return bytes(padded) + payload_bytes


# короткая тишина PCM16
def _silence_pcm16(ms: int, sample_rate: int = 24000, channels: int = 1) -> bytes:
    samples = int(ms * sample_rate / 1000) * channels
    return b"\x00\x00" * samples


# генерируем весёлую «мелодию» из коротких бипов с паузами
def make_beep_melody(ms_total: int = 1000, sample_rate: int = 24000, channels: int = 1) -> list[tuple[bytes, int]]:
    """
    Возвращает список пар (payload_bytes, duration_ms).
    Внутри немного случайности по частоте, длительности и паузам.
    """
    import random

    # набор приятных частот в районе приветственного бипа
    base_notes = [660, 704, 784, 880, 988]
    pieces: list[tuple[bytes, int]] = []
    elapsed = 0

    while elapsed < ms_total:
        dur = random.choice([90, 110, 130, 150, 170])  # длительность бипа
        freq = random.choice(base_notes)  # частота
        vol = random.uniform(0.35, 0.6)  # громкость
        payload = tone_pcm16_square(freq, dur, sample_rate, channels, vol)
        pieces.append((payload, dur))
        elapsed += dur

        if elapsed >= ms_total:
            break

        gap = random.choice([40, 60, 80])  # пауза между бипами
        pieces.append((_silence_pcm16(gap, sample_rate, channels), gap))
        elapsed += gap

    return pieces


def tone_pcm16_square(
        freq_hz: float,
        ms: int,
        sample_rate: int = 24000,
        channels: int = 1,
        volume: float = 0.3,
) -> bytes:
    # Простой квадратный тон PCM16 little-endian
    frames = int(ms * sample_rate / 1000)
    pcm = bytearray(frames * channels * 2)
    amp = int(32767 * max(0.0, min(1.0, volume)))
    period = max(1, int(sample_rate / max(1.0, freq_hz)))
    for i in range(frames):
        s = amp if (i % period) < (period // 2) else -amp
        lo = s & 0xFF
        hi = (s >> 8) & 0xFF
        for ch in range(channels):
            off = (i * channels + ch) * 2
            pcm[off] = lo
            pcm[off + 1] = hi
    return bytes(pcm)


async def _send_beeps(websocket: WebSocket, count: int, *, freq_hz: float = 880.0, ms_each: int = 120,
                      gap_ms: int = 120):
    # Отправляем count коротких бипов подряд
    sr, ch = 24000, 1
    header = {"type": "tts.chunk", "codec": "pcm16", "sampleRate": sr, "channels": ch}
    for i in range(max(1, count)):
        payload = tone_pcm16_square(freq_hz, ms_each, sr, ch, 1.0)
        frame = await _pack_envelope(header, payload)
        await websocket.send_bytes(frame)
        # Небольшая пауза между бипами
        await asyncio.sleep(gap_ms / 1000)


# --------- состояние VAD на подключение ----------
class VadState:
    def __init__(self, interview_id: str):
        self.interview_id = interview_id
        self.in_voice = False
        self.silence_ms = 0
        self.buffer = bytearray()
        self.total_ms = 0
        self.segment_counter = 0
        # Каталог для сохранения сегментов речи
        self.base_dir = Path(f"/tmp/voice_segments/{interview_id}")
        self.base_dir.mkdir(parents=True, exist_ok=True)

    def start_segment(self):
        # Начало нового речевого сегмента
        self.in_voice = True
        self.silence_ms = 0
        self.buffer.clear()
        self.total_ms = 0
        logger.info("[VAD] start segment for %s", self.interview_id)

    def push_chunk(self, payload: bytes, chunk_ms: int):
        # Добавляем очередной чанк в буфер
        self.buffer.extend(payload)
        self.total_ms += max(0, chunk_ms)

    def inc_silence(self, chunk_ms: int):
        # Увеличиваем счётчик тишины
        self.silence_ms += max(0, chunk_ms)

    def reset_after_finalize(self):
        # Сбрасываем состояние после финализации сегмента
        self.in_voice = False
        self.silence_ms = 0
        self.buffer.clear()
        self.total_ms = 0

    def save_segment(self, codec: str) -> Path:
        # Сохраняем накопленный сегмент на диск
        self.segment_counter += 1
        ts = datetime.utcnow().strftime("%Y%m%dT%H%M%S_%f")[:-3]
        ext = "webm" if codec == "opus/webm" else "pcm16"
        path = self.base_dir / f"seg_{ts}_{self.segment_counter:04d}.{ext}"
        with open(path, "wb") as f:
            f.write(self.buffer)
        return path


# --------- простая имитация TTS-фразы (для приветствия) ----------
async def _send_tts_phrase(websocket: WebSocket, text: str, *, ms_total: int = 1200) -> None:
    # Отправляем несколько чанков PCM16 с разной частотой - псевдо-речь
    sample_rate = 24000
    channels = 1
    header = {"type": "tts.chunk", "codec": "pcm16", "sampleRate": sample_rate, "channels": channels}
    try:
        await websocket.send_text(
            json.dumps({"type": "stt.partial", "text": text, "fromMs": 0, "toMs": 500}, ensure_ascii=False))
    except Exception:
        return

    elapsed = 0
    chunk_ms = 240
    step = 0
    try:
        while elapsed < ms_total:
            freq = [220.0, 440.0, 330.0, 280.0][step % 4]
            payload = tone_pcm16_square(freq, chunk_ms, sample_rate, channels, 0.30)
            frame = await _pack_envelope(header, payload)
            await websocket.send_bytes(frame)
            await asyncio.sleep(0.26)
            elapsed += chunk_ms
            step += 1
    finally:
        try:
            await websocket.send_text(
                json.dumps({"type": "stt.final", "text": text, "fromMs": 0, "toMs": ms_total}, ensure_ascii=False))
        except Exception:
            pass


# отправка «весёлых бипов» как TTS-ответа
async def _send_fun_beeps(websocket: WebSocket, caption: str, *, ms_total: int = 1000) -> None:
    """
    Мокаем аудио-ответ набором коротких бипов с паузами.
    Также шлём partial/final для субтитров, чтобы фронт рисовал текст.
    """
    sample_rate = 24000
    channels = 1
    header = {"type": "tts.chunk", "codec": "pcm16", "sampleRate": sample_rate, "channels": channels}

    # субтитры
    try:
        await websocket.send_text(
            json.dumps({"type": "stt.partial", "text": caption, "fromMs": 0, "toMs": 500}, ensure_ascii=False))
    except Exception:
        return

    # шлём пачку бипов
    melody = make_beep_melody(ms_total, sample_rate, channels)
    for payload, dur in melody:
        frame = await _pack_envelope(header, payload)
        await websocket.send_bytes(frame)
        # небольшая задержка с запасом, чтобы не «зализывать» границы
        await asyncio.sleep((dur + 20) / 1000)

    # финальный субтитр
    try:
        await websocket.send_text(
            json.dumps({"type": "stt.final", "text": caption, "fromMs": 0, "toMs": ms_total}, ensure_ascii=False))
    except Exception:
        pass


# --------- WebSocket: голосовой шлюз ----------
@app.websocket("/voice")
async def voice_gateway(websocket: WebSocket):
    await websocket.accept()
    try:
        qp = websocket.query_params
        interview_id = qp.get("interviewId") or "unknown"
        token = qp.get("token")

        # Диагностика для фронта
        await websocket.send_text(
            json.dumps({"type": "diag", "file": os.path.abspath(__file__), "version": VERSION_TAG}, ensure_ascii=False))

        if not interview_id or interview_id == "unknown":
            await websocket.send_text(json.dumps({"type": "system", "event": "error", "message": "Bad params"}))
            await websocket.close(code=1008)
            return

        # Сигнал готовности
        await websocket.send_text(json.dumps({"type": "system", "event": "ready", "server": "mock-v3"}))

        # Приветственный бип сразу после коннекта
        await _send_beeps(websocket, 1)

        # VAD-параметры мока
        vad = VadState(interview_id)
        VAD_THRESH = 0.02  # порог для rmsHint с фронта
        VAD_TAIL_MS = 2000  # завершаем сегмент после 2 секунд тишины

        # Внутреннее состояние беседы
        started = False
        tts_busy = False
        current_tts: asyncio.Task | None = None

        async def start_tts(text: str, ms: int = 1200, fun: bool = True):
            """
            Обёртка над отправкой TTS. Если fun=True - шлём «весёлые бипы»,
            иначе - стандартную псевдо-речь.
            """
            nonlocal tts_busy, current_tts
            if tts_busy:
                return
            tts_busy = True

            async def run():
                try:
                    if fun:
                        await _send_fun_beeps(websocket, caption=text, ms_total=ms)
                    else:
                        await _send_tts_phrase(websocket, text, ms_total=ms)
                finally:
                    # короткий пост-пауза, чтобы фронт успевал погасить анимацию
                    await asyncio.sleep(0.05)

            current_tts = asyncio.create_task(run())

            def _done(_):  # noqa
                nonlocal tts_busy
                tts_busy = False

            current_tts.add_done_callback(_done)

        # Главный цикл обработки входящих WS-сообщений
        while True:
            try:
                message = await websocket.receive()
            except WebSocketDisconnect:
                break

            # Бинарные сообщения - это аудио чанки от клиента
            if message.get("bytes") is not None:
                raw: bytes = message["bytes"]  # 256 байт заголовок + полезная нагрузка
                if len(raw) < 256:
                    continue
                head = raw[:256].rstrip(b"\x00")
                payload = raw[256:]
                try:
                    h = json.loads(head.decode("utf-8") or "{}")
                except Exception:
                    h = {}

                codec = h.get("codec") or "unknown"
                chunk_ms = int(h.get("durationMs") or 250)
                rms_hint = float(h.get("rmsHint") or 0.0)

                # Простейшая логика VAD
                if rms_hint > VAD_THRESH:
                    # Речь - добавляем в буфер
                    if not vad.in_voice:
                        vad.start_segment()
                    vad.push_chunk(payload, chunk_ms)
                    vad.silence_ms = 0
                else:
                    # Тишина - если были в речи, считаем хвост
                    if vad.in_voice:
                        vad.inc_silence(chunk_ms)
                        if vad.silence_ms >= VAD_TAIL_MS:
                            # Финализация сегмента
                            dur_ms = vad.total_ms
                            # Пока уберу сохранение сегмента
                            # path = vad.save_segment(codec)
                            # logger.info("[VAD] finalize: saved=%s dur_ms=%d bytes=%d codec=%s",
                            #             path, dur_ms, len(vad.buffer), codec)
                            await start_tts("Сегмент речи получен.", ms=900, fun=True)

                            vad.reset_after_finalize()
                    # Если не в речи - ничего не делаем

                continue

            # Текстовые сообщения - служебные команды
            text = message.get("text")
            if not text:
                continue

            try:
                data = json.loads(text)
            except json.JSONDecodeError:
                continue

            if data.get("type") == "control":
                action = data.get("action")
                if action == "start":
                    started = True
                    # Приветственная псевдо-фраза
                    await start_tts("Здравствуйте! Расскажите немного о себе.", ms=1200, fun=True)
                elif action == "stop":
                    # Принудительное завершение
                    if current_tts and not current_tts.done():
                        current_tts.cancel()
                        try:
                            await current_tts
                        except Exception:
                            pass
                    tts_busy = False
                    await websocket.send_text(
                        json.dumps({"type": "system", "event": "ended", "message": "Interview ended (mock)."}))
                elif action == "ping":
                    await websocket.send_text(json.dumps({"type": "system", "event": "ready", "server": "mock-v3"}))

    except WebSocketDisconnect:
        logger.info("Voice WS: client disconnected")
    except Exception:
        logger.exception("Voice WS: error")
        try:
            await websocket.send_text(
                json.dumps(
                    {"type": "system", "event": "error", "message": "Internal error"}
                )
            )
        except Exception:
            pass
        try:
            await websocket.close(code=1011)
        except Exception:
            pass


# --------- простая отладочная ручка: выдаём 250 мс PCM16 ----------
@app.get("/debug/pcm16")
async def debug_pcm16():
    data = tone_pcm16_square(440.0, 250, 24000, 1, 1.0)
    return Response(content=data, media_type="application/octet-stream")


# --------- прочие REST ручки ----------
@app.get("/")
async def root():
    return {"message": "Hello World", "version": VERSION_TAG}


@app.get("/hello/{name}")
async def say_hello(name: str):
    return {"message": f"Hello {name}"}


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
