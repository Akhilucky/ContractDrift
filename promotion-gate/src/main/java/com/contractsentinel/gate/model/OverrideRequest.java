package com.contractsentinel.gate.model;

public record OverrideRequest(String serviceId, String versionSha, String targetEnv, String reason, String overrideBy) {
}
