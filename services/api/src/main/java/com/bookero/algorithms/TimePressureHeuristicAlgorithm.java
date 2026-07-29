package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Closed-form markup from time pressure and cabin fill:
 *
 * <pre>
 *   multiplier = 1 + A*(1 - d/D)^P + B*loadFactor^Q
 * </pre>
 *
 * where {@code d} is days to departure clamped to the booking window {@code D}. The
 * first term captures the classic late-booking business traveller; the second raises
 * fares as scarcity grows. The result is clamped to the fare band in {@link Fares}.
 *
 * <p>Cheap to evaluate and stateless, which is why it is also the post-booking reprice.
 */
@Component
public class TimePressureHeuristicAlgorithm implements Algorithm {

  private static final double A = 0.35;
  private static final double P = 2.0;
  private static final double B = 0.25;
  private static final double Q = 1.5;
  private static final double BOOKING_WINDOW_DAYS = 30.0;

  @Override
  public String key() {
    return "time_pressure_heuristic";
  }

  @Override
  public String displayName() {
    return "Time Pressure Heuristic";
  }

  @Override
  public String family() {
    return "Heuristic";
  }

  @Override
  public String description() {
    return "Marks fares up from days-to-departure and load factor using a closed-form "
        + "multiplier, cheap enough to run on every booking.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    List<PriceUpdate> updates = new ArrayList<>();
    var fareClasses = ctx.getFareClasses();
    double multiplierSum = 0;
    int flightsPriced = 0;

    for (var flightId : ctx.flightIds()) {
      List<FareClassEntity> fares = fareClasses.getOrDefault(flightId, List.of());
      if (fares.isEmpty()) {
        continue;
      }

      double daysOut = Math.min(ctx.daysToDeparture(flightId), BOOKING_WINDOW_DAYS);
      double urgency = 1.0 - daysOut / BOOKING_WINDOW_DAYS;
      double multiplier = 1.0
          + A * Math.pow(urgency, P)
          + B * Math.pow(ctx.loadFactor(flightId), Q);

      multiplierSum += multiplier;
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
            "formula", "1 + %.2f*(1 - d/%.0f)^%.0f + %.2f*loadFactor^%.1f".formatted(A, BOOKING_WINDOW_DAYS, P, B, Q),
            "avgMultiplier", flightsPriced == 0 ? 0.0 : multiplierSum / flightsPriced,
            "faresMoved", updates.size()));
  }
}
