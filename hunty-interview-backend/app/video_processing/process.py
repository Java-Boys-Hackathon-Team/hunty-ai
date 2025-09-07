# app/video_processing/process.py
import asyncio
from pathlib import Path


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


async def process_chunk_async(data: bytes, session_id: str, chunk_index: int):
    """
    Async function to process video chunk in memory.
    This is where OpenCV analysis can be added later.
    """
    # This will run in the background and won't block the WebSocket
    try:
        # Simulate some processing (replace with OpenCV later)
        print(
            f"Processing chunk {chunk_index} for session {session_id}, size: {len(data)} bytes"
        )

        # Here you can add OpenCV processing:
        # - Decode the video chunk
        # - Analyze frames
        # - Extract metrics

        # For now, just simulate some work
        await asyncio.sleep(0.1)

    except Exception as e:
        print(f"Error processing chunk {chunk_index}: {e}")
