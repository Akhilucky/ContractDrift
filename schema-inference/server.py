import json
import logging
import os
import time
from concurrent import futures
from typing import Optional

import grpc
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource, SERVICE_NAME

from cache import RedisCache
from inference_engine import infer
import proto_parser

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("schema-inference")

resource = Resource.create({SERVICE_NAME: os.getenv("OTEL_SERVICE_NAME", "schema-inference")})
provider = TracerProvider(resource=resource)
otlp_endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://jaeger:4317")
exporter = OTLPSpanExporter(endpoint=otlp_endpoint, insecure=True)
provider.add_span_processor(BatchSpanProcessor(exporter))
trace.set_tracer_provider(provider)
tracer = trace.get_tracer("schema-inference")

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

        with tracer.start_as_current_span("InferSchema") as span:
            span.set_attribute("service.id", service_id)
            span.set_attribute("endpoint", endpoint)
            span.set_attribute("http.method", method)
            span.set_attribute("sample.count", len(samples))

            cache_key = f"inference:{service_id}:{endpoint}:{method}"
            cached_value: Optional[str] = self.cache.get(cache_key)
            was_cached = False

            if cached_value is not None:
                schema_json = cached_value
                was_cached = True
                span.set_attribute("cache.hit", True)
                logger.info("Cache hit for %s", cache_key)
            else:
                span.set_attribute("cache.hit", False)
                logger.info("Cache miss for %s - inferring schema from %d samples", cache_key, len(samples))
                schema = infer(samples)
                schema_json = json.dumps(schema)
                self.cache.set(cache_key, schema_json)
                logger.info("Schema inferred and cached for %s", cache_key)

            elapsed = time.monotonic() - start
            span.set_attribute("latency.seconds", elapsed)
            span.set_attribute("schema.cached", was_cached)
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

    def InferSchemaFromProto(
        self, request: pb2.InferProtoRequest, context: grpc.ServicerContext
    ) -> pb2.InferProtoResponse:
        start = time.monotonic()
        service_id = request.service_id
        endpoint = request.endpoint
        message_type = request.message_type
        package_name = request.package_name
        proto_content = request.proto_content

        with tracer.start_as_current_span("InferSchemaFromProto") as span:
            span.set_attribute("service.id", service_id)
            span.set_attribute("endpoint", endpoint)
            span.set_attribute("message.type", message_type)
            span.set_attribute("package.name", package_name)

            cache_key = f"proto:{service_id}:{endpoint}:{message_type}"
            cached_value: Optional[str] = self.cache.get(cache_key)
            was_cached = False

            if cached_value is not None:
                schema_json = cached_value
                was_cached = True
                span.set_attribute("cache.hit", True)
                logger.info("Proto cache hit for %s", cache_key)
            else:
                span.set_attribute("cache.hit", False)
                logger.info("Proto cache miss for %s - parsing proto content", cache_key)
                try:
                    openapi = proto_parser.parse_proto_to_openapi(proto_content, package_name)
                    schema = openapi.get("components", {}).get("schemas", {}).get(message_type, {})
                    schema_json = json.dumps(openapi)
                    self.cache.set(cache_key, schema_json)
                    logger.info("Proto schema parsed and cached for %s", cache_key)
                except Exception as e:
                    span.set_status(trace.StatusCode.ERROR, str(e))
                    logger.error("Proto parsing failed for %s: %s", cache_key, e)
                    context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                    context.set_details(f"Proto parsing failed: {e}")
                    return pb2.InferProtoResponse(
                        schema_json="",
                        service_id=service_id,
                        endpoint=endpoint,
                        message_type=message_type,
                        cached=False,
                    )

            elapsed = time.monotonic() - start
            span.set_attribute("latency.seconds", elapsed)
            span.set_attribute("schema.cached", was_cached)
            logger.info(
                "InferSchemaFromProto | service=%s endpoint=%s message_type=%s cached=%s latency=%.3fs",
                service_id, endpoint, message_type, was_cached, elapsed,
            )

        return pb2.InferProtoResponse(
            schema_json=schema_json,
            service_id=service_id,
            endpoint=endpoint,
            message_type=message_type,
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
