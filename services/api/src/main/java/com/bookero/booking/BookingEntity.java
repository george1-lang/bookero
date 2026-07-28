package com.bookero.booking;

import com.bookero.auth.UserEntity;
import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FlightEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class BookingEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private FlightEntity flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fare_class_id", nullable = false)
    private FareClassEntity fareClass;

    @Column(nullable = false)
    private BigDecimal paidPrice;

    @Column(nullable = false)
    private Instant createdAt;

    public BookingEntity() {
    }

    public BookingEntity(UUID id, UserEntity user, FlightEntity flight, FareClassEntity fareClass,
                        BigDecimal paidPrice, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.flight = flight;
        this.fareClass = fareClass;
        this.paidPrice = paidPrice;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public FlightEntity getFlight() {
        return flight;
    }

    public void setFlight(FlightEntity flight) {
        this.flight = flight;
    }

    public FareClassEntity getFareClass() {
        return fareClass;
    }

    public void setFareClass(FareClassEntity fareClass) {
        this.fareClass = fareClass;
    }

    public BigDecimal getPaidPrice() {
        return paidPrice;
    }

    public void setPaidPrice(BigDecimal paidPrice) {
        this.paidPrice = paidPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
