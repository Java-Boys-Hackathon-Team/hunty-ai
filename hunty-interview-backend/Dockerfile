FROM python:3.11.13-slim

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# system deps: ffmpeg для транскодинга (opus->pcm), либы для opencv-headless
RUN apt-get update && apt-get install -y --no-install-recommends \
      ffmpeg libglib2.0-0 libsm6 libxrender1 libxext6 curl \
    && rm -rf /var/lib/apt/lists/*

# non-root user
RUN useradd -u 10001 -m appuser
WORKDIR /app

# python deps
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# app
COPY main.py ./
COPY app ./app
RUN mkdir -p /app/static
COPY static ./static

# порт сервиса
EXPOSE 8000

# healthcheck
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD curl -fsS http://localhost:8000/health || exit 1

USER appuser

# запуск
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
