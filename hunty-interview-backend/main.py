import asyncio
import json
import logging
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from pathlib import Path
from typing import List

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
from starlette import status
from starlette.middleware.cors import CORSMiddleware

from app.db import check_db
from app.s3 import check_s3, upload_file
from app.video_processing.process import (
    finalize_video,
    load_models,
    process_video_balanced,
    save_video_chunk,
)


# ============================
# МОКИ: /meetings и /voice
# ============================
import math
from typing import Optional, Dict, Any
from datetime import datetime, timedelta
from fastapi import Body
from starlette.requests import Request

# Настройка логгера
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("app")

# Global thread pool for blocking operations
executor = ThreadPoolExecutor(max_workers=4)


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        # Загружаем модели для обработки видео
        load_models()

        # Fail fast if DB or S3 are unavailable
        await check_db()
        check_s3()
        logger.info("Startup checks passed: DB and S3 are reachable.")
    except Exception:
        logger.exception("Startup checks failed: DB or S3 are not reachable.")
        raise
    yield


app = FastAPI(lifespan=lifespan)

# CORS: allow requests from any origin
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files directory
app.mount("/static", StaticFiles(directory="static"), name="static")


# WebSocket manager class
class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        self.active_connections.remove(websocket)

    async def send_personal_message(self, message: str, websocket: WebSocket):
        await websocket.send_text(message)

    async def broadcast(self, message: str):
        for connection in self.active_connections:
            await connection.send_text(message)


manager = ConnectionManager()


# In-memory "БД" встреч
MEETINGS: Dict[str, Dict[str, Any]] = {}

def now_iso() -> str:
    return datetime.utcnow().isoformat(timespec="milliseconds") + "Z"

def add_minutes(iso: str, minutes: int) -> str:
    t = datetime.fromisoformat(iso.replace("Z", "+00:00"))
    return (t + timedelta(minutes=minutes)).isoformat(timespec="milliseconds").replace("+00:00", "Z")

# seed (как в mock-backend.js)
MEETINGS["abc"] = {
    "code": "abc",
    "candidateName": "Иван Петров",
    "greeting": "Добро пожаловать! Проверьте микрофон и камеру.\nКогда будете готовы - нажмите «Начать».",
    "status": "not_started",   # not_started | running | ended
    "interviewId": None,
    "token": None,
    "endAt": None,
}


# -------- REST: /meetings --------


@app.get("/meetings/{code}")
async def get_meeting(code: str):
    m = MEETINGS.get(code)
    if not m:
        raise HTTPException(status_code=404, detail={"error": "not_found", "message": "Meeting not found"})
    return m


@app.post("/meetings/{code}/start")
async def start_meeting(code: str, payload: Optional[Dict[str, Any]] = Body(default=None)):
    m = MEETINGS.get(code)
    if not m:
        raise HTTPException(status_code=404, detail={"error": "not_found", "message": "Meeting not found"})

    # если уже идёт - возвращаем текущие времена/идентификаторы
    if m.get("status") == "running":
        return {
            "startAt": m.get("startAt"),
            "endAt": m.get("endAt"),
            "interviewId": m.get("interviewId"),
            "token": m.get("token"),
        }

    # иначе запускаем
    duration = int(payload.get("durationMinutes", 30)) if payload else 30
    start_at = now_iso()
    end_at = add_minutes(start_at, duration)
    interview_id = f"iv_{hex(abs(hash(code + start_at)))[2:8]}"
    token = f"tok_{hex(abs(hash(interview_id + start_at)))[2:]}"

    m.update({
        "status": "running",
        "startAt": start_at,
        "endAt": end_at,
        "interviewId": interview_id,
        "token": token,
    })
    return {"startAt": start_at, "endAt": end_at, "interviewId": interview_id, "token": token}


@app.post("/meetings/{code}/end")
async def end_meeting(code: str):
    m = MEETINGS.get(code)
    if not m:
        raise HTTPException(status_code=404, detail={"error": "not_found", "message": "Meeting not found"})
    if m.get("status") != "running":
        raise HTTPException(status_code=409, detail={"error": "not_running", "message": "Meeting not running"})
    m.update({"status": "ended", "endAt": now_iso()})
    return {"ok": True}


