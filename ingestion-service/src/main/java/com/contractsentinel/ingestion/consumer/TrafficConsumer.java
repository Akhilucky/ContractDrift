package com.contractsentinel.ingestion.consumer;

import com.contractsentinel.ingestion.clickhouse.ClickHouseRepository;
import com.contractsentinel.ingestion.grpc.InferenceClient;
import com.contractsentinel.ingestion.model.NormalizedSample;
import com.contractsentinel.ingestion.normalizer.PayloadNormalizer;
import com.google.common.hash.Hashing;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Component
public class TrafficConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrafficConsumer.class);

    private final DeduplicationService deduplicationService;
    private final PayloadNormalizer payloadNormalizer;
    private final InferenceClient inferenceClient;
    private final ClickHouseRepository clickHouseRepository;

    public TrafficConsumer(DeduplicationService deduplicationService,
                           PayloadNormalizer payloadNormalizer,
                           InferenceClient inferenceClient,
                           ClickHouseRepository clickHouseRepository) {
        this.deduplicationService = deduplicationService;
        this.payloadNormalizer = payloadNormalizer;
        this.inferenceClient = inferenceClient;
        this.clickHouseRepository = clickHouseRepository;
    }

    @KafkaListener(topics = "raw-traffic", groupId = "ingestion-group-${ENV:dev}")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        log.debug("Received record from partition {} offset {}", record.partition(), record.offset());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            String rawPayload = deserializePayload(record);
            String contentHash = Hashing.sha256()
                    .hashString(rawPayload, StandardCharsets.UTF_8)
                    .toString();

            if (deduplicationService.isDuplicate(contentHash)) {
                log.debug("Duplicate message skipped, hash: {}", contentHash);
                ack.acknowledge();
                return;
            }

            String normalized = payloadNormalizer.normalize(rawPayload);

            String serviceId = extractServiceId(record);
            String endpoint = extractEndpoint(record);
            String method = extractMethod(record);

            var sample = new NormalizedSample(
                    serviceId, endpoint, method, normalized, contentHash, Instant.now()
            );

            CompletableFuture<Void> persistFuture = CompletableFuture.runAsync(
                    () -> clickHouseRepository.insertSample(sample), executor);

            CompletableFuture<Void> inferenceFuture = CompletableFuture.runAsync(
                    () -> {
                        try {
                            inferenceClient.inferSchema(
                                    List.of(normalized), serviceId, endpoint, method
                            ).get();
                        } catch (Exception e) {
                            log.warn("Schema inference failed asynchronously: {}", e.getMessage());
                        }
                    }, executor);

            CompletableFuture.allOf(persistFuture, inferenceFuture).join();

            deduplicationService.markProcessed(contentHash);
            ack.acknowledge();

            log.info("Processed sample for {}/{}:{}", serviceId, endpoint, method);
        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    private String deserializePayload(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (value instanceof byte[]) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value != null ? value.toString() : "{}";
    }

    private String extractServiceId(ConsumerRecord<String, Object> record) {
        var headers = record.headers();
        var header = headers.lastHeader("service_id");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return record.key() != null ? record.key() : "unknown";
    }

    private String extractEndpoint(ConsumerRecord<String, Object> record) {
        var headers = record.headers();
        var header = headers.lastHeader("endpoint");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "/unknown";
    }

    private String extractMethod(ConsumerRecord<String, Object> record) {
        var headers = record.headers();
        var header = headers.lastHeader("method");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "GET";
    }
}
