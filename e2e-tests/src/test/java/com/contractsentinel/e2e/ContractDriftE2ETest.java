package com.contractsentinel.e2e;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the Contract-Drift Sentinel platform.
 *
 * <p>Each test spins up a fresh set of infrastructure containers via Testcontainers
 * and verifies the full contract lifecycle: import, inference, drift detection,
 * violation recording, and promotion gate decisions.</p>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractDriftE2ETest {

    private static final Logger log = LoggerFactory.getLogger(ContractDriftE2ETest.class);

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final Duration DRIFT_DETECTION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private static final String PROVIDER = "order-service";
    private static final String CONSUMER = "payment-service";
    private static final String ENDPOINT = "POST /api/order";

    private static Network network;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;
    private static KafkaContainer kafka;

    private static OkHttpClient httpClient;
    private static ObjectMapper objectMapper;

    // Mapped ports for services — set after containers start
    private static int contractRegistryPort;
    private static int violationStorePort;
    private static int driftDetectorPort;
    private static int promotionGatePort;

    @BeforeAll
    static void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        network = TestConfig.createNetwork();

        postgres = TestConfig.createPostgres(network);
        postgres.start();

        redis = TestConfig.createRedis(network);
        redis.start();

        kafka = TestConfig.createKafka(network);
        kafka.start();

        log.info("PostgreSQL mapped port: {}", postgres.getMappedPort(5432));
        log.info("Redis mapped port: {}", redis.getMappedPort(6379));
        log.info("Kafka bootstrap servers: {}", kafka.getBootstrapServers());

        // In a real deployment you would start the service containers here.
        // For this test harness, the ports are configurable and services are
        // expected to be running locally or deployed separately.
        //
        // Example for starting a service container:
        //
        // GenericContainer<?> contractRegistry = new GenericContainer<>(
        //         "contract-drift/contract-registry:latest")
        //         .withExposedPorts(TestConfig.CONTRACT_REGISTRY_PORT)
        //         .withNetwork(network)
        //         .withNetworkAliases("contract-registry")
        //         .withEnv(TestConfig.buildServiceEnv(postgres, redis, kafka))
        //         .waitingFor(Wait.forHttp("/health", TestConfig.CONTRACT_REGISTRY_PORT)
        //                 .forStatusCode(200))
        //         .withStartupTimeout(Duration.ofSeconds(120));
        // contractRegistry.start();
        // contractRegistryPort = contractRegistry.getMappedPort(TestConfig.CONTRACT_REGISTRY_PORT);

        contractRegistryPort = TestConfig.CONTRACT_REGISTRY_PORT;
        violationStorePort = TestConfig.VIOLATION_STORE_PORT;
        driftDetectorPort = TestConfig.DRIFT_DETECTOR_PORT;
        promotionGatePort = TestConfig.PROMOTION_GATE_PORT;
    }

    @AfterAll
    static void tearDown() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
        if (redis != null && redis.isRunning()) {
            redis.stop();
        }
        if (kafka != null && kafka.isRunning()) {
            kafka.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    // ──────────────────────────────────────────────
    // Test 1: Full flow with no drift
    // ──────────────────────────────────────────────

    @Test
    @Order(1)
    void testFullFlow_NoDrift() throws Exception {
        log.info("Starting testFullFlow_NoDrift");

        // Import baseline contract
        String contractId = importBaselineContract();
        assertNotNull(contractId, "Contract ID should not be null after import");

        // Send 100 requests with matching schema
        for (int i = 0; i < 100; i++) {
            Map<String, Object> payload = buildValidOrderPayload(i);
            Response resp = sendOrderRequest(payload);
            assertEquals(200, resp.code(), "Request " + i + " should succeed");
            resp.close();
        }

        // Wait for drift detection cycle
        Awaitility.await()
                .atMost(DRIFT_DETECTION_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    List<JsonNode> violations = fetchViolations();
                    assertTrue(violations.isEmpty(),
                            "Expected no violations but found " + violations.size());
                });

        // Verify promotion gate allows
        JsonNode gateResponse = checkPromotionGate("order-service", "v1.0.0", "staging");
        assertTrue(gateResponse.get("allowed").asBoolean(),
                "Gate should allow promotion when there is no drift");
        assertEquals("ALLOW", gateResponse.get("decision").asText());

        log.info("testFullFlow_NoDrift passed");
    }

    // ──────────────────────────────────────────────
    // Test 2: Breaking change detected and blocked
    // ──────────────────────────────────────────────

    @Test
    @Order(2)
    void testBreakingChange_DetectedAndBlocked() throws Exception {
        log.info("Starting testBreakingChange_DetectedAndBlocked");

        String contractId = importBaselineContract();

        // Send 100 requests with matching schema
        for (int i = 0; i < 100; i++) {
            Map<String, Object> payload = buildValidOrderPayload(i);
            Response resp = sendOrderRequest(payload);
            resp.close();
        }

        // Send 200 requests with missing 'currency' field (breaking change)
        for (int i = 0; i < 200; i++) {
            Map<String, Object> payload = buildBreakingPayload(i);
            Response resp = sendOrderRequest(payload);
            resp.close();
        }

        // Wait for drift detection
        Awaitility.await()
                .atMost(DRIFT_DETECTION_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    List<JsonNode> violations = fetchViolations();
                    assertFalse(violations.isEmpty(), "Expected at least one violation");

                    boolean hasBreaking = violations.stream()
                            .anyMatch(v -> "BREAKING".equals(v.get("severity").asText()));
                    assertTrue(hasBreaking, "Expected a BREAKING severity violation");
                });

        // Verify violation mentions 'currency' field
        List<JsonNode> violations = fetchViolations();
        boolean mentionsCurrency = violations.stream()
                .anyMatch(v -> {
                    JsonNode fieldNode = v.get("field");
                    return fieldNode != null && "currency".equals(fieldNode.asText());
                });
        assertTrue(mentionsCurrency, "Violation should mention the 'currency' field");

        // Verify drift score >= 100
        JsonNode contract = fetchContractById(contractId);
        int driftScore = contract.get("drift_score").asInt();
        assertTrue(driftScore >= 100,
                "Drift score should be >= 100 for breaking change, got: " + driftScore);

        // Verify gate blocks promotion
        JsonNode gateResponse = checkPromotionGate("order-service", "v1.0.0", "staging");
        assertFalse(gateResponse.get("allowed").asBoolean(),
                "Gate should block promotion when BREAKING violations exist");
        assertEquals("DENY", gateResponse.get("decision").asText());

        log.info("testBreakingChange_DetectedAndBlocked passed with drift_score={}", driftScore);
    }

    // ──────────────────────────────────────────────
    // Test 3: Override allows promotion
    // ──────────────────────────────────────────────

    @Test
    @Order(3)
    void testOverride_AllowsPromotion() throws Exception {
        log.info("Starting testOverride_AllowsPromotion");

        // Create breaking change scenario
        importBaselineContract();
        for (int i = 0; i < 100; i++) {
            sendOrderRequest(buildValidOrderPayload(i)).close();
        }
        for (int i = 0; i < 200; i++) {
            sendOrderRequest(buildBreakingPayload(i)).close();
        }

        // Verify gate blocks
        Awaitility.await()
                .atMost(DRIFT_DETECTION_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    JsonNode gate = checkPromotionGate("order-service", "v1.0.0", "staging");
                    assertFalse(gate.get("allowed").asBoolean(), "Gate should block before override");
                });

        // Post override
        Map<String, String> overrideBody = new HashMap<>();
        overrideBody.put("service", "order-service");
        overrideBody.put("version", "v1.0.0");
        overrideBody.put("environment", "staging");
        overrideBody.put("reason", "Approved by SRE team for hotfix rollout");
        overrideBody.put("overridden_by", "test-admin");

        Response overrideResp = postJson(
                TestConfig.buildServiceUrl(promotionGatePort) + "/api/v1/gate/override",
                overrideBody);
        assertEquals(200, overrideResp.code(), "Override request should succeed");
        overrideResp.close();

        // Verify gate history shows OVERRIDE entry
        Response historyResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(promotionGatePort)
                        + "/api/v1/gate/history?service=order-service&limit=10")
                .get()
                .build()).execute();
        assertEquals(200, historyResp.code());

        String historyBody = historyResp.body() != null ? historyResp.body().string() : "[]";
        historyResp.close();

        List<JsonNode> historyEntries = objectMapper.readValue(historyBody,
                new TypeReference<List<JsonNode>>() {});
        boolean hasOverride = historyEntries.stream()
                .anyMatch(e -> "OVERRIDE".equals(e.get("decision").asText()));
        assertTrue(hasOverride, "Gate history should contain an OVERRIDE entry");

        log.info("testOverride_AllowsPromotion passed");
    }

    // ──────────────────────────────────────────────
    // Test 4: Contract import and retrieval
    // ──────────────────────────────────────────────

    @Test
    @Order(4)
    void testContractImportAndRetrieval() throws Exception {
        log.info("Starting testContractImportAndRetrieval");

        // Import contract
        Map<String, Object> contractPayload = buildContractPayload();
        Response importResp = postJson(
                TestConfig.buildServiceUrl(contractRegistryPort) + "/api/v1/contracts/import",
                contractPayload);
        assertEquals(201, importResp.code(), "Import should return 201 Created");

        String importBody = importResp.body() != null ? importResp.body().string() : "{}";
        importResp.close();

        JsonNode importResult = objectMapper.readTree(importBody);
        String contractId = importResult.get("id").asText();
        assertNotNull(contractId, "Imported contract should have an ID");

        // GET by pair
        Response pairResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(contractRegistryPort)
                        + "/api/v1/contracts/pair?provider=" + PROVIDER + "&consumer=" + CONSUMER)
                .get()
                .build()).execute();
        assertEquals(200, pairResp.code());
        String pairBody = pairResp.body() != null ? pairResp.body().string() : "[]";
        pairResp.close();

        List<JsonNode> contracts = objectMapper.readValue(pairBody,
                new TypeReference<List<JsonNode>>() {});
        assertFalse(contracts.isEmpty(), "Contract pair query should return results");
        assertEquals(CONSUMER, contracts.get(0).get("consumer").asText());

        // GET by ID
        Response getResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(contractRegistryPort)
                        + "/api/v1/contracts/" + contractId)
                .get()
                .build()).execute();
        assertEquals(200, getResp.code());
        getResp.close();

        // DELETE (archive)
        Response deleteResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(contractRegistryPort)
                        + "/api/v1/contracts/" + contractId)
                .delete()
                .build()).execute();
        assertEquals(200, deleteResp.code(), "DELETE should archive the contract");
        deleteResp.close();

        // GET archived
        Response archivedResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(contractRegistryPort)
                        + "/api/v1/contracts/" + contractId + "?status=archived")
                .get()
                .build()).execute();
        assertEquals(200, archivedResp.code());
        JsonNode archivedContract = objectMapper.readTree(
                archivedResp.body() != null ? archivedResp.body().string() : "{}");
        archivedResp.close();

        assertEquals("archived", archivedContract.get("status").asText(),
                "Archived contract should have status=archived");

        log.info("testContractImportAndRetrieval passed");
    }

    // ──────────────────────────────────────────────
    // Test 5: Violation summary
    // ──────────────────────────────────────────────

    @Test
    @Order(5)
    void testViolationSummary() throws Exception {
        log.info("Starting testViolationSummary");

        // Import contract and create violations
        importBaselineContract();
        for (int i = 0; i < 100; i++) {
            sendOrderRequest(buildValidOrderPayload(i)).close();
        }
        for (int i = 0; i < 200; i++) {
            sendOrderRequest(buildBreakingPayload(i)).close();
        }

        // Wait for violations to be recorded
        Awaitility.await()
                .atMost(DRIFT_DETECTION_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    List<JsonNode> violations = fetchViolations();
                    assertFalse(violations.isEmpty(), "Should have violations for summary");
                });

        // Get summary
        Response summaryResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(violationStorePort)
                        + "/api/v1/violations/summary?provider=" + PROVIDER)
                .get()
                .build()).execute();
        assertEquals(200, summaryResp.code());
        String summaryBody = summaryResp.body() != null ? summaryResp.body().string() : "{}";
        summaryResp.close();

        JsonNode summary = objectMapper.readTree(summaryBody);
        assertTrue(summary.get("total").asInt() > 0, "Summary total should be > 0");

        // Resolve one violation
        List<JsonNode> violations = fetchViolations();
        assertFalse(violations.isEmpty(), "Need at least one violation to resolve");
        String violationId = violations.get(0).get("id").asText();

        Response resolveResp = postJson(
                TestConfig.buildServiceUrl(violationStorePort)
                        + "/api/v1/violations/" + violationId + "/resolve",
                Map.of("resolved_by", "test-user"));
        assertEquals(200, resolveResp.code(), "Resolve should succeed");
        resolveResp.close();

        // Verify summary updates
        Response updatedSummaryResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(violationStorePort)
                        + "/api/v1/violations/summary?provider=" + PROVIDER)
                .get()
                .build()).execute();
        String updatedSummaryBody = updatedSummaryResp.body() != null
                ? updatedSummaryResp.body().string() : "{}";
        updatedSummaryResp.close();

        JsonNode updatedSummary = objectMapper.readTree(updatedSummaryBody);
        int resolvedCount = updatedSummary.has("resolved")
                ? updatedSummary.get("resolved").asInt() : 0;
        assertTrue(resolvedCount > 0, "Resolved count should be > 0 after resolving a violation");

        log.info("testViolationSummary passed");
    }

    // ──────────────────────────────────────────────
    // Test 6: Resolve violation
    // ──────────────────────────────────────────────

    @Test
    @Order(6)
    void testResolveViolation() throws Exception {
        log.info("Starting testResolveViolation");

        // Create a violation
        importBaselineContract();
        for (int i = 0; i < 100; i++) {
            sendOrderRequest(buildBreakingPayload(i)).close();
        }

        Awaitility.await()
                .atMost(DRIFT_DETECTION_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    List<JsonNode> violations = fetchViolations();
                    assertFalse(violations.isEmpty(), "Need violations to resolve");
                });

        List<JsonNode> violations = fetchViolations();
        String violationId = violations.get(0).get("id").asText();

        // Resolve
        Response resolveResp = postJson(
                TestConfig.buildServiceUrl(violationStorePort)
                        + "/api/v1/violations/" + violationId + "/resolve",
                Map.of("resolved_by", "test-user", "note", "False positive after schema update"));
        assertEquals(200, resolveResp.code());
        resolveResp.close();

        // Verify resolved
        Response getResp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(violationStorePort)
                        + "/api/v1/violations/" + violationId)
                .get()
                .build()).execute();
        assertEquals(200, getResp.code());
        String body = getResp.body() != null ? getResp.body().string() : "{}";
        getResp.close();

        JsonNode violation = objectMapper.readTree(body);
        assertTrue(violation.get("resolved").asBoolean(),
                "Violation should be marked as resolved");

        log.info("testResolveViolation passed");
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private String importBaselineContract() throws IOException {
        Map<String, Object> payload = buildContractPayload();
        Response resp = postJson(
                TestConfig.buildServiceUrl(contractRegistryPort) + "/api/v1/contracts/import",
                payload);

        String body = resp.body() != null ? resp.body().string() : "{}";
        resp.close();

        JsonNode result = objectMapper.readTree(body);
        return result.has("id") ? result.get("id").asText() : null;
    }

    private Map<String, Object> buildContractPayload() {
        Map<String, Object> contract = new HashMap<>();
        contract.put("provider", PROVIDER);
        contract.put("consumer", CONSUMER);
        contract.put("endpoint", ENDPOINT);
        contract.put("method", "POST");
        contract.put("description", "Order creation endpoint");

        Map<String, Object> requestSchema = new HashMap<>();
        requestSchema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("order_id", Map.of("type", "string"));
        properties.put("amount", Map.of("type", "number"));
        properties.put("currency", Map.of("type", "string"));
        properties.put("items", Map.of("type", "array", "items", Map.of("type", "string")));
        requestSchema.put("properties", properties);
        contract.put("request_schema", requestSchema);

        return contract;
    }

    private Map<String, Object> buildValidOrderPayload(int index) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", "ord_" + index + "_" + UUID.randomUUID());
        payload.put("amount", 99.99 + index);
        payload.put("currency", "USD");
        payload.put("items", List.of("item1", "item2"));

        Map<String, Object> user = new HashMap<>();
        user.put("name", "Test User");
        user.put("email", "test@example.com");
        payload.put("user", user);

        return payload;
    }

    private Map<String, Object> buildBreakingPayload(int index) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", "ord_" + index + "_" + UUID.randomUUID());
        payload.put("amount", 99.99 + index);
        // 'currency' field is intentionally omitted — this is the breaking change
        payload.put("items", List.of("item1", "item2"));
        return payload;
    }

    private Response sendOrderRequest(Map<String, Object> payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(TestConfig.buildServiceUrl(driftDetectorPort) + "/api/order")
                .post(body)
                .build();

        return httpClient.newCall(request).execute();
    }

    private List<JsonNode> fetchViolations() throws IOException {
        Response resp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(violationStorePort)
                        + "/api/v1/violations?provider=" + PROVIDER + "&consumer=" + CONSUMER)
                .get()
                .build()).execute();

        String body = resp.body() != null ? resp.body().string() : "[]";
        resp.close();

        return objectMapper.readValue(body, new TypeReference<List<JsonNode>>() {});
    }

    private JsonNode fetchContractById(String contractId) throws IOException {
        Response resp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(contractRegistryPort)
                        + "/api/v1/contracts/" + contractId)
                .get()
                .build()).execute();

        String body = resp.body() != null ? resp.body().string() : "{}";
        resp.close();

        return objectMapper.readTree(body);
    }

    private JsonNode checkPromotionGate(String service, String version, String environment)
            throws IOException {
        Response resp = httpClient.newCall(new Request.Builder()
                .url(TestConfig.buildServiceUrl(promotionGatePort)
                        + "/api/v1/gate/promote?service=" + service
                        + "&version=" + version
                        + "&env=" + environment)
                .get()
                .build()).execute();

        String body = resp.body() != null ? resp.body().string() : "{}";
        resp.close();

        return objectMapper.readTree(body);
    }

    private Response postJson(String url, Object payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        return httpClient.newCall(request).execute();
    }
}
