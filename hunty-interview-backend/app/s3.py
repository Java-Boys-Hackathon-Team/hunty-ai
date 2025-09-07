import asyncio
import logging

import boto3
from botocore.config import Config

from .config import settings

logger = logging.getLogger("s3")


def get_s3_client():
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
    client = get_s3_client()
    try:
        client.head_bucket(Bucket=settings.s3.bucket)
        logger.info(f"S3 bucket {settings.s3.bucket} is accessible")
    except Exception:
        logger.exception(f"S3 bucket {settings.s3.bucket} is not accessible")
        raise


async def upload_file(file_path: str, key: str):
    loop = asyncio.get_event_loop()
    s3 = get_s3_client()
    await loop.run_in_executor(None, s3.upload_file, file_path, settings.s3.bucket, key)
    logger.info(f"Uploaded {file_path} to s3://{settings.s3.bucket}/{key}")
