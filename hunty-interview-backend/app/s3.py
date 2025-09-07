from typing import Optional

import asyncio
import boto3
from botocore.config import Config

from .config import settings


def get_s3_client():
    # Keep short timeouts to fail fast on startup
    cfg = Config(connect_timeout=2, read_timeout=3, retries={"max_attempts": 1})
    return boto3.client(
        "s3",
        endpoint_url=settings.s3.endpoint_url,
        region_name=settings.s3.region,
        aws_access_key_id=settings.s3.access_key,
        aws_secret_access_key=settings.s3.secret_key,
        config=cfg,
    )


def check_s3() -> None:
    """Ensure S3 is reachable and the configured bucket is accessible."""
    client = get_s3_client()
    # Using head_bucket is cheaper than list_buckets
    client.head_bucket(Bucket=settings.s3.bucket)


async def upload_file(file_path: str, key: str):
    """
    Асинхронная обёртка для загрузки файла в S3.
    """
    loop = asyncio.get_event_loop()
    s3 = get_s3_client()
    await loop.run_in_executor(None, s3.upload_file, file_path, settings.s3.bucket, key)
    print(f"Uploaded {file_path} to s3://{settings.s3.bucket}/{key}")
