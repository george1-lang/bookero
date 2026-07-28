package com.bookero.flight;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fare_class")
public class FareClassEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private FlightEntity flight;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private BigDecimal currentPrice;

    @Column(nullable = false)
    private Integer seatsAllocated;

    public FareClassEntity() {
    }

    public FareClassEntity(UUID id, FlightEntity flight, String code, BigDecimal basePrice,
                          BigDecimal currentPrice, Integer seatsAllocated) {
        this.id = id;
        this.flight = flight;
        this.code = code;
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
        this.seatsAllocated = seatsAllocated;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public Integer getSeatsAllocated() {
        return seatsAllocated;
    }

    public void setSeatsAllocated(Integer seatsAllocated) {
        this.seatsAllocated = seatsAllocated;
    }
}
