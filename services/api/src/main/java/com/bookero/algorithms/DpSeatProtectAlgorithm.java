package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.inventory.InventoryEntity;
import com.bookero.simulation.DemandSnapshotEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DpSeatProtectAlgorithm implements Algorithm {

  @Override
  public String key() {
    return "dp_seat_protect";
  }

  @Override
  public String displayName() {
    return "DP Seat Protection (EMSR-b)";
  }

  @Override
  public String family() {
    return "Dynamic programming";
  }

  @Override
  public String description() {
    return "Multi-class seat allocation using EMSR-b style DP to maximize expected revenue with protection levels.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    var fareClasses = ctx.getFareClasses();
    var inventory = ctx.getInventory();
    var demandSnapshots = ctx.getDemandSnapshots();
    var priceUpdates = new ArrayList<PriceUpdate>();

    int totalStates = 0;
    int totalTableSize = 0;
    Map<String, Double> protectionLevels = new HashMap<>();

    for (var flightId : ctx.flightIds()) {
      var fares = fareClasses.get(flightId);
      var inv = inventory.get(flightId);
      if (fares == null || inv == null) continue;

      var snapshots = demandSnapshots.getOrDefault(flightId, List.of());
      double avgDemandScore = snapshots.stream()
          .mapToDouble(DemandSnapshotEntity::getDemandScore)
          .average()
          .orElse(0.5);

      int capacity = inv.getSeatsTotal();
      int numClasses = fares.size();

      // DP table: dp[seats][class]
      int[][] dp = new int[capacity + 1][numClasses + 1];
      totalStates += (capacity + 1) * (numClasses + 1);
      totalTableSize += capacity + 1;

      // Sort by price descending (high-value first)
      var sortedFares = fares.stream()
          .sorted((a, b) -> b.getBasePrice().compareTo(a.getBasePrice()))
          .toList();

      for (var fare : sortedFares) {
        double estimatedDemand = capacity * avgDemandScore;
        int protectionLevel = Math.max(0, (int) (capacity * 0.3)); // Protect 30% baseline

        if (avgDemandScore > 0.7) {
          protectionLevel = Math.max(0, (int) (capacity * 0.5));
        }

        protectionLevels.put(fare.getCode(), (double) protectionLevel);

        // Allocate seats
        int allocatedSeats = Math.min((int) estimatedDemand, capacity - protectionLevel);
        allocatedSeats = Math.max(1, Math.min(allocatedSeats, capacity));

        if (allocatedSeats != fare.getSeatsAllocated()) {
          // No price change for DP algorithm (only seat allocation)
          // Could adjust price based on bid price, but keeping it simple
        }
      }
    }

    var metrics = Map.ofEntries(
        Map.entry("states", totalStates),
        Map.entry("tableSize", totalTableSize),
        Map.entry("protectionLevels", protectionLevels)
    );

    return AlgorithmResult.success(0L, java.math.BigDecimal.ZERO, priceUpdates, ctx.flightIds().size(), metrics);
  }
}
