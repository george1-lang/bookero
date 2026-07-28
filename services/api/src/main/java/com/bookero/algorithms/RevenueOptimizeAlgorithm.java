package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.inventory.InventoryEntity;
import com.bookero.simulation.DemandSnapshotRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class RevenueOptimizeAlgorithm implements Algorithm {

  private final DemandSnapshotRepository demandSnapshotRepository;

  public RevenueOptimizeAlgorithm(DemandSnapshotRepository demandSnapshotRepository) {
    this.demandSnapshotRepository = demandSnapshotRepository;
  }

  @Override
  public String key() {
    return "revenue_optimize";
  }

  @Override
  public String displayName() {
    return "Revenue Optimize (Demand-Based)";
  }

  @Override
  public String family() {
    return "Optimization";
  }

  @Override
  public String description() {
    return "Maximize expected revenue using demand forecast with golden-section or grid search over price multiplier.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    var fareClasses = ctx.getFareClasses();
    var inventory = ctx.getInventory();
    var forecast = ctx.getDemandForecast();
    var priceUpdates = new ArrayList<PriceUpdate>();

    boolean analyticsAvailable = forecast.isPresent();
    String modelSource = analyticsAvailable ? "trained" : "heuristic";

    for (var flightId : ctx.flightIds()) {
      var fares = fareClasses.get(flightId);
      var inv = inventory.get(flightId);
      if (fares == null || inv == null) continue;

      // Get demand score
      double demandScore = forecast
          .flatMap(f -> Optional.ofNullable(f.get(flightId)))
          .orElse(0.5);

      // Price multiplier search: find revenue-maximizing multiplier
      double bestMultiplier = 1.0;
      double bestRevenue = 0;

      for (double multiplier = 0.8; multiplier <= 1.5; multiplier += 0.1) {
        double expectedDemand = inv.getSeatsTotal() * demandScore;
        double expectedSeatsToSell = Math.min(expectedDemand, inv.getSeatsTotal());

        // Demand elasticity: higher price → lower demand
        double elasticity = 0.8; // Simplified: 20% price increase → 20% demand decrease
        expectedSeatsToSell *= Math.pow(multiplier, -elasticity);

        // Revenue = price * seats sold
        double revenue = inv.getSeatsTotal() * multiplier * expectedSeatsToSell / inv.getSeatsTotal();
        if (revenue > bestRevenue) {
          bestRevenue = revenue;
          bestMultiplier = multiplier;
        }
      }

      // Apply best multiplier to all fares
      for (var fare : fares) {
        BigDecimal newPrice = fare.getBasePrice()
            .multiply(BigDecimal.valueOf(bestMultiplier))
            .setScale(2, RoundingMode.HALF_UP);

        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
          newPrice = BigDecimal.ONE;
        }

        if (!fare.getCurrentPrice().equals(newPrice)) {
          priceUpdates.add(new PriceUpdate(
              flightId,
              fare.getFlight().getFlightNo(),
              fare.getCode(),
              fare.getCurrentPrice(),
              newPrice
          ));
        }
      }
    }

    Map<String, Object> metrics = Map.ofEntries(
        Map.entry("faresOptimized", (Object) priceUpdates.size()),
        Map.entry("modelSource", (Object) modelSource),
        Map.entry("searchMethod", (Object) "grid_search")
    );

    return AlgorithmResult.success(0L, BigDecimal.ZERO, priceUpdates, ctx.flightIds().size(), metrics);
  }
}
