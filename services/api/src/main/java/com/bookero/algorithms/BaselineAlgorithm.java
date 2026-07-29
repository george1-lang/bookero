package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Experimental control: restores every in-scope fare to its published base price.
 * Every other algorithm is measured against the revenue this produces.
 */
@Component
public class BaselineAlgorithm implements Algorithm {

  @Override
  public String key() {
    return "baseline";
  }

  @Override
  public String displayName() {
    return "Baseline (Control)";
  }

  @Override
  public String family() {
    return "Control";
  }

  @Override
  public String description() {
    return "Static pricing. Restores every fare to its published base price and holds it "
        + "there, providing the control against which every dynamic strategy is measured.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    List<PriceUpdate> updates = new ArrayList<>();
    var fareClasses = ctx.getFareClasses();

    for (var flightId : ctx.flightIds()) {
      for (FareClassEntity fare : fareClasses.getOrDefault(flightId, List.of())) {
        BigDecimal base = fare.getBasePrice();
        if (Fares.moved(fare, base)) {
          updates.add(Fares.update(fare, base));
        }
      }
    }

    return AlgorithmResult.success(
        0L,
        BigDecimal.ZERO,
        updates,
        (int) updates.stream().map(PriceUpdate::flightId).distinct().count(),
        Map.of("faresReset", updates.size(), "flightsInScope", ctx.flightIds().size()));
  }
}
