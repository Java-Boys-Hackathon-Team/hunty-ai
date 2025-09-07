from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, HTTPException
from starlette import status
from starlette.middleware.cors import CORSMiddleware

from app.db import check_db
from app.s3 import check_s3


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
