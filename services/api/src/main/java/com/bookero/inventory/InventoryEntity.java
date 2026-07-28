package com.bookero.inventory;

import com.bookero.common.AssignedIdEntity;

import com.bookero.flight.FlightEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class InventoryEntity extends AssignedIdEntity<UUID> {
    @Id
    private UUID flightId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "flight_id")
    private FlightEntity flight;

    @Column(nullable = false)
    private Integer seatsTotal;

    @Column(nullable = false)
    private Integer seatsLeft;

    public InventoryEntity() {
    }

    public InventoryEntity(UUID flightId, FlightEntity flight, Integer seatsTotal, Integer seatsLeft) {
        this.flightId = flightId;
        this.flight = flight;
        this.seatsTotal = seatsTotal;
        this.seatsLeft = seatsLeft;
    }

    public UUID getFlightId() {
        return flightId;
    }

    public void setFlightId(UUID flightId) {
        this.flightId = flightId;
    }

    public FlightEntity getFlight() {
        return flight;
    }

    public void setFlight(FlightEntity flight) {
        this.flight = flight;
    }

    public Integer getSeatsTotal() {
        return seatsTotal;
    }

    public void setSeatsTotal(Integer seatsTotal) {
        this.seatsTotal = seatsTotal;
    }

    public Integer getSeatsLeft() {
        return seatsLeft;
    }

    public void setSeatsLeft(Integer seatsLeft) {
        this.seatsLeft = seatsLeft;
    }
  @Override
  public UUID getId() {
    return getFlightId();
  }
}
