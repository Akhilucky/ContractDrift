package com.contractsentinel.drift.scheduler;

import com.contractsentinel.drift.diff.DriftResult;
import com.contractsentinel.drift.diff.SemanticDiffEngine;
import com.contractsentinel.drift.kafka.DriftEventPublisher;
import com.contractsentinel.drift.model.DriftEvent;
import com.contractsentinel.drift.model.Violation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

@Component
public class DriftDetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DriftDetectionScheduler.class);

    private final SemanticDiffEngine diffEngine;
    private final DriftEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String registryBaseUrl;
    private final List<ContractPair> contractPairs;

    public DriftDetectionScheduler(SemanticDiffEngine diffEngine,
                                   DriftEventPublisher eventPublisher,
                                   @Value("${drift.registry.base-url}") String registryBaseUrl,
                                   @Value("#{${drift.contract-pairs:null}}") List<ContractPair> contractPairs) {
        this.diffEngine = diffEngine;
        this.eventPublisher = eventPublisher;
        this.registryBaseUrl = registryBaseUrl;
        this.contractPairs = contractPairs;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Scheduled(fixedDelayString = "${drift.detection.interval-ms:60000}")
    public void detectDrift() {
        if (contractPairs == null || contractPairs.isEmpty()) {
            log.debug("No contract pairs configured for drift detection");
            return;
        }

        log.info("Starting drift detection cycle for {} pairs", contractPairs.size());
        var results = new ConcurrentHashMap<ContractPair, DriftResult>();

        try (var scope = new StructuredTaskScope<ContractPair>() {
            @Override
            protected void handleComplete(Subtask<? extends ContractPair> subtask) {
                // handled via join
            }
        }) {
            for (ContractPair pair : contractPairs) {
                scope.fork(() -> {
                    try {
                        DriftResult result = processPair(pair);
                        if (result != null) {
                            results.put(pair, result);
                        }
                    } catch (Exception e) {
                        log.error("Error processing pair {}->{}: {}", pair.provider(), pair.consumer(), e.getMessage());
                    }
                    return pair;
                });
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Drift detection cycle interrupted");
        }

        log.info("Drift detection cycle complete. {} pairs had drift.", results.size());
    }

    private DriftResult processPair(ContractPair pair) {
        try {
            String baselineJson = fetchBaselineContract(pair);
            String inferredJson = fetchInferredSchema(pair);

            if (baselineJson == null || inferredJson == null) {
                log.warn("Missing baseline or inferred schema for {}->{}", pair.provider(), pair.consumer());
                return null;
            }

            JsonNode baselineSchema = objectMapper.readTree(baselineJson);
            JsonNode inferredSchema = objectMapper.readTree(inferredJson);

            DriftResult result = diffEngine.computeDrift(baselineSchema, inferredSchema);

            if (!result.violations().isEmpty()) {
                DriftEvent event = new DriftEvent(
                        pair.contractId(),
                        pair.provider(),
                        pair.consumer(),
                        pair.endpoint(),
                        pair.method(),
                        result.driftScore(),
                        result.violations(),
                        Instant.now()
                );
                eventPublisher.publish(event);
                log.warn("Drift detected for {}->{} ({}): score={}, violations={}",
                        pair.provider(), pair.consumer(), pair.endpoint(),
                        result.driftScore(), result.violations().size());
            } else {
                log.debug("No drift for {}->{} ({})", pair.provider(), pair.consumer(), pair.endpoint());
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to process pair {}->{}: {}", pair.provider(), pair.consumer(), e.getMessage());
            return null;
        }
    }

    private String fetchBaselineContract(ContractPair pair) throws Exception {
        String url = registryBaseUrl + "/api/v1/contracts/pair?provider=" + pair.provider()
                + "&consumer=" + pair.consumer() + "&endpoint=" + pair.endpoint();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            return root.has("schemaJson") ? root.get("schemaJson").toString() : null;
        }
        return null;
    }

    private String fetchInferredSchema(ContractPair pair) throws Exception {
        String url = registryBaseUrl + "/api/v1/contracts/pair?provider=" + pair.provider()
                + "&consumer=" + pair.consumer() + "&endpoint=" + pair.endpoint() + "&source=inferred";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            return root.has("schemaJson") ? root.get("schemaJson").toString() : null;
        }
        return null;
    }

    public record ContractPair(
            UUID contractId,
            String provider,
            String consumer,
            String endpoint,
            String method
    ) {}
}
