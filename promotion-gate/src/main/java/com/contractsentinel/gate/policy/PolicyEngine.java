package com.contractsentinel.gate.policy;

import com.contractsentinel.gate.policy.model.Condition;
import com.contractsentinel.gate.policy.model.Policy;
import com.contractsentinel.gate.policy.model.PolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final PolicyConfig policyConfig;

    public PolicyEngine(PolicyConfig policyConfig) {
        this.policyConfig = policyConfig;
    }

    public PolicyDecision evaluate(List<ViolationSummary> violations, String serviceId, String environment) {
        if (policyConfig.getPolicies() == null || policyConfig.getPolicies().isEmpty()) {
            log.debug("No policies configured, allowing promotion");
            return PolicyDecision.allow();
        }

        for (Policy policy : policyConfig.getPolicies()) {
            if (evaluatePolicy(policy, violations, serviceId, environment)) {
                log.info("Policy '{}' matched for service={} env={}", policy.getName(), serviceId, environment);
                return PolicyDecision.deny(policy.getDescription(), policy.getName());
            }
        }

        log.debug("No policies matched for service={} env={}", serviceId, environment);
        return PolicyDecision.allow();
    }

    private boolean evaluatePolicy(Policy policy, List<ViolationSummary> violations,
                                   String serviceId, String environment) {
        if (policy.getConditions() == null || policy.getConditions().isEmpty()) {
            return false;
        }

        for (Condition condition : policy.getConditions()) {
            if (!evaluateCondition(condition, violations)) {
                return false;
            }
        }

        return true;
    }

    private boolean evaluateCondition(Condition condition, List<ViolationSummary> violations) {
        String severity = condition.getSeverity();
        int minCount = condition.getMinCount();
        String window = condition.getWindow();

        Instant windowStart = parseWindow(window);
        Instant now = Instant.now();

        long count = violations.stream()
                .filter(v -> severity.equals(v.severity()))
                .filter(v -> v.earliest() != null && v.earliest().isAfter(windowStart))
                .filter(v -> v.latest() != null && v.latest().isBefore(now))
                .mapToInt(ViolationSummary::count)
                .sum();

        return count >= minCount;
    }

    private Instant parseWindow(String window) {
        if (window == null || window.isEmpty()) {
            return Instant.EPOCH;
        }

        String trimmed = window.trim().toLowerCase();
        long amount;
        Duration duration;

        if (trimmed.endsWith("m")) {
            amount = Long.parseLong(trimmed.replace("m", "").trim());
            duration = Duration.ofMinutes(amount);
        } else if (trimmed.endsWith("h")) {
            amount = Long.parseLong(trimmed.replace("h", "").trim());
            duration = Duration.ofHours(amount);
        } else if (trimmed.endsWith("d")) {
            amount = Long.parseLong(trimmed.replace("d", "").trim());
            duration = Duration.ofDays(amount);
        } else {
            duration = Duration.ofHours(24);
        }

        return Instant.now().minus(duration);
    }
}
