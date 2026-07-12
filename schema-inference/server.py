import json
import logging
import os
import time
from concurrent import futures
from typing import Optional

import grpc

from cache import RedisCache
from inference_engine import infer

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("schema-inference")

try:
    from protos import inference_pb2 as pb2
    from protos import inference_pb2_grpc as pb2_grpc
except ImportError:
    import inference_pb2 as pb2
    import inference_pb2_grpc as pb2_grpc


class SchemaInferenceServicer(pb2_grpc.SchemaInferenceServicer):
    def __init__(self) -> None:
        self.cache = RedisCache()

    def InferSchema(
        self, request: pb2.InferSchemaRequest, context: grpc.ServicerContext
    ) -> pb2.InferSchemaResponse:
        start = time.monotonic()
        service_id = request.service_id
        endpoint = request.endpoint
        method = request.method
        samples = list(request.samples)

        cache_key = f"inference:{service_id}:{endpoint}:{method}"
        cached_value: Optional[str] = self.cache.get(cache_key)
        was_cached = False

        if cached_value is not None:
            schema_json = cached_value
            was_cached = True
            logger.info("Cache hit for %s", cache_key)
        else:
            logger.info("Cache miss for %s - inferring schema from %d samples", cache_key, len(samples))
            schema = infer(samples)
            schema_json = json.dumps(schema)
            self.cache.set(cache_key, schema_json)
            logger.info("Schema inferred and cached for %s", cache_key)

        elapsed = time.monotonic() - start
        logger.info(
            "InferSchema | service=%s endpoint=%s method=%s samples=%d cached=%s latency=%.3fs",
            service_id, endpoint, method, len(samples), was_cached, elapsed,
        )

        return pb2.InferSchemaResponse(
            schema_json=schema_json,
            service_id=service_id,
            endpoint=endpoint,
            method=method,
            sample_count=len(samples),
            cached=was_cached,
        )


def serve() -> None:
    port = os.getenv("GRPC_PORT", "50051")
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb2_grpc.add_SchemaInferenceServicer_to_server(SchemaInferenceServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    logger.info("Schema Inference Server starting on port %s", port)
    server.start()
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
