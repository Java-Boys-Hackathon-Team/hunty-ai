#!/usr/bin/env bash

source .venv/bin/activate

export VOSK_MODEL_PATH=./app/models/vosk/ru

uvicorn main:app --reload --host 0.0.0.0 --port 8000