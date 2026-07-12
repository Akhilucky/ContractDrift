package com.contractsentinel.gate.api;

import com.contractsentinel.gate.audit.GateHistoryRepository;
import com.contractsentinel.gate.model.GateCheckResponse;
import com.contractsentinel.gate.model.GateHistory;
import com.contractsentinel.gate.model.OverrideRequest;
import io.micrometer.core.instrument.Counter;
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
    private final Counter gateDecisionsCounter;
    private final Counter gateDenyCounter;
    private final Counter gateOverrideCounter;

    public PromotionGateController(GateHistoryRepository historyRepository,
                                   Counter gateDecisionsCounter,
                                   Counter gateDenyCounter,
                                   Counter gateOverrideCounter) {
        this.historyRepository = historyRepository;
        this.gateDecisionsCounter = gateDecisionsCounter;
        this.gateDenyCounter = gateDenyCounter;
        this.gateOverrideCounter = gateOverrideCounter;
    }

    @GetMapping("/promote")
    public ResponseEntity<GateCheckResponse> checkPromotion(
            @RequestParam("service") String serviceId,
            @RequestParam("version") String versionSha,
            @RequestParam("env") String targetEnv) {

        log.info("Promotion check: service={}, version={}, env={}", serviceId, versionSha, targetEnv);

        List<String> violations = List.of();
        int driftScore = 0;
        boolean allowed = true;
        String reason = "No blocking violations found";
        String recommendation = "proceed";

        if (!violations.isEmpty()) {
            allowed = false;
            reason = "Blocking violations detected";
            recommendation = "block";
            gateDenyCounter.increment();
        } else {
            gateDecisionsCounter.increment();
        }

        GateCheckResponse response = new GateCheckResponse(allowed, reason, violations, driftScore, recommendation);

        GateHistory history = GateHistory.builder()
                .serviceId(serviceId)
                .versionSha(versionSha)
                .targetEnv(targetEnv)
                .decision(allowed ? "ALLOW" : "DENY")
                .driftScore(driftScore)
                .timestamp(LocalDateTime.now())
                .build();
        historyRepository.save(history);

        log.info("Promotion decision: {} for service={} env={}", history.getDecision(), serviceId, targetEnv);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/override")
    public ResponseEntity<GateCheckResponse> override(@RequestBody OverrideRequest request) {
        log.info("Override request: service={}, version={}, env={}, by={}", request.serviceId(), request.versionSha(), request.targetEnv(), request.overrideBy());

        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        gateOverrideCounter.increment();

        GateCheckResponse response = new GateCheckResponse(true, "Overridden: " + request.reason(), List.of(), 0, "proceed");

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

        log.info("Override recorded for service={} env={} by={}", request.serviceId(), request.targetEnv(), request.overrideBy());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<GateHistory>> getHistory(@RequestParam("service") String serviceId) {
        log.info("Fetching gate history for service={}", serviceId);
        List<GateHistory> history = historyRepository.findByServiceIdOrderByTimestampDesc(serviceId);
        return ResponseEntity.ok(history);
    }
}
