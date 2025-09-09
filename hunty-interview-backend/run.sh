#!/usr/bin/env bash

source .venv/bin/activate

export VOSK_MODEL_PATH=./app/models/vosk/ru
export PIPER_MODEL_PATH="$PWD/app/models/piper/voice-ru-irinia-medium/ru-irinia-medium.onnx"
export PIPER_CONFIG_PATH="$PWD/app/models/piper/voice-ru-irinia-medium/ru-irinia-medium.onnx.json"

uvicorn main:app --reload --host 0.0.0.0 --port 8000