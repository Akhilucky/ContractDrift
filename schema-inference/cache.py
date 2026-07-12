import os
from typing import Optional

import redis


class RedisCache:
    def __init__(self) -> None:
        self.host = os.getenv("REDIS_HOST", "localhost")
        self.port = int(os.getenv("REDIS_PORT", "6379"))
        self.ttl = int(os.getenv("REDIS_TTL_SECONDS", "600"))
        self._client: Optional[redis.Redis] = None

    def _get_client(self) -> redis.Redis:
        if self._client is None:
            self._client = redis.Redis(
                host=self.host, port=self.port, decode_responses=True
            )
        return self._client

    def get(self, key: str) -> Optional[str]:
        try:
            client = self._get_client()
            value = client.get(key)
            return value
        except redis.RedisError:
            return None

    def set(self, key: str, value: str, ttl: Optional[int] = None) -> bool:
        try:
            client = self._get_client()
            expiry = ttl if ttl is not None else self.ttl
            return client.setex(key, expiry, value)
        except redis.RedisError:
            return False

    def health(self) -> bool:
        try:
            client = self._get_client()
            return client.ping()
        except redis.RedisError:
            return False
