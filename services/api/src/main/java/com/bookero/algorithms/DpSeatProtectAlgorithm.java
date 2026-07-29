package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import com.bookero.inventory.InventoryEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-class seat protection by dynamic programming.
 *
 * <p>For each flight we solve, over states {@code (seats remaining, fare class index)},
 * the expected-revenue recurrence
 *
 * <pre>
 *   V(s, i) = max over a in [0 .. s] of
 *               fare_i * E[min(D_i, a)]  +  V(s - E[min(D_i, a)], i + 1)
 * </pre>
 *
 * evaluated from the dearest class down, where {@code D_i} is the demand for class
 * {@code i}. The optimal split of the cabin gives a booking limit per class, and the
 * marginal value of the last protected seat - the bid price - sets the fare: classes
 * whose value sits below the bid price are marked up, classes above it are discounted
 * to stimulate sales.
 */
@Component
public class DpSeatProtectAlgorithm implements Algorithm {

  /** Demand for a class is modelled as a share of cabin demand, dearest class smallest. */
  private static final double[] CLASS_DEMAND_SHARE = {0.50, 0.25, 0.15, 0.10};

  @Override
  public String key() {
    return "dp_seat_protect";
  }

  @Override
  public String displayName() {
    return "DP Seat Protection (Bid Price)";
  }

  @Override
  public String family() {
    return "Dynamic programming";
  }

  @Override
  public String description() {
    return "Solves the multi-class seat allocation recurrence over (seats, class) states to "
        + "derive booking limits and a bid price, then prices each bucket against that bid price.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    List<PriceUpdate> updates = new ArrayList<>();
    Map<String, Object> protectionLevels = new LinkedHashMap<>();
    long states = 0;
    long tableCells = 0;
    int flightsSolved = 0;

    var fareClasses = ctx.getFareClasses();
    var inventory = ctx.getInventory();

    for (var flightId : ctx.flightIds()) {
      List<FareClassEntity> ladder = Fares.byBaseFareDescending(fareClasses.getOrDefault(flightId, List.of()));
      InventoryEntity inv = inventory.get(flightId);
      if (ladder.isEmpty() || inv == null || inv.getSeatsTotal() == null || inv.getSeatsTotal() <= 0) {
        continue;
      }

      int capacity = inv.getSeatsTotal();
      int classes = ladder.size();
      double cabinDemand = ctx.demandFor(flightId) * capacity;

      double[] fare = new double[classes];
      int[] demand = new int[classes];
      for (int i = 0; i < classes; i++) {
        fare[i] = ladder.get(i).getBasePrice().doubleValue();
        // The ladder runs dearest-first while the shares run cheapest-first, and the
        // cheapest bucket always draws the most demand.
        int shareIndex = Math.min(classes - 1 - i, CLASS_DEMAND_SHARE.length - 1);
        demand[i] = (int) Math.clamp(Math.round(cabinDemand * CLASS_DEMAND_SHARE[shareIndex]), 0, capacity);
      }

      // value[i][s] = best expected revenue from classes i.. with s seats left.
      double[][] value = new double[classes + 1][capacity + 1];
      int[][] take = new int[classes + 1][capacity + 1];
      states += (long) (classes + 1) * (capacity + 1);
      tableCells += (long) (classes + 1) * (capacity + 1) * 2;

      for (int i = classes - 1; i >= 0; i--) {
        for (int s = 0; s <= capacity; s++) {
          double best = value[i + 1][s];
          int bestTake = 0;
          int maxSell = Math.min(s, demand[i]);
          for (int a = 1; a <= maxSell; a++) {
            double candidate = fare[i] * a + value[i + 1][s - a];
            if (candidate > best) {
              best = candidate;
              bestTake = a;
            }
          }
          value[i][s] = best;
          take[i][s] = bestTake;
        }
      }

      // Bid price: marginal value of the last seat in the cabin.
      double bidPrice = value[0][capacity] - value[0][Math.max(0, capacity - 1)];

      int remaining = capacity;
      Map<String, Integer> limits = new LinkedHashMap<>();
      for (int i = 0; i < classes; i++) {
        int limit = take[i][remaining];
        remaining -= limit;
        limits.put(ladder.get(i).getCode(), limit);
      }
      // Any unallocated seats fall to the cheapest bucket so the ladder sums to capacity.
      if (remaining > 0) {
        String cheapest = ladder.get(classes - 1).getCode();
        limits.merge(cheapest, remaining, Integer::sum);
      }

      for (FareClassEntity fc : ladder) {
        double base = fc.getBasePrice().doubleValue();
        // A bucket worth less than the marginal seat is under-priced and must be lifted.
        double multiplier = base <= 0 ? 1.0 : Math.clamp(bidPrice / base, 0.9, 1.6);
        BigDecimal target = Fares.repriceFromBase(fc, multiplier);
        Integer limit = limits.get(fc.getCode());

        boolean priceMoved = Fares.moved(fc, target);
        boolean limitMoved = limit != null && !limit.equals(fc.getSeatsAllocated());
        if (priceMoved || limitMoved) {
          updates.add(Fares.update(fc, target, limit));
        }
      }

      protectionLevels.put(flightNo(ladder), limits);
      flightsSolved++;
    }

    return AlgorithmResult.success(
        0L,
        BigDecimal.ZERO,
        updates,
        flightsSolved,
        Map.of(
            "states", states,
            "tableSize", tableCells,
            "flightsSolved", flightsSolved,
            "protectionLevels", protectionLevels));
  }

  private String flightNo(List<FareClassEntity> ladder) {
    return ladder.getFirst().getFlight().getFlightNo();
  }
}
