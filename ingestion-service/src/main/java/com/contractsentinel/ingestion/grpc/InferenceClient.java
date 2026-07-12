package com.contractsentinel.ingestion.grpc;

import inference.SchemaInferenceGrpc;
import inference.InferSchemaRequest;
import inference.InferSchemaResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class InferenceClient {

    private static final Logger log = LoggerFactory.getLogger(InferenceClient.class);

    private final ManagedChannel channel;
    private final String host;
    private final int port;

    public InferenceClient(
            @Value("${ingestion.grpc.inference.host:localhost}") String host,
            @Value("${ingestion.grpc.inference.port:50051}") int port) {
        this.host = host;
        this.port = port;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    public CompletableFuture<String> inferSchema(List<String> samples, String serviceId, String endpoint, String method) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var blockingStub = SchemaInferenceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS);

                var request = InferSchemaRequest.newBuilder()
                        .addAllSamples(samples)
                        .setServiceId(serviceId)
                        .setEndpoint(endpoint)
                        .setMethod(method)
                        .build();

                InferSchemaResponse response = blockingStub.inferSchema(request);
                log.info("Schema inference completed for {}/{}", serviceId, endpoint);
                return response.getSchemaJson();
            } catch (Exception e) {
                log.error("Schema inference failed for {}/{}: {}", serviceId, endpoint, e.getMessage());
                throw new RuntimeException("gRPC inference failed", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down gRPC channel to {}:{}", host, port);
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
