import asyncio
import gc
import logging
import os
from pathlib import Path

import cv2

logger = logging.getLogger("video_processing")

face_cascade = None


def load_models():
    global face_cascade
    if face_cascade is None:
        face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        )
        if face_cascade.empty():
            raise Exception("Не удалось загрузить модель для обнаружения лиц")
        logger.info("Face detection model loaded successfully")


async def repair_webm_file(input_path: str, output_path: str) -> bool:
    try:
        cmd = ["ffmpeg", "-i", input_path, "-c", "copy", "-y", output_path]
        process = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
        )
        stdout, stderr = await process.communicate()

        if process.returncode == 0:
            return os.path.exists(output_path) and os.path.getsize(output_path) > 0
        else:
            logger.error(f"FFmpeg error: {stderr.decode()}")
            return False
    except Exception as e:
        logger.exception(f"Ошибка при восстановлении файла {input_path}")
        return False


async def finalize_video(session_id: str) -> str:
    file_path = f"/tmp/interview_video/{session_id}/recording.webm"
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Video not found for session {session_id}")

    temp_path = f"/tmp/interview_video/{session_id}/recording_temp.webm"
    cmd = ["ffmpeg", "-i", file_path, "-c", "copy", "-y", temp_path]

    process = await asyncio.create_subprocess_exec(
        *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
    )
    stdout, stderr = await process.communicate()

    if process.returncode != 0:
        logger.error(f"FFmpeg error: {stderr.decode()}")
        raise RuntimeError(f"Failed to finalize video for session {session_id}")

    os.replace(temp_path, file_path)
    return file_path


async def process_video_balanced(file_path: str, fps_sample: int = 1) -> dict:
    try:
        if not os.path.exists(file_path):
            logger.warning(f"File not found: {file_path}")
            return {"error": "file_not_found"}

        cap = cv2.VideoCapture(file_path)
        if not cap.isOpened():
            logger.warning(f"Cannot open video: {file_path}")
            return {"error": "cannot_open_video"}

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        video_fps = cap.get(cv2.CAP_PROP_FPS) or 30
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
            logger.warning("No frames to analyze")
            return {"error": "no_frames"}

        valid_ratio = valid_frames / analyzed_frames
        multi_face_ratio = multi_face_frames / analyzed_frames
        is_valid = valid_ratio >= 0.7 and multi_face_ratio <= 0.05

        result = {
            "analyzed_frames": analyzed_frames,
            "valid_frames": valid_frames,
            "multi_face_frames": multi_face_frames,
            "valid_ratio": round(valid_ratio, 2),
            "multi_face_ratio": round(multi_face_ratio, 2),
            "is_valid": is_valid,
        }

        logger.info(f"Video analysis result: {result}")
        return result

    except Exception as e:
        logger.exception(f"Error processing video {file_path}")
        return {"error": str(e)}


def save_video_chunk(data: bytes, session_id: str) -> str:
    base_dir = Path(f"/tmp/interview_video/{session_id}")
    base_dir.mkdir(parents=True, exist_ok=True)

    filename = "recording.webm"
    filepath = base_dir / filename

    with open(filepath, "ab") as f:
        f.write(data)

    logger.debug(f"Saved video chunk to {filepath}")
    return str(filepath)
