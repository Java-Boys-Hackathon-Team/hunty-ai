from contextlib import asynccontextmanager
import logging
from typing import List
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from starlette import status
from starlette.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from concurrent.futures import ThreadPoolExecutor
from app.video_processing.process import save_video_chunk

from app.db import check_db
from app.s3 import check_s3

# Global thread pool for blocking operations
executor = ThreadPoolExecutor(max_workers=4)

@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
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
    
    try:
        chunk_count = 0
        while True:
            # Receive video data from client
            data = await websocket.receive_bytes()
            chunk_count += 1
            
            # Save chunk to filesystem
            filepath = save_video_chunk(data, session_id)
            print(f"Session {session_id}: Saved chunk {chunk_count} to {filepath}")
            
            # Acknowledge receipt
            await websocket.send_text(f"Received chunk {chunk_count}, saved to {filepath}")
            
    except WebSocketDisconnect:
        print(f"Session {session_id}: Client disconnected after {chunk_count} chunks")
    except Exception as e:
        print(f"Session {session_id}: Error: {e}")
        await websocket.close(code=1011)
