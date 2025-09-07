import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from pathlib import Path
from typing import List
import json

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
from starlette import status
from starlette.middleware.cors import CORSMiddleware

from app.db import check_db
from app.s3 import check_s3
from app.video_processing.process import save_video_chunk, process_video, load_models

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
        logging.info("Startup checks passed: DB and S3 are reachable.")
    except Exception:
        logging.exception("Startup checks failed: DB or S3 are not reachable.")
        # Re-raise to abort FastAPI startup
        raise
    yield


app = FastAPI(lifespan=lifespan)

# CORS: allow requests from any origin
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,  # must be False when using "*"
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
        # Return 503 if any dependency is down
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"db": db_ok, "s3": s3_ok},
        )
    return {"db": db_ok, "s3": s3_ok}


@app.websocket("/ws/stream/{session_id}")
async def websocket_video_endpoint(websocket: WebSocket, session_id: str):
    await websocket.accept()
    print(f"Client connected to session: {session_id}")

    # Создаем/очищаем файл при подключении
    base_dir = Path(f"/tmp/interview_video/{session_id}")
    base_dir.mkdir(parents=True, exist_ok=True)
    filepath = base_dir / "recording.webm"

    # Очищаем файл если он существует
    if filepath.exists():
        filepath.unlink()

    try:
        chunk_count = 0
        while True:
            # Receive data from client
            data = await websocket.receive()

            # Обрабатываем бинарные чанки
            if data.get("bytes"):
                chunk = data["bytes"]
                chunk_count += 1

                # Save chunk in thread pool (blocking)
                loop = asyncio.get_event_loop()
                filepath = await loop.run_in_executor(
                    executor, save_video_chunk, chunk, session_id
                )

                print(f"Session {session_id}: Saved chunk {chunk_count} to {filepath}")

                # Подтверждение фронтенду
                await websocket.send_text(
                    f"Received chunk {chunk_count}, saved to {filepath}"
                )

            # Обрабатываем специальные JSON-сообщения
            elif data.get("text"):
                try:
                    message = json.loads(data["text"])
                except json.JSONDecodeError:
                    continue

                if message.get("action") == "end_of_stream":
                    print(f"Session {session_id}: Received end of stream signal")
                    break

    except WebSocketDisconnect:
        print(f"Session {session_id}: Client disconnected after {chunk_count} chunks")
    except Exception as e:
        print(f"Session {session_id}: Error: {e}")
        await websocket.close(code=1011)

    # После отключения клиента запускаем анализ видео
    analysis_result = await process_video(session_id)
    print(f"Session {session_id}: Analysis result: {analysis_result}")
