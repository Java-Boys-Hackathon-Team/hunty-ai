# app/video_processing/process.py
import uuid
from datetime import datetime
from pathlib import Path

def save_video_chunk(data: bytes, session_id: str) -> str:
    """
    Save video chunk to /tmp/interview_video/{session_id}/{chunk_id}.webm
    Returns the path to the saved file.
    """
    # Create directory if it doesn't exist
    base_dir = Path(f"/tmp/interview_video/{session_id}")
    base_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate unique chunk ID
    chunk_id = f"{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
    filename = f"{chunk_id}.webm"
    filepath = base_dir / filename
    
    # Save the chunk
    with open(filepath, 'wb') as f:
        f.write(data)
    
    return str(filepath)