# -------- WS: /voice (TTS/STT мок протокола) --------
async def _pack_envelope(header: Dict[str, Any], payload_bytes: bytes) -> bytes:
    header_json = json.dumps(header, ensure_ascii=False)
    header_bytes = header_json.encode("utf-8")
    padded = bytearray(256)
    padded[: min(len(header_bytes), 256)] = header_bytes[:256]
    return bytes(padded) + payload_bytes


def _silence_pcm16(ms: int, sample_rate: int = 24000, channels: int = 1) -> bytes:
    samples = int(ms * sample_rate / 1000) * channels
    return b"\x00\x00" * samples

def tone_pcm16_square(freq_hz: float, ms: int, sample_rate: int = 24000, channels: int = 1, volume: float = 0.3) -> bytes:
    frames = int(ms * sample_rate / 1000)
    pcm = bytearray(frames * channels * 2)
    amp = int(32767 * max(0.0, min(1.0, volume)))
    period = max(1, int(sample_rate / max(1.0, freq_hz)))
    for i in range(frames):
        s = amp if (i % period) < (period // 2) else -amp
        lo = s & 0xff
        hi = (s >> 8) & 0xff
        for ch in range(channels):
            off = (i * channels + ch) * 2
            pcm[off] = lo
            pcm[off+1] = hi
    return bytes(pcm)

def rms_pcm16_le(payload: bytes) -> float:
    import struct, math
    n = len(payload) // 2
    if n <= 0: return 0.0
    s = 0.0
    for (v,) in struct.iter_unpack('<h', payload):
        f = v / 32768.0
        s += f * f
    return (s / n) ** 0.5


async def _send_tts_phrase(websocket: WebSocket, text: str, *, ms_total: int = 1600) -> None:
    """
    Имитация «речевой» фразы: отправляем tts.chunk каждые ~240–260 мс,
    генерируя простую «речь» (смена частоты), плюс отправляем текстовые partial/final.
    """
    sample_rate = 24000
    channels = 1
    header = {"type": "tts.chunk", "codec": "pcm16", "sampleRate": sample_rate, "channels": channels}

    # Субтитры
    try:
        await websocket.send_text(json.dumps({"type": "stt.partial", "text": text, "fromMs": 0, "toMs": 500}, ensure_ascii=False))
    except Exception:
        return

    def _rms_pcm16(payload: bytes) -> float:
        # payload — little-endian int16
        import struct, math
        n = len(payload) // 2
        if n == 0: return 0.0
        it = struct.iter_unpack('<h', payload)
        s = 0.0
        c = 0
        for (v,) in it:
            f = v / 32768.0
            s += f * f
            c += 1
        return math.sqrt(s / max(1, c))

    elapsed = 0
    chunk_ms = 240
    step = 0
    try:
        while elapsed < ms_total:
            # простая «речевая» огибающая: меняем частоту 220↔440↔330 Гц
            freq = [220.0, 440.0, 330.0, 280.0][step % 4]
            payload = _tone_pcm16(freq, chunk_ms, sample_rate, channels, 0.30)
            rms = _rms_pcm16(payload)
            logger.info(f"TTS chunk: freq={freq} ms={chunk_ms} bytes={len(payload)} rms={rms:.4f}")
            frame = await _pack_envelope(header, payload)
            await websocket.send_bytes(frame)
            await asyncio.sleep(0.26)  # чуть больше длины чанка
            elapsed += chunk_ms
            step += 1
    finally:
        # Финальный субтитр
        try:
            await websocket.send_text(json.dumps({"type": "stt.final", "text": text, "fromMs": 0, "toMs": ms_total}, ensure_ascii=False))
        except Exception:
            pass


@app.websocket("/voice")
async def voice_gateway(websocket: WebSocket):
    await websocket.accept()
    try:
        qp = websocket.query_params
        interview_id = qp.get("interviewId")
        token = qp.get("token")

        if not interview_id:
            await websocket.send_text(json.dumps({"type": "system", "event": "error", "message": "Bad params"}))
            await websocket.close(code=1008)
            return

        await websocket.send_text(json.dumps({"type": "system", "event": "ready", "server":"mock-v3"}))

        # Сразу один «удар» 100мс, слышно всегда
        sr, ch = 24000, 1
        header = {"type":"tts.chunk","codec":"pcm16","sampleRate":sr,"channels":ch}
        bang = tone_pcm16_square(880.0, 120, sr, ch, 1.0)
        logger.info(f"TTS bang: bytes={len(bang)} rms={rms_pcm16_le(bang):.4f}")
        await websocket.send_bytes(await _pack_envelope(header, bang))

        # Состояние диалога
        started = False            # была ли команда start
        tts_busy = False           # сейчас проигрываем ответ
        current_tts: asyncio.Task | None = None

        async def start_tts(text: str, ms: int = 1600):
            nonlocal tts_busy, current_tts
            if tts_busy:
                return
            tts_busy = True
            async def run():
                try:
                    await _send_tts_phrase(websocket, text, ms_total=ms)
                finally:
                    # небольшая пауза после «речи», чтобы визуально было правдоподобно
                    await asyncio.sleep(0.05)
            current_tts = asyncio.create_task(run())
            def _done(_):
                nonlocal tts_busy
                tts_busy = False
            current_tts.add_done_callback(_done)

        # Главный цикл
        while True:
            try:
                message = await websocket.receive()
            except WebSocketDisconnect:
                break

            # Бинарные чанки микрофона — триггерим ответ, только если уже был start
            if message.get("bytes") is not None:
                if started and not tts_busy:
                    await start_tts("Спасибо, я вас услышал. Продолжайте, пожалуйста.", ms=1400)
                continue

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
                    # 1) Приветствие сразу после старта
                    await start_tts("Здравствуйте! Расскажите немного о себе.", ms=1500)
                elif action == "stop":
                    # прервать текущий ответ и завершить
                    if current_tts and not current_tts.done():
                        current_tts.cancel()
                        try:
                            await current_tts
                        except Exception:
                            pass
                    tts_busy = False
                    await websocket.send_text(json.dumps({"type": "system", "event": "ended", "message": "Interview ended (mock)."}))
                elif action == "ping":
                    await websocket.send_text(json.dumps({"type": "system", "event": "ready", "server":"mock-v3"}))

    except WebSocketDisconnect:
        logger.info("Voice WS: client disconnected")
    except Exception:
        logger.exception("Voice WS: error")
        try:
            await websocket.send_text(json.dumps({"type": "system", "event": "error", "message": "Internal error"}))
        except Exception:
            pass
        try:
            await websocket.close(code=1011)
        except Exception:
            pass


@app.get("/")
async def root():
    return {"message": "Hello World"}


@app.get("/hello/{name}")
async def say_hello(name: str):
    return {"message": f"Hello {name}"}


@app.get("/health")
async def health():
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

                logger.info(f"Session {session_id}: Saved chunk {chunk_count} to {filepath}")
                if websocket.application_state == "connected":
                    await websocket.send_text(f"Received chunk {chunk_count}, saved to {filepath}")

            elif data.get("text"):
                try:
                    message = json.loads(data["text"])
                except json.JSONDecodeError:
                    continue

                if message.get("action") == "end_of_stream":
                    logger.info(f"Session {session_id}: Received end of stream signal")
                    break

    except WebSocketDisconnect:
        logger.warning(f"Session {session_id}: Client disconnected after {chunk_count} chunks")
    except Exception:
        logger.exception(f"Session {session_id}: Error during streaming")
        if websocket.application_state == "connected":
            try:
                await websocket.close(code=1011)
            except Exception:
                pass

    # Финализируем видео и анализируем
    try:
        final_video_path = await finalize_video(session_id)
        logger.info(f"Session {session_id}: Final video ready at {final_video_path}")

        analysis_result = await process_video_balanced(final_video_path)
        logger.info(f"Session {session_id}: Analysis result: {analysis_result}")

        result_json_path = base_dir / f"{session_id}_analysis.json"
        with open(result_json_path, "w") as f:
            json.dump({"session_id": session_id, "analysis": analysis_result}, f)
        logger.info(f"Session {session_id}: Analysis JSON saved at {result_json_path}")

        # Параллельная загрузка видео и JSON в S3
        video_task = asyncio.create_task(upload_file(final_video_path, f"{session_id}.webm"))
        json_task = asyncio.create_task(upload_file(result_json_path, f"{session_id}_analysis.json"))
        await asyncio.gather(video_task, json_task)

        if websocket.application_state == "connected":
            await websocket.send_text("Video and analysis uploaded to S3 successfully")

    except Exception:
        logger.exception(f"Session {session_id}: Failed to finalize/analyze/upload video")
        if websocket.application_state == "connected":
            try:
                await websocket.send_text("Error processing video")
            except Exception:
                pass
