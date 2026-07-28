package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.inventory.InventoryEntity;
import com.bookero.simulation.DemandSnapshotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record AlgorithmContext(
    List<UUID> flightIds,
    Supplier<Map<UUID, List<FareClassEntity>>> fareClassesLoader,
    Supplier<Map<UUID, InventoryEntity>> inventoryLoader,
    Supplier<Map<UUID, List<DemandSnapshotEntity>>> demandSnapshotsLoader,
    Supplier<Optional<Map<UUID, Double>>> demandForecastLoader,
    Instant timestamp
) {

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
}
