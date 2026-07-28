package com.bookero.algorithms;

import com.bookero.common.AssignedIdEntity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "algorithm_run")
public class AlgorithmRunEntity extends AssignedIdEntity<UUID> {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String algorithmKey;

    @JdbcTypeCode(SqlTypes.JSON)
    private String params;

    @Column(nullable = false)
    private String status;

    private Long durationMs;

    private BigDecimal revenueDelta;

    @Column(nullable = false)
    private Instant createdAt;

    public AlgorithmRunEntity() {
    }

    public AlgorithmRunEntity(UUID id, String algorithmKey, String params, String status,
                             Long durationMs, BigDecimal revenueDelta, Instant createdAt) {
        this.id = id;
        this.algorithmKey = algorithmKey;
        this.params = params;
        this.status = status;
        this.durationMs = durationMs;
        this.revenueDelta = revenueDelta;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAlgorithmKey() {
        return algorithmKey;
    }

    public void setAlgorithmKey(String algorithmKey) {
        this.algorithmKey = algorithmKey;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public BigDecimal getRevenueDelta() {
        return revenueDelta;
    }

    public void setRevenueDelta(BigDecimal revenueDelta) {
        this.revenueDelta = revenueDelta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
