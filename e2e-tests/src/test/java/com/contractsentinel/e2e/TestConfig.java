package com.contractsentinel.e2e;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration that defines and manages Testcontainers infrastructure
 * for E2E integration tests.
 */
public final class TestConfig {

    private static final Logger log = LoggerFactory.getLogger(TestConfig.class);

    private TestConfig() {
    }

    public static final String POSTGRES_IMAGE = "postgres:16-alpine";
    public static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.6.0";
    public static final String REDIS_IMAGE = "redis:7-alpine";
    public static final String ZOOKEEPER_IMAGE = "confluentinc/cp-zookeeper:7.6.0";

    public static final String POSTGRES_DB = "sentinel";
    public static final String POSTGRES_USER = "sentinel";
    public static final String POSTGRES_PASSWORD = "sentinel";

    public static final int CONTRACT_REGISTRY_PORT = 8083;
    public static final int VIOLATION_STORE_PORT = 8082;
    public static final int DRIFT_DETECTOR_PORT = 8081;
    public static final int INFERENCE_ENGINE_PORT = 8080;
    public static final int PROMOTION_GATE_PORT = 8084;

    /**
     * Creates the network shared by all containers.
     */
    public static Network createNetwork() {
        return Network.newNetwork();
    }

    /**
     * Creates a PostgreSQL container for the contract repository.
     */
    public static PostgreSQLContainer<?> createPostgres(Network network) {
        return (PostgreSQLContainer<?>) new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName(POSTGRES_DB)
                .withUsername(POSTGRES_USER)
                .withPassword(POSTGRES_PASSWORD)
                .withStartupTimeout(Duration.ofSeconds(60))
                .withLogConsumer(new Slf4jLogConsumer(
                        LoggerFactory.getLogger("testcontainers.postgres")));
    }

    /**
     * Creates a Redis container for caching and session state.
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createRedis(Network network) {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(6379)
                .withNetwork(network)
                .withNetworkAliases("redis")
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
                .withStartupTimeout(Duration.ofSeconds(30))
                .withLogConsumer(new Slf4jLogConsumer(
                        LoggerFactory.getLogger("testcontainers.redis")));
    }

    /**
     * Creates a Kafka container (with internal Zookeeper).
     */
    public static KafkaContainer createKafka(Network network) {
        return (KafkaContainer) new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withStartupTimeout(Duration.ofSeconds(60))
                .withLogConsumer(new Slf4jLogConsumer(
                        LoggerFactory.getLogger("testcontainers.kafka")));
    }

    /**
     * Builds environment variables that service containers need to connect to
     * the infrastructure containers.
     */
    public static Map<String, String> buildServiceEnv(PostgreSQLContainer<?> postgres,
                                                       GenericContainer<?> redis,
                                                       KafkaContainer kafka) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", "postgres");
        env.put("DB_PORT", String.valueOf(postgres.getMappedPort(5432)));
        env.put("DB_NAME", POSTGRES_DB);
        env.put("DB_USER", POSTGRES_USER);
        env.put("DB_PASSWORD", POSTGRES_PASSWORD);
        env.put("REDIS_HOST", "redis");
        env.put("REDIS_PORT", String.valueOf(redis.getMappedPort(6379)));
        env.put("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        return env;
    }

    /**
     * Builds JDBC URL for direct connection from test code.
     */
    public static String buildJdbcUrl(PostgreSQLContainer<?> postgres) {
        return String.format("jdbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getMappedPort(5432),
                POSTGRES_DB);
    }

    /**
     * Builds base URL for a service running on localhost with the given mapped port.
     */
    public static String buildServiceUrl(int mappedPort) {
        return String.format("http://localhost:%d", mappedPort);
    }
}
