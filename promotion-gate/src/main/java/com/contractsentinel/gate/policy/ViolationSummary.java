package com.contractsentinel.gate.policy;

import java.time.Instant;

public record ViolationSummary(String severity, int count, Instant earliest, Instant latest) {
}
