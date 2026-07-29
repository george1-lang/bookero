package com.bookero.algorithms;

import com.bookero.analytics.AnalyticsClient;
import com.bookero.flight.FareClassEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies the demand forecast produced by the Python gradient-boosting pipeline
 * (see {@code docs/algorithms/demand_ml.md}) directly to fares: a flight the model
 * expects to fill is marked up, one it expects to go out empty is discounted.
 *
 * <pre>
 *   multiplier = 1 + SENSITIVITY * (forecast - NEUTRAL_DEMAND)
 * </pre>
 *
 * When the analytics service is unreachable the algorithm degrades to the stored
 * demand snapshots and reports that in its message rather than failing the run.
 */
@Component
public class DemandMlAlgorithm implements Algorithm {

  /** Forecast at which a flight is priced at exactly its base fare. */
  private static final double NEUTRAL_DEMAND = 0.5;
  private static final double SENSITIVITY = 0.8;

  private final AnalyticsClient analyticsClient;

  public DemandMlAlgorithm(AnalyticsClient analyticsClient) {
    this.analyticsClient = analyticsClient;
  }

  @Override
  public String key() {
    return "demand_ml";
  }

  @Override
  public String displayName() {
    return "Demand ML (Gradient Boosting)";
  }

  @Override
  public String family() {
    return "Machine learning";
  }

  @Override
  public String description() {
    return "Prices each flight off the demand score predicted by the Python gradient-boosting "
        + "pipeline, falling back to observed demand snapshots when analytics is offline.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    Optional<Map<UUID, Double>> forecast = analyticsClient.demandForecast();
    boolean trained = forecast.isPresent();

    List<PriceUpdate> updates = new ArrayList<>();
    var fareClasses = ctx.getFareClasses();
    int flightsPriced = 0;
    double demandSum = 0;

    for (var flightId : ctx.flightIds()) {
      List<FareClassEntity> fares = fareClasses.getOrDefault(flightId, List.of());
      if (fares.isEmpty()) {
        continue;
      }

      Double predicted = forecast.map(f -> f.get(flightId)).orElse(null);
      double demand = predicted != null ? Math.clamp(predicted, 0.0, 1.0) : ctx.demandFor(flightId);
      double multiplier = 1.0 + SENSITIVITY * (demand - NEUTRAL_DEMAND);

      demandSum += demand;
      flightsPriced++;

      for (FareClassEntity fare : fares) {
        BigDecimal target = Fares.repriceFromBase(fare, multiplier);
        if (Fares.moved(fare, target)) {
          updates.add(Fares.update(fare, target));
        }
      }
    }

    return AlgorithmResult.success(
            0L,
            BigDecimal.ZERO,
            updates,
            flightsPriced,
            Map.of(
                "flightsForecast", flightsPriced,
                "modelSource", trained ? "trained" : "heuristic",
                "avgDemandScore", flightsPriced == 0 ? 0.0 : demandSum / flightsPriced,
                "faresMoved", updates.size()))
        .withMessage(trained
            ? null
            : "Analytics service unavailable; priced from stored demand snapshots.");
  }
}
