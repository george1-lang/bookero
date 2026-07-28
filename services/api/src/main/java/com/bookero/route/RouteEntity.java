package com.bookero.route;

import com.bookero.common.AssignedIdEntity;

import com.bookero.airport.AirportEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "route")
public class RouteEntity extends AssignedIdEntity<UUID> {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_code", nullable = false)
    private AirportEntity origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_code", nullable = false)
    private AirportEntity destination;

    private Integer distanceKm;

    public RouteEntity() {
    }

    public RouteEntity(UUID id, AirportEntity origin, AirportEntity destination, Integer distanceKm) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AirportEntity getOrigin() {
        return origin;
    }

    public void setOrigin(AirportEntity origin) {
        this.origin = origin;
    }

    public AirportEntity getDestination() {
        return destination;
    }

    public void setDestination(AirportEntity destination) {
        this.destination = destination;
    }

    public Integer getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Integer distanceKm) {
        this.distanceKm = distanceKm;
    }
}
