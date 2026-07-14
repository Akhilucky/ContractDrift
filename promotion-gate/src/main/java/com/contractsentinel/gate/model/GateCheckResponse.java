package com.contractsentinel.gate.model;

import java.util.List;

public record GateCheckResponse(boolean allowed, String reason, List<String> violations, int driftScore, 
                               String recommendation, String policyApplied, String policyAction) {
}
