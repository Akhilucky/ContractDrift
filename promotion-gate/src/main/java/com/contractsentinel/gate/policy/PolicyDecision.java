package com.contractsentinel.gate.policy;

public record PolicyDecision(String action, String reason, String policyName) {
    public static PolicyDecision allow() {
        return new PolicyDecision("allow", "No blocking policies matched", null);
    }

    public static PolicyDecision deny(String reason, String policyName) {
        return new PolicyDecision("deny", reason, policyName);
    }

    public static PolicyDecision warn(String reason, String policyName) {
        return new PolicyDecision("warn", reason, policyName);
    }
}
