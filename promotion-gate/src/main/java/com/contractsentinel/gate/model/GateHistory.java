package com.contractsentinel.gate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "gate_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "version_sha", nullable = false)
    private String versionSha;

    @Column(name = "target_env", nullable = false)
    private String targetEnv;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "drift_score")
    private int driftScore;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "override_by")
    private String overrideBy;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
