package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.inventory.InventoryEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Constrained revenue maximisation per flight.
 *
 * <p>Willingness to pay is modelled with constant price elasticity: raising the fare
 * by a factor {@code m} scales expected demand by {@code m^-E}. Expected revenue is
 *
 * <pre>
 *   R(m) = m * min(seatsLeft, demandAtBase * m^-E)
 * </pre>
 *
 * measured in units of the base fare. It rises linearly while the cabin still sells
 * out and falls as {@code m^(1-E)} once it does not, giving an interior maximum
 * whenever {@code E > 1}. Golden-section search finds it in roughly twenty
 * evaluations rather than the dense grid a linear scan would need.
 */
@Component
public class RevenueOptimizeAlgorithm implements Algorithm {

  /** Leisure air travel is generally estimated as price-elastic. */
  private static final double ELASTICITY = 1.6;
  private static final double INV_PHI = 0.618_033_988_75;
  private static final double TOLERANCE = 1e-4;

  @Override
  public String key() {
    return "revenue_optimize";
  }

  @Override
  public String displayName() {
    return "Revenue Optimisation (Golden Section)";
  }

  @Override
  public String family() {
    return "Optimization";
  }

  @Override
  public String description() {
    return "Maximises expected revenue under capacity against a constant-elasticity demand "
        + "curve driven by the ML forecast, searching the fare band by golden section.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    List<PriceUpdate> updates = new ArrayList<>();
    var fareClasses = ctx.getFareClasses();
    var inventory = ctx.getInventory();
    boolean forecastAvailable = ctx.getDemandForecast().isPresent();

    int evaluations = 0;
    int flightsOptimised = 0;
    double multiplierSum = 0;

    for (var flightId : ctx.flightIds()) {
      List<FareClassEntity> fares = fareClasses.getOrDefault(flightId, List.of());
      InventoryEntity inv = inventory.get(flightId);
      if (fares.isEmpty() || inv == null || inv.getSeatsLeft() == null) {
        continue;
      }

      double capacity = Math.max(1, inv.getSeatsLeft());
      double demandAtBase = ctx.demandFor(flightId) * inv.getSeatsTotal();

      double lo = Fares.MIN_MULTIPLIER;
      double hi = Fares.MAX_MULTIPLIER;
      double x1 = hi - INV_PHI * (hi - lo);
      double x2 = lo + INV_PHI * (hi - lo);
      double f1 = expectedRevenue(x1, demandAtBase, capacity);
      double f2 = expectedRevenue(x2, demandAtBase, capacity);
      evaluations += 2;

      while (hi - lo > TOLERANCE) {
        if (f1 > f2) {
          hi = x2;
          x2 = x1;
          f2 = f1;
          x1 = hi - INV_PHI * (hi - lo);
          f1 = expectedRevenue(x1, demandAtBase, capacity);
        } else {
          lo = x1;
          x1 = x2;
          f1 = f2;
          x2 = lo + INV_PHI * (hi - lo);
          f2 = expectedRevenue(x2, demandAtBase, capacity);
        }
        evaluations++;
      }

      double optimal = (lo + hi) / 2.0;
      multiplierSum += optimal;
      flightsOptimised++;

      for (FareClassEntity fare : fares) {
        BigDecimal target = Fares.repriceFromBase(fare, optimal);
        if (Fares.moved(fare, target)) {
          updates.add(Fares.update(fare, target));
        }
      }
    }

    return AlgorithmResult.success(
            0L,
            BigDecimal.ZERO,
            updates,
            flightsOptimised,
            Map.of(
                "elasticity", ELASTICITY,
                "objectiveEvaluations", evaluations,
                "avgOptimalMultiplier", flightsOptimised == 0 ? 0.0 : multiplierSum / flightsOptimised,
                "demandSource", forecastAvailable ? "ml_forecast" : "demand_snapshot"))
        .withMessage(forecastAvailable
            ? null
            : "Analytics forecast unavailable; optimised against the latest demand snapshots.");
  }

  private double expectedRevenue(double multiplier, double demandAtBase, double capacity) {
    double demand = demandAtBase * Math.pow(multiplier, -ELASTICITY);
    return multiplier * Math.min(capacity, demand);
  }
}
