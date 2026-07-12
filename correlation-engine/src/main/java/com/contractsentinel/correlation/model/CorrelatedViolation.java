package com.contractsentinel.correlation.model;

import java.time.LocalDateTime;

public record CorrelatedViolation(String violationId, String contractId, LocalDateTime detectedAt,
                                  String deploymentId, String serviceId, String versionSha,
                                  String provider, String consumer, String violationType) {
}
