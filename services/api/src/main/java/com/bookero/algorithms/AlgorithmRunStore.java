package com.bookero.algorithms;

import com.bookero.analytics.AnalyticsClient;
import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import com.bookero.pricing.PriceHistoryEntity;
import com.bookero.pricing.PriceHistoryRepository;
import com.bookero.simulation.DemandSnapshotEntity;
import com.bookero.simulation.DemandSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Transactional half of an algorithm run: loads the context, applies the resulting
 * fares, and records the audit trail. Split from {@link AlgorithmRunService} so a
 * failed run can be recorded in its own transaction rather than being rolled back
 * along with the failure that produced it.
 */
@Service
public class AlgorithmRunStore {

  /** Fallback sell-through when no forecast or snapshot is available. */
  private static final double ASSUMED_SELL_THROUGH = 0.5;

  private final FlightRepository flightRepository;
  private final FareClassRepository fareClassRepository;
  private final InventoryRepository inventoryRepository;
  private final DemandSnapshotRepository demandSnapshotRepository;
  private final AlgorithmRunRepository algorithmRunRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final AnalyticsClient analyticsClient;

  public AlgorithmRunStore(
      FlightRepository flightRepository,
      FareClassRepository fareClassRepository,
      InventoryRepository inventoryRepository,
      DemandSnapshotRepository demandSnapshotRepository,
      AlgorithmRunRepository algorithmRunRepository,
      PriceHistoryRepository priceHistoryRepository,
      AnalyticsClient analyticsClient) {
    this.flightRepository = flightRepository;
    this.fareClassRepository = fareClassRepository;
    this.inventoryRepository = inventoryRepository;
    this.demandSnapshotRepository = demandSnapshotRepository;
    this.algorithmRunRepository = algorithmRunRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.analyticsClient = analyticsClient;
  }

  @Transactional
  public AlgorithmRunResponse runAndRecord(Algorithm algorithm, List<UUID> flightIds) {
    List<FlightEntity> flights = flightIds == null || flightIds.isEmpty()
        ? flightRepository.findAllByDepartAtAfter(Instant.now())
        : flightRepository.findAllById(flightIds);

    AlgorithmContext context = buildContext(flights);

    long startNanos = System.nanoTime();
    AlgorithmResult result = algorithm.execute(context);
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

    List<PriceUpdate> updates = result.priceUpdates() == null ? List.of() : result.priceUpdates();
    BigDecimal revenueDelta = revenueDeltaAgainstBaseFare(updates, context);

    AlgorithmRunEntity run = algorithmRunRepository.save(new AlgorithmRunEntity(
        UUID.randomUUID(),
        algorithm.key(),
        null,
        result.status() == null ? "SUCCESS" : result.status(),
        durationMs,
        revenueDelta,
        Instant.now()));

    applyPriceUpdates(updates, flights, run.getId());

    AlgorithmResult timed = new AlgorithmResult(
        run.getStatus(), durationMs, revenueDelta, updates,
        result.flightsAffected() == null ? distinctFlights(updates) : result.flightsAffected(),
        result.message(), result.metrics());

    return AlgorithmRunResponse.of(run, timed);
  }

  /** Recorded outside the failing transaction so the audit row survives the rollback. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AlgorithmRunResponse recordFailure(String algorithmKey, String message, long durationMs) {
    AlgorithmRunEntity run = new AlgorithmRunEntity(
        UUID.randomUUID(), algorithmKey, null, "FAILED", durationMs, null, Instant.now());
    run.setParams(errorJson(message));
    algorithmRunRepository.save(run);

    return AlgorithmRunResponse.of(run, AlgorithmResult.failed(durationMs, message));
  }

  private AlgorithmContext buildContext(List<FlightEntity> flights) {
    List<UUID> ids = flights.stream().map(FlightEntity::getId).toList();
    Map<UUID, FlightEntity> flightsById = flights.stream()
        .collect(Collectors.toMap(FlightEntity::getId, f -> f, (a, b) -> a));

    return new AlgorithmContext(
        ids,
        () -> flightsById,
        memoize(() -> loadFareClasses(ids)),
        memoize(() -> loadInventory(ids)),
        memoize(() -> loadDemandSnapshots(ids)),
        memoize(analyticsClient::demandForecast),
        Instant.now());
  }

  /**
   * Loaders are consulted by both the algorithm and the revenue-delta calculation;
   * memoizing keeps a run to one query per concern and, critically, to at most one
   * call out to the analytics service.
   */
  private static <T> Supplier<T> memoize(Supplier<T> delegate) {
    AtomicReference<T> cached = new AtomicReference<>();
    return () -> {
      T value = cached.get();
      if (value == null) {
        value = delegate.get();
        cached.set(value);
      }
      return value;
    };
  }

  private Map<UUID, List<FareClassEntity>> loadFareClasses(List<UUID> flightIds) {
    if (flightIds.isEmpty()) {
      return Map.of();
    }
    return fareClassRepository.findAllByFlightIdIn(flightIds).stream()
        .collect(Collectors.groupingBy(fc -> fc.getFlight().getId()));
  }

