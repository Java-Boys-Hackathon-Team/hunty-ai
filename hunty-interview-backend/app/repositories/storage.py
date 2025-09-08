import logging
from datetime import datetime

from sqlalchemy import text

from ..config import settings
from ..db import engine

logger = logging.getLogger("storage_repo")


def _make_s3_uri(key: str) -> str:
    return f"s3://{settings.s3.bucket}/{key}"


async def save_video_and_json_refs(session_id: str) -> None:
    """
    Создаёт записи в storage_object_entity для видео и JSON,
    обновляет или создаёт запись в interview_session_entity.
    """
    json_key = f"{session_id}_analysis.json"
    video_key = f"{session_id}.webm"

    async with engine.begin() as conn:
        # JSON объект
        res_json = await conn.execute(
            text(
                """
                INSERT INTO storage_object_entity (ref_, type_, created_at)
                VALUES (:ref, :type, :created_at)
                RETURNING id
            """
            ),
            {
                "ref": _make_s3_uri(json_key),
                "type": "application/json",
                "created_at": datetime.utcnow(),
            },
        )
        json_id = res_json.scalar_one()

        # Видео объект
        res_video = await conn.execute(
            text(
                """
                INSERT INTO storage_object_entity (ref_, type_, created_at)
                VALUES (:ref, :type, :created_at)
                RETURNING id
            """
            ),
            {
                "ref": _make_s3_uri(video_key),
                "type": "video/webm",
                "created_at": datetime.utcnow(),
            },
        )
        video_id = res_video.scalar_one()

        # Проверяем, есть ли запись для этого session_id
        res_check = await conn.execute(
            text("SELECT id FROM interview_session_entity WHERE id = :session_id"),
            {"session_id": session_id},
        )
        row = res_check.first()

        if row:
            # Обновляем
            await conn.execute(
                text(
                    """
                    UPDATE interview_session_entity
                    SET analytics_id = :analytics_id,
                        video_source_id = :video_source_id
                    WHERE id = :session_id
                """
                ),
                {
                    "analytics_id": json_id,
                    "video_source_id": video_id,
                    "session_id": session_id,
                },
            )
            logger.info(
                f"Session {session_id}: Updated interview_session_entity with analytics_id={json_id}, video_source_id={video_id}"
            )
        else:
            # Создаём новую
            await conn.execute(
                text(
                    """
                    INSERT INTO interview_session_entity (id, analytics_id, video_source_id, created_at)
                    VALUES (:id, :analytics_id, :video_source_id, :created_at)
                """
                ),
                {
                    "id": session_id,
                    "analytics_id": json_id,
                    "video_source_id": video_id,
                    "created_at": datetime.utcnow(),
                },
            )
            logger.info(
                f"Session {session_id}: Created new interview_session_entity with analytics_id={json_id}, video_source_id={video_id}"
            )
