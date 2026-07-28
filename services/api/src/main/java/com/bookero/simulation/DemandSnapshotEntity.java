package com.bookero.simulation;

import com.bookero.common.AssignedIdEntity;

import com.bookero.flight.FlightEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demand_snapshot")
public class DemandSnapshotEntity extends AssignedIdEntity<UUID> {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private FlightEntity flight;

    @Column(nullable = false)
    private Double demandScore;

    @Column(nullable = false)
    private Instant at;

    public DemandSnapshotEntity() {
    }

    public DemandSnapshotEntity(UUID id, FlightEntity flight, Double demandScore, Instant at) {
        this.id = id;
        this.flight = flight;
        this.demandScore = demandScore;
        this.at = at;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FlightEntity getFlight() {
        return flight;
    }

    public void setFlight(FlightEntity flight) {
        this.flight = flight;
    }

    public Double getDemandScore() {
        return demandScore;
    }

    public void setDemandScore(Double demandScore) {
        this.demandScore = demandScore;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }
}
