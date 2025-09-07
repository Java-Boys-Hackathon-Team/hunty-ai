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
from app.s3 import check_s3, upload_file
from app.video_processing.process import save_video_chunk, process_video_balanced, load_models, finalize_video

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

                print(f"Session {session_id}: Saved chunk {chunk_count} to {filepath}")
                if websocket.application_state == websocket.STATE_CONNECTED:
                    await websocket.send_text(f"Received chunk {chunk_count}, saved to {filepath}")

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
        print(f"Session {session_id}: Error during streaming: {e}")
        try:
            if websocket.application_state == websocket.STATE_CONNECTED:
                await websocket.close(code=1011)
        except Exception:
            pass

    # Финализируем видео и анализируем
    try:
        final_video_path = await finalize_video(session_id)
        print(f"Session {session_id}: Final video ready at {final_video_path}")

        analysis_result = await process_video_balanced(final_video_path)
        print(f"Session {session_id}: Analysis result: {analysis_result}")

        # Сохраняем JSON с результатами
        result_json_path = base_dir / f"{session_id}_analysis.json"
        with open(result_json_path, "w") as f:
            json.dump({"session_id": session_id, "analysis": analysis_result}, f)
        print(f"Session {session_id}: Analysis JSON saved at {result_json_path}")

        # Параллельная загрузка видео и JSON в S3
        video_task = asyncio.create_task(upload_file(final_video_path, f"{session_id}.webm"))
        json_task = asyncio.create_task(upload_file(result_json_path, f"{session_id}_analysis.json"))
        await asyncio.gather(video_task, json_task)

        if websocket.application_state == websocket.STATE_CONNECTED:
            await websocket.send_text("Video and analysis uploaded to S3 successfully")

    except Exception as e:
        print(f"Session {session_id}: Failed to finalize/analyze/upload video: {e}")
        try:
            if websocket.application_state == websocket.STATE_CONNECTED:
                await websocket.send_text(f"Error processing video: {e}")
        except Exception:
            pass
