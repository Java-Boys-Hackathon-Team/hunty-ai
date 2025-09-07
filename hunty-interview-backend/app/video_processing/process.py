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
        # Используем FFmpeg для перекодирования файла
        cmd = [
            'ffmpeg', '-i', input_path, '-c', 'copy',  # Копируем кодеки без перекодирования
            '-y',  # Перезаписываем выходной файл
            output_path
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

async def process_video(session_id: str):
    """
    Анализирует полное видео после завершения записи
    Проверяет наличие одного человека в кадре
    """
    try:
        file_path = f"/tmp/interview_video/{session_id}/recording.webm"
        
        if not os.path.exists(file_path):
            print(f"Файл не найден для сессии {session_id}")
            return False
        
        # Проверяем размер файла
        file_size = os.path.getsize(file_path)
        print(f"Размер файла для сессии {session_id}: {file_size} bytes")
        
        if file_size < 1024:  # Меньше 1KB
            print(f"Файл слишком мал для анализа: {file_size} bytes")
            return False

        # Создаем временный файл для восстановленного видео
        with tempfile.NamedTemporaryFile(suffix='.webm', delete=False) as temp_file:
            repaired_path = temp_file.name

        # Пытаемся восстановить файл
        repaired = await repair_webm_file(file_path, repaired_path)
        
        if not repaired:
            print(f"Не удалось восстановить файл для сессии {session_id}")
            os.unlink(repaired_path)
            return False

        # Анализируем восстановленный файл
        cap = cv2.VideoCapture(repaired_path)
        if not cap.isOpened():
            print(f"Не удалось открыть восстановленное видео для сессии {session_id}")
            os.unlink(repaired_path)
            return False

        # Получаем общее количество кадров
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total_frames <= 0:
            # Если не удалось получить количество кадров, пробуем прочитать вручную
            total_frames = 0
            while True:
                ret, _ = cap.read()
                if not ret:
                    break
                total_frames += 1
            cap.release()
            cap = cv2.VideoCapture(repaired_path)
            
        if total_frames <= 0:
            print(f"Восстановленное видео не содержит кадров для сессии {session_id}")
            os.unlink(repaired_path)
            return False

        # Анализируем только каждый 5-й кадр для экономии ресурсов
        frame_interval = max(1, total_frames // 5)
        analyzed_frames = 0
        valid_frames = 0
        
        for frame_idx in range(0, total_frames, frame_interval):
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
            ret, frame = cap.read()
            
            if not ret:
                continue
                
            analyzed_frames += 1
            
            # Уменьшаем разрешение для ускорения обработки
            small_frame = cv2.resize(frame, (320, 240))
            
            # Конвертируем в grayscale
            gray = cv2.cvtColor(small_frame, cv2.COLOR_BGR2GRAY)
            
            # Обнаружение лиц
            faces = face_cascade.detectMultiScale(
                gray, 
                scaleFactor=1.1, 
                minNeighbors=5, 
                minSize=(30, 30)
            )
            
            # Проверяем количество лиц
            if len(faces) == 1:
                valid_frames += 1
            
            # Освобождаем память
            del small_frame, gray, frame
            gc.collect()

        cap.release()
        
        # Удаляем временный файл
        os.unlink(repaired_path)
        
        # Если в большинстве кадров обнаружено ровно одно лицо, считаем проверку пройденной
        if analyzed_frames > 0 and valid_frames / analyzed_frames >= 0.7:
            print(f"✓ Сессия {session_id}: Обнаружен один человек в {valid_frames}/{analyzed_frames} кадрах")
            return True
        else:
            print(f"✗ Сессия {session_id}: Проблема - лиц не обнаружено или обнаружено несколько. Valid: {valid_frames}/{analyzed_frames}")
            return False

    except Exception as e:
        print(f"Ошибка обработки видео для сессии {session_id}: {e}")
        # Убедимся, что временный файл удален в случае ошибки
        if 'repaired_path' in locals() and os.path.exists(repaired_path):
            os.unlink(repaired_path)
        return False

def save_video_chunk(data: bytes, session_id: str) -> str:
    """
    Save video chunk to /tmp/interview_video/{session_id}/recording.webm
    Appends data to existing file if it exists
    Returns the path to the saved file.
    """
    # Create directory if it doesn't exist
    base_dir = Path(f"/tmp/interview_video/{session_id}")
    base_dir.mkdir(parents=True, exist_ok=True)

    filename = "recording.webm"
    filepath = base_dir / filename

    # Append the chunk to the file
    with open(filepath, "ab") as f:
        f.write(data)

    return str(filepath)
