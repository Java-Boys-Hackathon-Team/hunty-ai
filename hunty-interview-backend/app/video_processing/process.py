# app/video_processing/process.py
import asyncio
from pathlib import Path
import cv2
import os
import gc
import tempfile

# Глобальные переменные для кэширования моделей
face_cascade = None

def load_models():
    """Загружает модели один раз при старте приложения"""
    global face_cascade
    if face_cascade is None:
        face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        if face_cascade.empty():
            raise Exception("Не удалось загрузить модель для обнаружения лиц")

async def repair_webm_file(input_path: str, output_path: str) -> bool:
    """
    Восстанавливает WebM файл с помощью FFmpeg
    """
    try:
        cmd = [
            'ffmpeg', '-i', input_path, '-c', 'copy', '-y', output_path
        ]
        
        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        
        stdout, stderr = await process.communicate()
        
        if process.returncode == 0:
            return os.path.exists(output_path) and os.path.getsize(output_path) > 0
        else:
            print(f"Ошибка FFmpeg: {stderr.decode()}")
            return False
            
    except Exception as e:
        print(f"Ошибка при восстановлении файла: {e}")
        return False

async def finalize_video(session_id: str) -> str:
    """
    Перепаковывает raw видео в seekable WebM.
    Возвращает путь к финальному видео (тот же файл).
    """
    file_path = f"/tmp/interview_video/{session_id}/recording.webm"

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Video not found for session {session_id}")

    temp_path = f"/tmp/interview_video/{session_id}/recording_temp.webm"

    # FFmpeg перепаковывает в новый файл
    cmd = [
        "ffmpeg",
        "-i", file_path,
        "-c", "copy",
        "-y",
        temp_path
    ]

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE
    )
    stdout, stderr = await process.communicate()

    if process.returncode != 0:
        print(f"FFmpeg error: {stderr.decode()}")
        raise RuntimeError(f"Failed to finalize video for session {session_id}")

    # Заменяем исходный файл перепакованным
    os.replace(temp_path, file_path)

    return file_path


async def process_video(file_path: str):
    """
    Анализирует видео после завершения записи.
    Проверяет наличие одного человека в кадре.
    file_path: путь к видеофайлу
    """
    try:
        if not os.path.exists(file_path):
            print(f"Файл не найден: {file_path}")
            return False

        file_size = os.path.getsize(file_path)
        print(f"Размер файла: {file_size} bytes")
        if file_size < 1024:
            print(f"Файл слишком мал для анализа: {file_size} bytes")
            return False

        cap = cv2.VideoCapture(file_path)
        if not cap.isOpened():
            print(f"Не удалось открыть видео: {file_path}")
            return False

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total_frames <= 0:
            total_frames = 0
            while True:
                ret, _ = cap.read()
                if not ret:
                    break
                total_frames += 1
            cap.release()
            cap = cv2.VideoCapture(file_path)
            
        if total_frames <= 0:
            print(f"Видео не содержит кадров: {file_path}")
            return False

        frame_interval = max(1, total_frames // 5)
        analyzed_frames = 0
        valid_frames = 0
        
        for frame_idx in range(0, total_frames, frame_interval):
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
            ret, frame = cap.read()
            if not ret:
                continue
                
            analyzed_frames += 1
            small_frame = cv2.resize(frame, (320, 240))
            gray = cv2.cvtColor(small_frame, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
            if len(faces) == 1:
                valid_frames += 1
            del small_frame, gray, frame
            gc.collect()

        cap.release()

        if analyzed_frames > 0 and valid_frames / analyzed_frames >= 0.7:
            print(f"✓ Обнаружен один человек в {valid_frames}/{analyzed_frames} кадрах")
            return True
        else:
            print(f"✗ Проблема - лиц не обнаружено или обнаружено несколько. Valid: {valid_frames}/{analyzed_frames}")
            return False

    except Exception as e:
        print(f"Ошибка обработки видео: {e}")
        return False


def save_video_chunk(data: bytes, session_id: str) -> str:
    """
    Save video chunk to /tmp/interview_video/{session_id}/recording.webm
    Appends data to existing file if it exists
    Returns the path to the saved file.
    """
    base_dir = Path(f"/tmp/interview_video/{session_id}")
    base_dir.mkdir(parents=True, exist_ok=True)

    filename = "recording.webm"
    filepath = base_dir / filename

    with open(filepath, "ab") as f:
        f.write(data)

    return str(filepath)
