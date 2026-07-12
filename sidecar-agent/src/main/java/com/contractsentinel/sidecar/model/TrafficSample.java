package com.contractsentinel.sidecar.model;

import java.time.Instant;
import java.util.Map;

public record TrafficSample(
        String serviceId,
        String endpoint,
        String method,
        Map<String, String> requestHeaders,
        String requestBody,
        Map<String, String> responseHeaders,
        String responseBody,
        int statusCode,
        Instant timestamp,
        Instant sampledAt) {

    public TrafficSample {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId must not be blank");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (sampledAt == null) {
            sampledAt = Instant.now();
        }
    }
}
