package com.contractsentinel.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;

public class TracingUtil {

    private static final OpenTelemetry OPEN_TELEMETRY;
    private static final Tracer TRACER;

    static {
        String serviceName = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "sentinel");
        String endpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://jaeger:4317");

        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();

        Resource resource = Resource.getDefault().merge(
                Resource.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .build()
        );

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();

        OPEN_TELEMETRY = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();

        TRACER = OPEN_TELEMETRY.getTracer("contract-sentinel");
    }

    public static Span startSpan(String spanName, String... attributes) {
        Span span = TRACER.spanBuilder(spanName).startSpan();
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            span.setAttribute(attributes[i], attributes[i + 1]);
        }
        return span;
    }

    public static void endSpan(Span span) {
        if (span != null) {
            span.end();
        }
    }

    public static void addEvent(Span span, String name, String... attributes) {
        if (span != null) {
            io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
            for (int i = 0; i + 1 < attributes.length; i += 2) {
                builder.put(attributes[i], attributes[i + 1]);
            }
            span.addEvent(name, builder.build());
        }
    }

    public static Span startConsumerSpan(String topic, String messageKey) {
        Span span = TRACER.spanBuilder("kafka.consume")
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", topic)
                .setAttribute("messaging.message.id", messageKey)
                .startSpan();
        return span;
    }

    public static Span startProducerSpan(String topic, String messageKey) {
        Span span = TRACER.spanBuilder("kafka.produce")
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", topic)
                .setAttribute("messaging.message.id", messageKey)
                .startSpan();
        return span;
    }

    public static Span startHttpSpan(String method, String url, int statusCode) {
        Span span = TRACER.spanBuilder("http.request")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.method", method)
                .setAttribute("http.url", url)
                .setAttribute("http.status_code", (long) statusCode)
                .startSpan();
        if (statusCode >= 400) {
            span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
        }
        return span;
    }
}
