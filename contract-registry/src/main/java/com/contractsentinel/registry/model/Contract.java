package com.contractsentinel.registry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "consumer_id", nullable = false)
    private String consumerId;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private int version;

    @Column(name = "schema_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String schemaJson;

    @Column(name = "inferred_at")
    private Instant inferredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public Contract() {}

    public Contract(String providerId, String consumerId, String endpoint, String method,
                    int version, String schemaJson, Instant inferredAt, Source source, Status status) {
        this.providerId = providerId;
        this.consumerId = consumerId;
        this.endpoint = endpoint;
        this.method = method;
        this.version = version;
        this.schemaJson = schemaJson;
        this.inferredAt = inferredAt;
        this.source = source;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public Instant getInferredAt() { return inferredAt; }
    public void setInferredAt(Instant inferredAt) { this.inferredAt = inferredAt; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Source {
        inferred, manual, imported
    }

    public enum Status {
        active, superseded, archived
    }
}
