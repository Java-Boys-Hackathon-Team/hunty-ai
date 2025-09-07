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


async def process_video_balanced(file_path: str, fps_sample: int = 1):
    """
    Анализ видео с выборкой кадров для баланса скорости и точности.
    
    fps_sample: сколько кадров в секунду видео анализировать
    """
    try:
        if not os.path.exists(file_path):
            print(f"Файл не найден: {file_path}")
            return False

        cap = cv2.VideoCapture(file_path)
        if not cap.isOpened():
            print(f"Не удалось открыть видео: {file_path}")
            return False

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        video_fps = cap.get(cv2.CAP_PROP_FPS) or 30  # default 30 FPS если не определено
        frame_interval = max(1, int(video_fps / fps_sample))

        analyzed_frames = 0
        valid_frames = 0
        multi_face_frames = 0

        frame_idx = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            if frame_idx % frame_interval == 0:
                small_frame = cv2.resize(frame, (320, 240))
                gray = cv2.cvtColor(small_frame, cv2.COLOR_BGR2GRAY)
                faces = face_cascade.detectMultiScale(
                    gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30)
                )

                analyzed_frames += 1
                if len(faces) == 1:
                    valid_frames += 1
                elif len(faces) > 1:
                    multi_face_frames += 1

                del small_frame, gray
                gc.collect()

            frame_idx += 1

        cap.release()

        if analyzed_frames == 0:
            print("Нет кадров для анализа")
            return False

        valid_ratio = valid_frames / analyzed_frames
        multi_face_ratio = multi_face_frames / analyzed_frames

        if valid_ratio >= 0.7 and multi_face_ratio <= 0.05:
            print(f"✓ Видео валидно: {valid_frames}/{analyzed_frames} кадров с одним лицом, {multi_face_frames} кадров с >1 лицом")
            return True
        else:
            print(f"✗ Видео невалидно: {valid_frames}/{analyzed_frames} кадров с одним лицом, {multi_face_frames} кадров с >1 лицом")
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
