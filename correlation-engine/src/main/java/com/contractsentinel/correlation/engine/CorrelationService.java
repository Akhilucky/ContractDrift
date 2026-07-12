package com.contractsentinel.correlation.engine;

import com.contractsentinel.correlation.model.CorrelatedViolation;
import com.contractsentinel.correlation.model.DeploymentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Map<String, List<DeploymentEvent>> recentDeployments = new ConcurrentHashMap<>();

    @Value("${kafka.topic.contract-violations:contract-violations}")
    private String contractViolationsTopic;

    @Value("${correlation.window.minutes:30}")
    private int correlationWindowMinutes;

    public CorrelationService(KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.kafkaTemplate = kafkaTemplate;
    }

    public void recordDeployment(DeploymentEvent event) {
        recentDeployments.computeIfAbsent(event.serviceId(), k -> new ArrayList<>()).add(event);
        log.info("Recorded deployment {} for service {}", event.deploymentId(), event.serviceId());
    }

    @KafkaListener(topics = "${kafka.topic.contract-violations:contract-violations}", groupId = "correlation-engine")
    public void consumeDriftEvent(String message) {
        try {
            CorrelatedViolation violation = objectMapper.readValue(message, CorrelatedViolation.class);
            log.info("Consumed drift event for service {}: violationId={}", violation.serviceId(), violation.violationId());

            List<DeploymentEvent> deployments = recentDeployments.get(violation.serviceId());
            if (deployments == null || deployments.isEmpty()) {
                log.info("No recent deployments found for service {}", violation.serviceId());
                return;
            }

            LocalDateTime windowStart = violation.detectedAt().minusMinutes(correlationWindowMinutes);
            for (DeploymentEvent dep : deployments) {
                if (dep.deployedAt().isAfter(windowStart) && dep.deployedAt().isBefore(violation.detectedAt())) {
                    CorrelatedViolation correlated = new CorrelatedViolation(
                            violation.violationId(),
                            violation.contractId(),
                            violation.detectedAt(),
                            dep.deploymentId(),
                            violation.serviceId(),
                            dep.versionSha(),
                            violation.provider(),
                            violation.consumer(),
                            violation.violationType()
                    );
                    String json = objectMapper.writeValueAsString(correlated);
                    kafkaTemplate.send("correlated-violations", correlated.violationId(), json);
                    log.info("Correlated violation {} with deployment {}", violation.violationId(), dep.deploymentId());
                    break;
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse drift event", e);
        }
    }
}
