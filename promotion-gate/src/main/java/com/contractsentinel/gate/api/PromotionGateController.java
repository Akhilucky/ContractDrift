package com.contractsentinel.gate.api;

import com.contractsentinel.gate.audit.GateHistoryRepository;
import com.contractsentinel.gate.model.GateCheckResponse;
import com.contractsentinel.gate.model.GateHistory;
import com.contractsentinel.gate.model.OverrideRequest;
import com.contractsentinel.gate.policy.PolicyDecision;
import com.contractsentinel.gate.policy.PolicyEngine;
import com.contractsentinel.gate.policy.PolicyRepository;
import com.contractsentinel.gate.policy.ViolationSummary;
import io.micrometer.core.instrument.Counter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gate")
public class PromotionGateController {

    private static final Logger log = LoggerFactory.getLogger(PromotionGateController.class);

    private final GateHistoryRepository historyRepository;
    private final PolicyEngine policyEngine;
    private final PolicyRepository policyRepository;
    private final Counter gateDecisionsCounter;
    private final Counter gateDenyCounter;
    private final Counter gateOverrideCounter;

    public PromotionGateController(GateHistoryRepository historyRepository,
                                   PolicyEngine policyEngine,
                                   PolicyRepository policyRepository,
                                   Counter gateDecisionsCounter,
                                   Counter gateDenyCounter,
                                   Counter gateOverrideCounter) {
        this.historyRepository = historyRepository;
        this.policyEngine = policyEngine;
        this.policyRepository = policyRepository;
        this.gateDecisionsCounter = gateDecisionsCounter;
        this.gateDenyCounter = gateDenyCounter;
        this.gateOverrideCounter = gateOverrideCounter;
    }

    @GetMapping("/promote")
    public ResponseEntity<GateCheckResponse> checkPromotion(
            @RequestParam("service") String serviceId,
            @RequestParam("version") String versionSha,
            @RequestParam("env") String targetEnv) {

        Span span = GlobalOpenTelemetry.getTracer("contract-sentinel")
                .spanBuilder("gate.promote")
                .setAttribute("service.id", serviceId)
                .setAttribute("deployment.version", versionSha)
                .setAttribute("deployment.environment", targetEnv)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            log.info("Promotion check: service={}, version={}, env={}", serviceId, versionSha, targetEnv);

            List<ViolationSummary> violations = policyRepository.getViolationSummaries(serviceId, targetEnv);
            PolicyDecision decision = policyEngine.evaluate(violations, serviceId, targetEnv);

            List<String> violationDetails = violations.stream()
                    .map(v -> String.format("%s: %d violations", v.severity(), v.count()))
                    .toList();

            boolean allowed = "allow".equals(decision.action());
            String reason = decision.reason();
            String recommendation = allowed ? "proceed" : decision.action();

            if (!allowed) {
                gateDenyCounter.increment();
            } else {
                gateDecisionsCounter.increment();
            }

            span.setAttribute("decision", allowed ? "allow" : "deny");
            span.setAttribute("policy.applied", decision.policyName() != null ? decision.policyName() : "none");
            span.setAttribute("violation.count", (long) violations.size());

            if (!allowed) {
                span.setStatus(StatusCode.ERROR, reason);
            }

            GateCheckResponse response = new GateCheckResponse(allowed, reason, violationDetails, 0, 
                    recommendation, decision.policyName(), decision.action());

            GateHistory history = GateHistory.builder()
                    .serviceId(serviceId)
                    .versionSha(versionSha)
                    .targetEnv(targetEnv)
                    .decision(allowed ? "ALLOW" : "DENY")
                    .driftScore(0)
                    .timestamp(LocalDateTime.now())
                    .build();
            historyRepository.save(history);

            log.info("Promotion decision: {} for service={} env={} (policy: {})", 
                    history.getDecision(), serviceId, targetEnv, decision.policyName());
            return ResponseEntity.ok(response);
        } finally {
            span.end();
        }
    }

    @PostMapping("/override")
    public ResponseEntity<GateCheckResponse> override(@RequestBody OverrideRequest request) {
        log.info("Override request: service={}, version={}, env={}, by={}", 
                request.serviceId(), request.versionSha(), request.targetEnv(), request.overrideBy());

        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        gateOverrideCounter.increment();

        GateCheckResponse response = new GateCheckResponse(true, "Overridden: " + request.reason(), 
                List.of(), 0, "proceed", null, null);

        GateHistory history = GateHistory.builder()
                .serviceId(request.serviceId())
                .versionSha(request.versionSha())
                .targetEnv(request.targetEnv())
                .decision("OVERRIDE")
                .overrideReason(request.reason())
                .overrideBy(request.overrideBy())
                .timestamp(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        log.info("Override recorded for service={} env={} by={}", 
                request.serviceId(), request.targetEnv(), request.overrideBy());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<GateHistory>> getHistory(@RequestParam("service") String serviceId) {
        log.info("Fetching gate history for service={}", serviceId);
        List<GateHistory> history = historyRepository.findByServiceIdOrderByTimestampDesc(serviceId);
        return ResponseEntity.ok(history);
    }
}
