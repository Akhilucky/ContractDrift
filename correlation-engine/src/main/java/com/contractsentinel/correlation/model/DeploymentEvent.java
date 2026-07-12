package com.contractsentinel.correlation.model;

import java.time.LocalDateTime;

public record DeploymentEvent(String deploymentId, String serviceId, String versionSha, String environment,
                              LocalDateTime deployedAt, String source, String metadata) {
}
