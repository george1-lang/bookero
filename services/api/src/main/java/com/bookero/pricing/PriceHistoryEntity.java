package com.bookero.pricing;

import com.bookero.flight.FlightEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_history")
public class PriceHistoryEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private FlightEntity flight;

    @Column(name = "algorithm_run_id")
    private UUID algorithmRunId;

    @Column(nullable = false)
    private String fareClassCode;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Instant at;

    public PriceHistoryEntity() {
    }

    public PriceHistoryEntity(UUID id, FlightEntity flight, UUID algorithmRunId, String fareClassCode,
                             BigDecimal price, Instant at) {
        this.id = id;
        this.flight = flight;
        this.algorithmRunId = algorithmRunId;
        this.fareClassCode = fareClassCode;
        this.price = price;
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

    public UUID getAlgorithmRunId() {
        return algorithmRunId;
    }

    public void setAlgorithmRunId(UUID algorithmRunId) {
        this.algorithmRunId = algorithmRunId;
    }

    public String getFareClassCode() {
        return fareClassCode;
    }

    public void setFareClassCode(String fareClassCode) {
        this.fareClassCode = fareClassCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }
}
