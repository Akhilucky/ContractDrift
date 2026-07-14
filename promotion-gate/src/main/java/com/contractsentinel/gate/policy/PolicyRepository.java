package com.contractsentinel.gate.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class PolicyRepository {

    private static final Logger log = LoggerFactory.getLogger(PolicyRepository.class);

    @Value("${sentinel.contract-registry.url:http://localhost:8083}")
    private String contractRegistryUrl;

    private final RestTemplate restTemplate;

    public PolicyRepository() {
        this.restTemplate = new RestTemplate();
    }

    public List<ViolationSummary> getViolationSummaries(String serviceId, String environment) {
        try {
            String url = String.format("%s/api/v1/violations?provider=%s&limit=1000", contractRegistryUrl, serviceId);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> violations = restTemplate.getForObject(url, List.class);
            
            if (violations == null || violations.isEmpty()) {
                return List.of();
            }

            return aggregateViolations(violations);
        } catch (Exception e) {
            log.warn("Failed to fetch violations from contract-registry: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ViolationSummary> aggregateViolations(List<Map<String, Object>> violations) {
        Map<String, ViolationSummaryBuilder> builders = new java.util.HashMap<>();

        for (Map<String, Object> violation : violations) {
            String severity = (String) violation.getOrDefault("severity", "UNKNOWN");
            Instant detectedAt = parseInstant(violation.get("detected_at"));

            ViolationSummaryBuilder builder = builders.computeIfAbsent(severity, 
                    k -> new ViolationSummaryBuilder(severity));
            builder.addViolation(detectedAt);
        }

        return builders.values().stream()
                .map(ViolationSummaryBuilder::build)
                .toList();
    }

    private Instant parseInstant(Object value) {
        if (value instanceof String s) {
            return Instant.parse(s);
        }
        return Instant.now();
    }

    private static class ViolationSummaryBuilder {
        private final String severity;
        private int count = 0;
        private Instant earliest = Instant.MAX;
        private Instant latest = Instant.MIN;

        ViolationSummaryBuilder(String severity) {
            this.severity = severity;
        }

        void addViolation(Instant detectedAt) {
            count++;
            if (detectedAt != null) {
                if (detectedAt.isBefore(earliest)) {
                    earliest = detectedAt;
                }
                if (detectedAt.isAfter(latest)) {
                    latest = detectedAt;
                }
            }
        }

        ViolationSummary build() {
            return new ViolationSummary(severity, count, earliest == Instant.MAX ? null : earliest, 
                    latest == Instant.MIN ? null : latest);
        }
    }
}
