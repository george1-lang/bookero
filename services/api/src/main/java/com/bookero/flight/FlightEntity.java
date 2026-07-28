package com.bookero.flight;

import com.bookero.route.RouteEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flight")
public class FlightEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;

    @Column(nullable = false)
    private String flightNo;

    @Column(nullable = false)
    private Instant departAt;

    public FlightEntity() {
    }

    public FlightEntity(UUID id, RouteEntity route, String flightNo, Instant departAt) {
        this.id = id;
        this.route = route;
        this.flightNo = flightNo;
        this.departAt = departAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RouteEntity getRoute() {
        return route;
    }

    public void setRoute(RouteEntity route) {
        this.route = route;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public void setFlightNo(String flightNo) {
        this.flightNo = flightNo;
    }

    public Instant getDepartAt() {
        return departAt;
    }

    public void setDepartAt(Instant departAt) {
        this.departAt = departAt;
    }
}
