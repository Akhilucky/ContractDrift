package com.contractsentinel.gate.model;

public record GateCheckRequest(String serviceId, String versionSha, String targetEnv) {
}
