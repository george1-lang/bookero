package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FlightEntity;
import com.bookero.inventory.InventoryEntity;
import com.bookero.simulation.DemandSnapshotEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything an algorithm may read, loaded at most once per run. Suppliers are
 * memoized by the caller, so an algorithm that never asks for the demand forecast
 * never triggers a call to the analytics service.
 */
public record AlgorithmContext(
    List<UUID> flightIds,
    Supplier<Map<UUID, FlightEntity>> flightsLoader,
    Supplier<Map<UUID, List<FareClassEntity>>> fareClassesLoader,
    Supplier<Map<UUID, InventoryEntity>> inventoryLoader,
    Supplier<Map<UUID, List<DemandSnapshotEntity>>> demandSnapshotsLoader,
    Supplier<Optional<Map<UUID, Double>>> demandForecastLoader,
    Instant timestamp
) {

  public Map<UUID, FlightEntity> getFlights() {
    return flightsLoader.get();
  }

  public Map<UUID, List<FareClassEntity>> getFareClasses() {
    return fareClassesLoader.get();
  }

  public Map<UUID, InventoryEntity> getInventory() {
    return inventoryLoader.get();
  }

  public Map<UUID, List<DemandSnapshotEntity>> getDemandSnapshots() {
    return demandSnapshotsLoader.get();
  }

  public Optional<Map<UUID, Double>> getDemandForecast() {
    return demandForecastLoader.get();
  }

  /** Latest observed demand for a flight, falling back to the forecast, then to 0.5. */
  public double demandFor(UUID flightId) {
    return getDemandSnapshots().getOrDefault(flightId, List.of()).stream()
        .mapToDouble(DemandSnapshotEntity::getDemandScore)
        .average()
        .orElseGet(() -> getDemandForecast().map(f -> f.get(flightId)).orElse(0.5));
  }

  public double loadFactor(UUID flightId) {
    InventoryEntity inv = getInventory().get(flightId);
    if (inv == null || inv.getSeatsTotal() == null || inv.getSeatsTotal() == 0) {
      return 0.0;
    }
    return Math.clamp((double) (inv.getSeatsTotal() - inv.getSeatsLeft()) / inv.getSeatsTotal(), 0.0, 1.0);
  }

  public long daysToDeparture(UUID flightId) {
    FlightEntity flight = getFlights().get(flightId);
    if (flight == null) {
      return 0;
    }
    return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(timestamp, flight.getDepartAt()));
  }
}
