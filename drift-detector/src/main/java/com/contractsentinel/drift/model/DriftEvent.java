package com.contractsentinel.drift.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DriftEvent(
        UUID contractId,
        String providerId,
        String consumerId,
        String endpoint,
        String method,
        int driftScore,
        List<Violation> violations,
        Instant detectedAt
) {
}
