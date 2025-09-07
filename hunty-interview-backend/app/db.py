from typing import Any, AsyncGenerator, Optional

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from .config import settings


class Base(DeclarativeBase):
    pass


engine = create_async_engine(
    settings.db.dsn_async,
    echo=settings.db.echo,
    pool_pre_ping=True,
)

async_session_maker = async_sessionmaker(
    engine, expire_on_commit=False, class_=AsyncSession
)


async def get_session() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_maker() as session:
        yield session


async def check_db() -> None:
    """Ensure DB is reachable by running a trivial query."""
    async with engine.connect() as conn:
        _ = await conn.scalar(text("SELECT 1"))


async def execute_raw(sql: str, params: Optional[dict[str, Any]] = None):
    """Execute raw SQL and return the Result object."""
    async with engine.begin() as conn:
        result = await conn.execute(text(sql), params or {})
        return result
