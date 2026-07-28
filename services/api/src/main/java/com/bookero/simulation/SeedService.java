package com.bookero.simulation;

import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import com.bookero.route.RouteEntity;
import com.bookero.route.RouteRepository;
import com.bookero.airport.AirportRepository;
import com.bookero.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Builds the demo hub-and-spoke network on top of the OpenFlights reference data.
 * Idempotent per route: a route that already carries flights is left untouched, so
 * the endpoint can be re-run during a demo without inflating inventory.
 */
@Service
public class SeedService {

  private static final String PREFERRED_HUB = "ACC";
  private static final int MAX_SPOKES = 10;
  private static final int SCHEDULE_DAYS = 14;
  private static final int MIN_SEATS = 120;
  private static final int SEAT_SPREAD = 61;

  /** Fare ladder: cheapest cabin first. Multipliers are applied to the distance-based base fare. */
  private static final List<FareClassSpec> FARE_LADDER = List.of(
      new FareClassSpec("Y", 1.00, 0.55),
      new FareClassSpec("B", 1.30, 0.20),
      new FareClassSpec("M", 1.60, 0.15),
      new FareClassSpec("J", 2.20, 0.10));

  private final AirportRepository airportRepository;
  private final RouteRepository routeRepository;
  private final FlightRepository flightRepository;
  private final FareClassRepository fareClassRepository;
  private final InventoryRepository inventoryRepository;

  public SeedService(
      AirportRepository airportRepository,
      RouteRepository routeRepository,
      FlightRepository flightRepository,
      FareClassRepository fareClassRepository,
      InventoryRepository inventoryRepository) {
    this.airportRepository = airportRepository;
    this.routeRepository = routeRepository;
    this.flightRepository = flightRepository;
    this.fareClassRepository = fareClassRepository;
    this.inventoryRepository = inventoryRepository;
  }

  @Transactional
  public SeedResponseDto seed() {
    String hub = pickHub();
    List<RouteEntity> legs = pickLegs(hub);
    if (legs.isEmpty()) {
      throw ApiException.badRequest(
          "No routes available from hub " + hub + ". Run the analytics ETL first.");
    }

    Instant now = Instant.now();
    int created = 0;
    for (RouteEntity leg : legs) {
      if (flightRepository.countByRouteId(leg.getId()) > 0) {
        continue;
      }
      created += seedRoute(leg, now);
    }

    long flights = flightRepository.countByDepartAtAfter(now.minus(1, ChronoUnit.DAYS));
    return new SeedResponseDto(
        flights,
        fareClassRepository.count(),
        legs.size(),
        hub,
        created == 0
            ? "Network already seeded; nothing duplicated"
            : "Seeded " + created + " flights on a " + hub + " hub-and-spoke network");
  }

  private String pickHub() {
    if (airportRepository.existsById(PREFERRED_HUB)
        && !routeRepository.findAllByOriginCode(PREFERRED_HUB).isEmpty()) {
      return PREFERRED_HUB;
    }
    return routeRepository.findBusiestOrigin()
        .orElseThrow(() -> ApiException.badRequest(
            "No routes in the database. Run the analytics ETL first."));
  }

  /** Outbound and return legs for the busiest spokes, so the demo network is symmetric. */
  private List<RouteEntity> pickLegs(String hub) {
    List<RouteEntity> legs = new ArrayList<>();
    for (RouteEntity outbound : routeRepository.findAllByOriginCode(hub)) {
      if (legs.size() >= MAX_SPOKES * 2) {
        break;
      }
      legs.add(outbound);
      routeRepository
          .findByOriginCodeAndDestCode(outbound.getDestination().getCode(), hub)
          .ifPresent(legs::add);
    }
    return legs;
  }

  private int seedRoute(RouteEntity route, Instant now) {
    String origin = route.getOrigin().getCode();
    String dest = route.getDestination().getCode();
    int distanceKm = route.getDistanceKm() != null ? route.getDistanceKm() : 1000;

    // Seeded from the route itself: reproducible across runs, distinct per leg.
    Random rand = new Random((origin + dest).hashCode());
    int flightCount = 2 + rand.nextInt(3);

    List<FlightEntity> flights = new ArrayList<>(flightCount);
    List<FareClassEntity> fareClasses = new ArrayList<>(flightCount * FARE_LADDER.size());
    List<InventoryEntity> inventories = new ArrayList<>(flightCount);

    for (int i = 0; i < flightCount; i++) {
      Instant departAt = now
          .plus(1 + rand.nextInt(SCHEDULE_DAYS), ChronoUnit.DAYS)
          .truncatedTo(ChronoUnit.DAYS)
          .plus(5 + rand.nextInt(16), ChronoUnit.HOURS)
          .plus(rand.nextInt(12) * 5L, ChronoUnit.MINUTES);

      FlightEntity flight = new FlightEntity(
          UUID.randomUUID(), route, flightNumber(origin, dest, i), departAt);
      flights.add(flight);

      int seatsTotal = MIN_SEATS + rand.nextInt(SEAT_SPREAD);
      fareClasses.addAll(buildFareLadder(flight, distanceKm, seatsTotal));
      inventories.add(new InventoryEntity(flight.getId(), flight, seatsTotal, seatsTotal));
    }

    flightRepository.saveAll(flights);
    fareClassRepository.saveAll(fareClasses);
    inventoryRepository.saveAll(inventories);
    return flights.size();
  }

  /** Allocations are floored per class with the remainder handed to Y, so they sum to capacity. */
  private List<FareClassEntity> buildFareLadder(FlightEntity flight, int distanceKm, int seatsTotal) {
    List<FareClassEntity> ladder = new ArrayList<>(FARE_LADDER.size());
    int assigned = 0;

    for (int i = 1; i < FARE_LADDER.size(); i++) {
      FareClassSpec spec = FARE_LADDER.get(i);
      int seats = Math.max(1, (int) Math.floor(seatsTotal * spec.share()));
      assigned += seats;
      ladder.add(fareClass(flight, spec, distanceKm, seats));
    }

    ladder.add(0, fareClass(flight, FARE_LADDER.getFirst(), distanceKm, seatsTotal - assigned));
    return ladder;
  }

  private FareClassEntity fareClass(FlightEntity flight, FareClassSpec spec, int distanceKm, int seats) {
    BigDecimal basePrice = basePrice(distanceKm, spec.multiplier());
    return new FareClassEntity(UUID.randomUUID(), flight, spec.code(), basePrice, basePrice, seats);
  }

  private BigDecimal basePrice(int distanceKm, double multiplier) {
    return BigDecimal.valueOf(60 + distanceKm / 8.0)
        .multiply(BigDecimal.valueOf(multiplier))
        .setScale(2, RoundingMode.HALF_UP);
  }

  /** Deterministic per leg so the same route always shows the same flight numbers. */
  private String flightNumber(String origin, String dest, int index) {
    int series = Math.floorMod((origin + dest).hashCode(), 900) + 100;
    return "BK" + (series + index);
  }

  private record FareClassSpec(String code, double multiplier, double share) {
  }
}
