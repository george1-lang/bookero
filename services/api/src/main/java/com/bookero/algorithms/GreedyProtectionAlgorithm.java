package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.inventory.InventoryEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class GreedyProtectionAlgorithm implements Algorithm {

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
    return "Progressive protection: as load factor rises, close cheaper fare classes first.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    var fareClasses = ctx.getFareClasses();
    var inventory = ctx.getInventory();
    var priceUpdates = new ArrayList<PriceUpdate>();

    for (var flightId : ctx.flightIds()) {
      var fares = fareClasses.get(flightId);
      var inv = inventory.get(flightId);
      if (fares == null || inv == null) continue;

      double loadFactor = (double) (inv.getSeatsTotal() - inv.getSeatsLeft()) / inv.getSeatsTotal();

      // Sort by price ascending (cheap first)
      var sortedFares = fares.stream()
          .sorted(Comparator.comparing(FareClassEntity::getCurrentPrice))
          .toList();

      // Determine thresholds to close (heuristic: close cheapest as load rises)
      int classesToClose = 0;
      if (loadFactor > 0.9) {
        classesToClose = Math.min(1, sortedFares.size() - 1);
      } else if (loadFactor > 0.75) {
        classesToClose = 0;
      }

      for (int i = 0; i < classesToClose && i < sortedFares.size(); i++) {
        var fare = sortedFares.get(i);
        // Set to very high price to effectively close it (but not zero)
        var newPrice = BigDecimal.valueOf(99999.99);

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

    Map<String, Object> metrics = Map.of("faresProtected", (Object) priceUpdates.size());
    return AlgorithmResult.success(0L, BigDecimal.ZERO, priceUpdates, ctx.flightIds().size(), metrics);
  }
}
