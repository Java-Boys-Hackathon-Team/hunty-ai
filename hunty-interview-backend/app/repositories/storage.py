import uuid
from datetime import datetime
from sqlalchemy import text
from app.db import async_session_maker

from ..config import settings
from ..db import engine


async def save_video_and_json_refs(session_id: str):
    """
    Сохраняет ссылки на видео и JSON-анализ в storage_object_entity
    и проставляет их в interview_session_entity.
    Если interview_session_entity не существует — создаёт её.
    """

    async with async_session_maker() as session:
        async with session.begin():
            # 1. Вставляем объект JSON (анализ)
            json_id = str(uuid.uuid4())
            res_json = await session.execute(
                text("""
                    INSERT INTO storage_object_entity (id, ref_, type_, created_at)
                    VALUES (:id, :ref_, :type_, :created_at)
                    RETURNING id
                """),
                {
                    "id": json_id,
                    "ref_": f"s3://files/{session_id}_analysis.json",
                    "type_": "application/json",
                    "created_at": datetime.utcnow(),
                },
            )
            json_obj_id = res_json.scalar_one()

            # 2. Вставляем объект VIDEO
            video_id = str(uuid.uuid4())
            res_video = await session.execute(
                text("""
                    INSERT INTO storage_object_entity (id, ref_, type_, created_at)
                    VALUES (:id, :ref_, :type_, :created_at)
                    RETURNING id
                """),
                {
                    "id": video_id,
                    "ref_": f"s3://files/{session_id}.webm",
                    "type_": "video/webm",
                    "created_at": datetime.utcnow(),
                },
            )
            video_obj_id = res_video.scalar_one()

            # 3. Убеждаемся, что session есть в interview_session_entity
            res_session = await session.execute(
                text("SELECT id FROM interview_session_entity WHERE id = :id"),
                {"id": session_id},
            )
            existing = res_session.scalar_one_or_none()

            if existing:
                # 4. Обновляем
                await session.execute(
                    text("""
                        UPDATE interview_session_entity
                        SET video_source_id = :video_id,
                            analytics_id = :json_id
                        WHERE id = :id
                    """),
                    {"video_id": video_obj_id, "json_id": json_obj_id, "id": session_id},
                )
            else:
                # 5. Создаём новую запись
                await session.execute(
                    text("""
                        INSERT INTO interview_session_entity (id, video_source_id, analytics_id, state, scheduled_start_at)
                        VALUES (:id, :video_id, :json_id, :state, :now)
                    """),
                    {
                        "id": session_id,
                        "video_id": video_obj_id,
                        "json_id": json_obj_id,
                        "state": "completed",
                        "now": datetime.utcnow(),
                    },
                )

            return {"session_id": session_id, "video_id": video_obj_id, "json_id": json_obj_id}
