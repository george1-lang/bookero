package com.bookero.algorithms;

import com.bookero.analytics.AnalyticsClient;
import com.bookero.simulation.DemandSnapshotRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DemandMlAlgorithm implements Algorithm {

  private final AnalyticsClient analyticsClient;
  private final DemandSnapshotRepository demandSnapshotRepository;

  public DemandMlAlgorithm(
      AnalyticsClient analyticsClient,
      DemandSnapshotRepository demandSnapshotRepository
  ) {
    this.analyticsClient = analyticsClient;
    this.demandSnapshotRepository = demandSnapshotRepository;
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
    return "Spring-side wrapper around Python HistGradientBoostingRegressor. Pulls forecast and reports model source.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    var forecast = analyticsClient.demandForecast();

    int flightsForecast = 0;
    String modelSource = "heuristic";

    if (forecast.isPresent()) {
      flightsForecast = forecast.get().size();
      modelSource = "trained";
    } else {
      // Fall back to latest demand snapshots
      var snapshots = demandSnapshotRepository.findLatestPerFlight();
      flightsForecast = snapshots.size();
      modelSource = "heuristic";
    }

    Map<String, Object> metrics = Map.ofEntries(
        Map.entry("flightsForecast", (Object) flightsForecast),
        Map.entry("modelSource", (Object) modelSource)
    );

    String message = modelSource.equals("heuristic")
        ? "Analytics service unavailable; using fallback demand snapshots"
        : null;

    return new AlgorithmResult(
        "SUCCESS",
        0L,
        BigDecimal.ZERO,
        List.of(),
        flightsForecast,
        message,
        metrics
    );
  }
}
