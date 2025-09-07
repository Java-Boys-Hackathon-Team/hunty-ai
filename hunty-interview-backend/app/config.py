import os
from dataclasses import dataclass, field
from typing import Optional


def _getenv(key: str, default: Optional[str] = None) -> Optional[str]:
    val = os.getenv(key)
    return val if val is not None and val != "" else default


def _getenv_int(key: str, default: int) -> int:
    try:
        return int(_getenv(key, str(default)))
    except Exception:
        return default


def _getenv_bool(key: str, default: bool) -> bool:
    val = _getenv(key)
    if val is None:
        return default
    return val.lower() in {"1", "true", "yes", "on"}


@dataclass
class DBSettings:
    host: str = _getenv("DB_HOST", "localhost")
    port: int = _getenv_int("DB_PORT", 25435)
    name: str = _getenv("DB_NAME", "huntyhr")
    user: str = _getenv("DB_USER", "pgadmin")
    password: str = _getenv("DB_PASSWORD", "pgadmin")
    echo: bool = _getenv_bool("DB_ECHO", False)

    @property
    def dsn_async(self) -> str:
        return f"postgresql+asyncpg://{self.user}:{self.password}@{self.host}:{self.port}/{self.name}"


@dataclass
class S3Settings:
    endpoint_url: Optional[str] = _getenv("S3_ENDPOINT_URL", "http://localhost:29002")
    region: Optional[str] = _getenv("S3_REGION", "ru-mos")
    access_key: Optional[str] = _getenv("S3_ACCESS_KEY_ID", "minioadmin")
    secret_key: Optional[str] = _getenv("S3_SECRET_ACCESS_KEY", "minioadmin")
    bucket: Optional[str] = _getenv("S3_BUCKET", "files")


@dataclass
class Settings:
    db: DBSettings = field(default_factory=DBSettings)
    s3: S3Settings = field(default_factory=S3Settings)


settings = Settings()
