package com.contractsentinel.ingestion.model;

import java.time.Instant;

public record NormalizedSample(
        String serviceId,
        String endpoint,
        String method,
        String normalizedPayload,
        String contentHash,
        Instant ingestedAt) {
}
