package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

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
    return "Reset all in-scope fares to base price. Serves as the control group.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    var fareClasses = ctx.getFareClasses();
    var priceUpdates = new ArrayList<PriceUpdate>();

    for (var flightId : ctx.flightIds()) {
      var fares = fareClasses.get(flightId);
      if (fares != null) {
        for (var fare : fares) {
          if (!fare.getBasePrice().equals(fare.getCurrentPrice())) {
            priceUpdates.add(new PriceUpdate(
                flightId,
                fare.getFlight().getFlightNo(),
                fare.getCode(),
                fare.getCurrentPrice(),
                fare.getBasePrice()
            ));
          }
        }
      }
    }

    Map<String, Object> metrics = Map.of(
        "faresReset", (Object) priceUpdates.size()
    );

    return AlgorithmResult.success(
        0L,
        BigDecimal.ZERO,
        priceUpdates,
        ctx.flightIds().size(),
        metrics
    );
  }
}
