package com.bookero.algorithms;

import com.bookero.analytics.AnalyticsClient;
import com.bookero.booking.BookingRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlgorithmRunService {

  private static final Logger log = LoggerFactory.getLogger(AlgorithmRunService.class);

  private final AlgorithmRegistry algorithmRegistry;
  private final FlightRepository flightRepository;
  private final FareClassRepository fareClassRepository;
  private final InventoryRepository inventoryRepository;
  private final DemandSnapshotRepository demandSnapshotRepository;
  private final AlgorithmRunRepository algorithmRunRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final BookingRepository bookingRepository;
  private final AnalyticsClient analyticsClient;

  public AlgorithmRunService(
      AlgorithmRegistry algorithmRegistry,
      FlightRepository flightRepository,
      FareClassRepository fareClassRepository,
      InventoryRepository inventoryRepository,
      DemandSnapshotRepository demandSnapshotRepository,
      AlgorithmRunRepository algorithmRunRepository,
      PriceHistoryRepository priceHistoryRepository,
      BookingRepository bookingRepository,
      AnalyticsClient analyticsClient
  ) {
    this.algorithmRegistry = algorithmRegistry;
    this.flightRepository = flightRepository;
    this.fareClassRepository = fareClassRepository;
    this.inventoryRepository = inventoryRepository;
    this.demandSnapshotRepository = demandSnapshotRepository;
    this.algorithmRunRepository = algorithmRunRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.bookingRepository = bookingRepository;
    this.analyticsClient = analyticsClient;
  }

  @Transactional
  public AlgorithmRunEntity execute(String algorithmKey, List<UUID> flightIds) {
    var startNanos = System.nanoTime();

    try {
      var algorithm = algorithmRegistry.get(algorithmKey);

      // Build context once per run: load all flights, fare classes, inventory, demand
      var flights = getFlights(flightIds);
      var context = buildContext(flights);

      // Execute algorithm
      var result = algorithm.execute(context);

      // Apply price updates to database
      var priceHistoryEntries = applyPriceUpdates(result.priceUpdates());

      // Calculate revenue delta
      var revenueDelta = calculateRevenueDelta(result.priceUpdates(), context);

      // Persist algorithm run
      var durationMs = (System.nanoTime() - startNanos) / 1_000_000;
      var run = new AlgorithmRunEntity(
          UUID.randomUUID(),
          algorithmKey,
          null,
          "SUCCESS",
          durationMs,
          revenueDelta,
          Instant.now()
      );
      algorithmRunRepository.save(run);

      // Persist price history with run ID
      priceHistoryEntries.forEach(ph -> {
        ph.setAlgorithmRunId(run.getId());
        priceHistoryRepository.save(ph);
      });

      return run;

    } catch (Exception e) {
      var durationMs = (System.nanoTime() - startNanos) / 1_000_000;
      var message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.error("Algorithm {} failed: {}", algorithmKey, message, e);

      var run = new AlgorithmRunEntity(
          UUID.randomUUID(),
          algorithmKey,
          null,
          "FAILED",
          durationMs,
          null,
          Instant.now()
      );

      // Store error message as params (JSON)
      run.setParams("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
      algorithmRunRepository.save(run);

      // Do NOT persist price updates; keep last good prices
      throw new RuntimeException("Algorithm execution failed: " + message, e);
    }
  }

  private List<FlightEntity> getFlights(List<UUID> flightIds) {
    if (flightIds == null || flightIds.isEmpty()) {
      return flightRepository.findAllByDepartAtAfter(Instant.now());
    }
    return flightRepository.findAllById(flightIds);
  }

  private AlgorithmContext buildContext(List<FlightEntity> flights) {
    var flightIds = flights.stream()
        .map(FlightEntity::getId)
        .toList();

    return new AlgorithmContext(
        flightIds,
        () -> loadFareClasses(flightIds),
        () -> loadInventory(flightIds),
        () -> loadDemandSnapshots(flightIds),
        analyticsClient::demandForecast,
        Instant.now()
    );
  }

  private Map<UUID, List<FareClassEntity>> loadFareClasses(List<UUID> flightIds) {
    return fareClassRepository.findAllByFlightIdIn(flightIds).stream()
        .collect(Collectors.groupingBy(fc -> fc.getFlight().getId()));
  }

  private Map<UUID, InventoryEntity> loadInventory(List<UUID> flightIds) {
    return inventoryRepository.findAllById(flightIds).stream()
        .collect(Collectors.toMap(InventoryEntity::getFlightId, i -> i));
  }

  private Map<UUID, List<DemandSnapshotEntity>> loadDemandSnapshots(List<UUID> flightIds) {
    return demandSnapshotRepository.findLatestPerFlight().stream()
        .filter(ds -> flightIds.contains(ds.getFlight().getId()))
        .collect(Collectors.groupingBy(ds -> ds.getFlight().getId()));
  }

  private List<PriceHistoryEntity> applyPriceUpdates(List<PriceUpdate> updates) {
    var historyEntries = new ArrayList<PriceHistoryEntity>();

    for (var update : updates) {
      // Load flight entity
      var flight = flightRepository.findById(update.flightId()).orElse(null);
      if (flight == null) continue;

      // Update fare class current price
      var fareClasses = fareClassRepository.findAllByFlightId(update.flightId());
      for (var fc : fareClasses) {
        if (fc.getCode().equals(update.fareClassCode())) {
          fc.setCurrentPrice(update.newPrice());
          fareClassRepository.save(fc);

          // Create price history entry
          var history = new PriceHistoryEntity(
              UUID.randomUUID(),
              flight,
              null,
              update.fareClassCode(),
              update.newPrice(),
              Instant.now()
          );
          historyEntries.add(history);
          break;
        }
      }
    }

    return historyEntries;
  }

  private BigDecimal calculateRevenueDelta(List<PriceUpdate> updates, AlgorithmContext context) {
    var revenueDelta = BigDecimal.ZERO;

    var forecast = context.getDemandForecast();
    var inventory = context.getInventory();

    for (var update : updates) {
      var inv = inventory.get(update.flightId());
      if (inv == null) continue;

      // Expected demand for this flight (from forecast or default)
      var expectedDemand = forecast
          .flatMap(f -> Optional.ofNullable(f.get(update.flightId())))
          .orElse(0.5); // Default to 50% if forecast unavailable

      // Seats expected to sell (min of allocated and expected demand)
      var seatsAllocated = BigDecimal.valueOf(inv.getSeatsTotal());
      var expectedSeatsToSell = seatsAllocated
          .multiply(BigDecimal.valueOf(expectedDemand))
          .setScale(0, RoundingMode.DOWN);

      // Revenue delta = new price * seats - base price * seats
      var newRevenue = update.newPrice().multiply(expectedSeatsToSell);
      var baseRevenue = update.oldPrice().multiply(expectedSeatsToSell);
      var delta = newRevenue.subtract(baseRevenue);

      revenueDelta = revenueDelta.add(delta);
    }

    return revenueDelta.setScale(2, RoundingMode.HALF_UP);
  }
}
