package com.contractsentinel.ingestion.clickhouse;

import com.contractsentinel.ingestion.model.NormalizedSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ClickHouseRepository {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseRepository.class);

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS contract_sentinel.raw_samples (
                service_id String,
                endpoint String,
                method String,
                normalized_payload String,
                content_hash String,
                ingested_at DateTime64(3)
            ) ENGINE = MergeTree()
            ORDER BY (service_id, ingested_at)
            """;

    private static final String INSERT_SQL = """
            INSERT INTO contract_sentinel.raw_samples
            (service_id, endpoint, method, normalized_payload, content_hash, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String QUERY_SQL = """
            SELECT service_id, endpoint, method, normalized_payload, content_hash, ingested_at
            FROM contract_sentinel.raw_samples
            WHERE ingested_at >= ? AND ingested_at <= ?
            ORDER BY ingested_at DESC
            """;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public ClickHouseRepository(
            @Value("${ingestion.clickhouse.jdbc-url}") String jdbcUrl,
            @Value("${ingestion.clickhouse.username:default}") String username,
            @Value("${ingestion.clickhouse.password:}") String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        initializeSchema();
    }

    private void initializeSchema() {
        try (Connection conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS contract_sentinel");
            stmt.execute(CREATE_TABLE_SQL);
            log.info("ClickHouse schema initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize ClickHouse schema: {}", e.getMessage());
        }
    }

    public void insertSample(NormalizedSample sample) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, sample.serviceId());
            ps.setString(2, sample.endpoint());
            ps.setString(3, sample.method());
            ps.setString(4, sample.normalizedPayload());
            ps.setString(5, sample.contentHash());
            ps.setTimestamp(6, Timestamp.from(sample.ingestedAt()));
            ps.executeUpdate();
            log.debug("Inserted sample {} for {}/{}", sample.contentHash(), sample.serviceId(), sample.endpoint());
        } catch (SQLException e) {
            log.error("Failed to insert sample into ClickHouse: {}", e.getMessage());
        }
    }

    public List<NormalizedSample> querySamples(Instant from, Instant to) {
        List<NormalizedSample> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_SQL)) {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new NormalizedSample(
                            rs.getString("service_id"),
                            rs.getString("endpoint"),
                            rs.getString("method"),
                            rs.getString("normalized_payload"),
                            rs.getString("content_hash"),
                            rs.getTimestamp("ingested_at").toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query samples from ClickHouse: {}", e.getMessage());
        }
        return results;
    }

    private Connection getConnection() throws SQLException {
        if (username != null && !username.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
        return DriverManager.getConnection(jdbcUrl);
    }
}
