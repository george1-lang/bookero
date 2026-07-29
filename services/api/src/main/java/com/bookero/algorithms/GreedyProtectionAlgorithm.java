package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Greedy nested protection. As a cabin fills, the cheapest buckets are withdrawn
 * first so the remaining seats stay available to later, higher-yielding demand.
 *
 * <p>A bucket is withdrawn by lifting its fare to the next bucket up the ladder -
 * the seat is still sellable, just no longer at a discount. Fares are never set to a
 * sentinel value: a traveller must never be shown a price the airline would not honour.
 */
@Component
public class GreedyProtectionAlgorithm implements Algorithm {

  /** Load factor at which the i-th cheapest bucket closes. */
  private static final double[] CLOSE_AT_LOAD = {0.55, 0.75, 0.90};

  @Override
  public String key() {
    return "greedy_protection";
  }

  @Override
  public String displayName() {
    return "Greedy Protection (Close Cheap First)";
  }

  @Override
  public String family() {
    return "Greedy";
  }

  @Override
  public String description() {
    return "Withdraws discount buckets in ascending fare order as the cabin fills, lifting "
        + "each closed bucket to the next fare up so late demand cannot buy cheap seats.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    List<PriceUpdate> updates = new ArrayList<>();
    Map<String, Integer> closuresByClass = new LinkedHashMap<>();
    var fareClasses = ctx.getFareClasses();
    int flightsTouched = 0;

    for (var flightId : ctx.flightIds()) {
      List<FareClassEntity> ladder = Fares.byBaseFareAscending(fareClasses.getOrDefault(flightId, List.of()));
      if (ladder.size() < 2) {
        continue;
      }

      double loadFactor = ctx.loadFactor(flightId);
      int closed = 0;
      for (int i = 0; i < ladder.size() - 1 && i < CLOSE_AT_LOAD.length; i++) {
        if (loadFactor >= CLOSE_AT_LOAD[i]) {
          closed++;
        }
      }

      boolean moved = false;
      for (int i = 0; i < ladder.size(); i++) {
        FareClassEntity fare = ladder.get(i);
        BigDecimal target = i < closed
            // Withdrawn: priced at the next bucket up, so the discount disappears.
            ? ladder.get(i + 1).getBasePrice()
            : fare.getBasePrice();

        if (Fares.moved(fare, target)) {
          updates.add(Fares.update(fare, target));
          moved = true;
        }
      }

      if (closed > 0) {
        closuresByClass.merge(ladder.get(0).getCode(), closed, Integer::sum);
      }
      if (moved) {
        flightsTouched++;
      }
    }

    return AlgorithmResult.success(
        0L,
        BigDecimal.ZERO,
        updates,
        flightsTouched,
        Map.of(
            "bucketsWithdrawn", updates.size(),
            "closureThresholds", CLOSE_AT_LOAD,
            "closuresByLowestClass", closuresByClass));
  }
}
