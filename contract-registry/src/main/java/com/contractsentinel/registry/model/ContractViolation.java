package com.contractsentinel.registry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_violations")
public class ContractViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "violation_type", nullable = false)
    private String violationType;

    @Column(name = "diff_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String diffJson;

    @Column(name = "deployment_id")
    private String deploymentId;

    @Column(nullable = false)
    private boolean resolved;

    public ContractViolation() {}

    public ContractViolation(UUID contractId, Instant detectedAt, String violationType,
                             String diffJson, String deploymentId, boolean resolved) {
        this.contractId = contractId;
        this.detectedAt = detectedAt;
        this.violationType = violationType;
        this.diffJson = diffJson;
        this.deploymentId = deploymentId;
        this.resolved = resolved;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID contractId) { this.contractId = contractId; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    public String getViolationType() { return violationType; }
    public void setViolationType(String violationType) { this.violationType = violationType; }
    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}