  private Map<UUID, InventoryEntity> loadInventory(List<UUID> flightIds) {
    if (flightIds.isEmpty()) {
      return Map.of();
    }
    return inventoryRepository.findAllById(flightIds).stream()
        .collect(Collectors.toMap(InventoryEntity::getFlightId, i -> i));
  }

  private Map<UUID, List<DemandSnapshotEntity>> loadDemandSnapshots(List<UUID> flightIds) {
    Set<UUID> wanted = new HashSet<>(flightIds);
    return demandSnapshotRepository.findLatestPerFlight().stream()
        .filter(ds -> wanted.contains(ds.getFlight().getId()))
        .collect(Collectors.groupingBy(ds -> ds.getFlight().getId()));
  }

  private void applyPriceUpdates(List<PriceUpdate> updates, List<FlightEntity> flights, UUID runId) {
    if (updates.isEmpty()) {
      return;
    }

    Map<UUID, FlightEntity> flightsById = flights.stream()
        .collect(Collectors.toMap(FlightEntity::getId, f -> f, (a, b) -> a));
    Map<UUID, Map<String, FareClassEntity>> byFlight = new HashMap<>();
    for (FareClassEntity fc : fareClassRepository.findAllByFlightIdIn(List.copyOf(flightsById.keySet()))) {
      byFlight.computeIfAbsent(fc.getFlight().getId(), k -> new HashMap<>()).put(fc.getCode(), fc);
    }

    List<FareClassEntity> changed = new ArrayList<>(updates.size());
    List<PriceHistoryEntity> history = new ArrayList<>(updates.size());
    Instant at = Instant.now();

    for (PriceUpdate update : updates) {
      FlightEntity flight = flightsById.get(update.flightId());
      FareClassEntity fareClass = byFlight
          .getOrDefault(update.flightId(), Map.of())
          .get(update.fareClassCode());
      if (flight == null || fareClass == null || update.newPrice() == null) {
        continue;
      }

      fareClass.setCurrentPrice(update.newPrice().setScale(2, RoundingMode.HALF_UP));
      if (update.seatsAllocated() != null && update.seatsAllocated() >= 0) {
        fareClass.setSeatsAllocated(update.seatsAllocated());
      }
      changed.add(fareClass);
      history.add(new PriceHistoryEntity(
          UUID.randomUUID(), flight, runId, update.fareClassCode(), fareClass.getCurrentPrice(), at));
    }

    fareClassRepository.saveAll(changed);
    priceHistoryRepository.saveAll(history);
  }

  /**
   * Revenue delta is measured against the published base fare, never against the
   * price the previous run happened to leave behind - otherwise a second run of the
   * same algorithm would report a delta of roughly zero. Expected seats sold is
   * capped at the class allocation.
   */
  private BigDecimal revenueDeltaAgainstBaseFare(List<PriceUpdate> updates, AlgorithmContext context) {
    if (updates.isEmpty()) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    Map<UUID, List<FareClassEntity>> fareClasses = context.getFareClasses();
    Map<UUID, List<DemandSnapshotEntity>> snapshots = context.getDemandSnapshots();

    BigDecimal total = BigDecimal.ZERO;
    for (PriceUpdate update : updates) {
      FareClassEntity fareClass = fareClasses.getOrDefault(update.flightId(), List.of()).stream()
          .filter(fc -> fc.getCode().equals(update.fareClassCode()))
          .findFirst()
          .orElse(null);
      if (fareClass == null || update.newPrice() == null) {
        continue;
      }

      double demand = expectedDemand(update.flightId(), context, snapshots);
      BigDecimal expectedSeats = BigDecimal.valueOf(fareClass.getSeatsAllocated())
          .multiply(BigDecimal.valueOf(demand))
          .setScale(0, RoundingMode.DOWN);

      total = total.add(
          update.newPrice().subtract(fareClass.getBasePrice()).multiply(expectedSeats));
    }
    return total.setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Snapshots are local and free; the analytics forecast is only consulted when there
   * is no observation, so a post-booking reprice never blocks on an HTTP round trip.
   */
  private double expectedDemand(
      UUID flightId,
      AlgorithmContext context,
      Map<UUID, List<DemandSnapshotEntity>> snapshots) {
    OptionalDouble observed = snapshots.getOrDefault(flightId, List.of()).stream()
        .mapToDouble(DemandSnapshotEntity::getDemandScore)
        .average();
    if (observed.isPresent()) {
      return observed.getAsDouble();
    }
    Double predicted = context.getDemandForecast().map(f -> f.get(flightId)).orElse(null);
    return predicted != null ? predicted : ASSUMED_SELL_THROUGH;
  }

  private int distinctFlights(List<PriceUpdate> updates) {
    return (int) updates.stream().map(PriceUpdate::flightId).distinct().count();
  }

  private String errorJson(String message) {
    String safe = message == null ? "unknown error" : message;
    return "{\"error\":\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
  }
}
