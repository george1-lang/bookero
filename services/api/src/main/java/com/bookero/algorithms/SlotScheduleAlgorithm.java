package com.bookero.algorithms;

import com.bookero.flight.FlightEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Gate assignment by interval scheduling.
 *
 * <p>Each departure occupies a stand from {@code departAt - PREP} until
 * {@code departAt + TURNAROUND}. Sorting those intervals by finishing time and giving
 * each one the stand that frees earliest is the classic greedy schedule: it is optimal
 * in the number of stands used, because at the moment a new stand is opened every
 * existing stand is still occupied, so that many overlapping intervals genuinely exist.
 *
 * <p>Reports stands used, how many potential clashes the ordering avoided, and the mean
 * occupancy of each stand across the scheduling horizon. Fares are untouched.
 */
@Component
public class SlotScheduleAlgorithm implements Algorithm {

  private static final Duration PREP = Duration.ofMinutes(45);
  private static final Duration TURNAROUND = Duration.ofMinutes(45);

  @Override
  public String key() {
    return "slot_schedule";
  }

  @Override
  public String displayName() {
    return "Slot Scheduling (Interval Scheduling)";
  }

  @Override
  public String family() {
    return "Scheduling";
  }

  @Override
  public String description() {
    return "Assigns every seeded departure to a stand using earliest-finishing-time greedy "
        + "interval scheduling, honouring a minimum turnaround between occupancies.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    List<FlightEntity> flights = ctx.flightIds().stream()
        .map(id -> ctx.getFlights().get(id))
        .filter(java.util.Objects::nonNull)
        .toList();

    if (flights.isEmpty()) {
      return AlgorithmResult.success(0L, BigDecimal.ZERO, List.of(), 0,
          Map.of("standsUsed", 0, "flightsScheduled", 0));
    }

    record Occupancy(String flightNo, Instant start, Instant end) {
    }

    List<Occupancy> intervals = flights.stream()
        .map(f -> new Occupancy(
            f.getFlightNo(), f.getDepartAt().minus(PREP), f.getDepartAt().plus(TURNAROUND)))
        .sorted(Comparator.comparing(Occupancy::end).thenComparing(Occupancy::start))
        .toList();

    // Min-heap over the time each stand next becomes free.
    PriorityQueue<Instant> freeAt = new PriorityQueue<>();
    List<Long> busyMillisPerStand = new ArrayList<>();
    Map<String, Integer> standByFlight = new LinkedHashMap<>();
    Map<Instant, Integer> standIdByFreeTime = new LinkedHashMap<>();

    int standsUsed = 0;
    int clashesAvoided = 0;
    Instant horizonStart = intervals.getFirst().start();
    Instant horizonEnd = horizonStart;

    for (Occupancy slot : intervals) {
      Instant earliest = freeAt.peek();
      int stand;
      if (earliest != null && !earliest.isAfter(slot.start())) {
        freeAt.poll();
        stand = standIdByFreeTime.remove(earliest);
      } else {
        // Every stand is still occupied at this moment, so one more is genuinely needed.
        if (earliest != null) {
          clashesAvoided++;
        }
        stand = standsUsed++;
        busyMillisPerStand.add(0L);
      }

      busyMillisPerStand.set(stand,
          busyMillisPerStand.get(stand) + Duration.between(slot.start(), slot.end()).toMillis());
      freeAt.add(slot.end());
      standIdByFreeTime.put(slot.end(), stand);
      standByFlight.put(slot.flightNo(), stand);
      if (slot.end().isAfter(horizonEnd)) {
        horizonEnd = slot.end();
      }
    }

    long horizonMillis = Math.max(1, Duration.between(horizonStart, horizonEnd).toMillis());
    double meanUtilisation = busyMillisPerStand.stream()
        .mapToDouble(busy -> (double) busy / horizonMillis)
        .average()
        .orElse(0.0);

    return AlgorithmResult.success(
        0L,
        BigDecimal.ZERO,
        List.of(),
        flights.size(),
        Map.of(
            "standsUsed", standsUsed,
            "flightsScheduled", flights.size(),
            "clashesAvoided", clashesAvoided,
            "meanStandUtilisation", String.format("%.2f%%", meanUtilisation * 100),
            "prepMinutes", PREP.toMinutes(),
            "turnaroundMinutes", TURNAROUND.toMinutes(),
            "horizonHours", Duration.between(horizonStart, horizonEnd).toHours()));
  }
}
