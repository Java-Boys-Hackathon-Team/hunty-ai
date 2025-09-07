FROM python:3.11.13-slim AS builder

ENV POETRY_VERSION=2.1.4 \
    POETRY_HOME=/opt/poetry \
    PATH="/opt/poetry/bin:${PATH}" \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Install build tools needed for Poetry and possible wheels build
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       curl \
    && rm -rf /var/lib/apt/lists/*

# Install Poetry and the export plugin via pip (pipx not available by default on slim)
RUN pip install --no-cache-dir "poetry==${POETRY_VERSION}" poetry-plugin-export

WORKDIR /app

# Copy only dependency files first to leverage Docker layer caching
COPY pyproject.toml poetry.lock ./

# Export application runtime dependencies to a requirements file
RUN poetry export --without-hashes --only main -f requirements.txt -o requirements.txt

FROM python:3.11.13-slim AS runtime

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Install minimal runtime OS deps (curl for healthcheck)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -u 10001 -m appuser

WORKDIR /app

# Install Python deps
COPY --from=builder /app/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY main.py ./
COPY app ./app

# Expose service port
EXPOSE 8000

# Healthcheck hitting the FastAPI health endpoint
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD curl -fsS http://localhost:8000/health || exit 1

# Run as non-root
USER appuser

# Start the app with Uvicorn
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
