package com.bookero.booking;

import com.bookero.airport.AirportEntity;
import com.bookero.airport.AirportRepository;
import com.bookero.auth.AuthenticatedUser;
import com.bookero.auth.Role;
import com.bookero.auth.UserEntity;
import com.bookero.auth.UserRepository;
import com.bookero.common.ApiException;
import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import com.bookero.route.RouteEntity;
import com.bookero.route.RouteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oversell protection under contention. Deliberately NOT {@code @Transactional}: the
 * fixture has to be committed before the worker threads — each running its own
 * transaction — can see it, and the pessimistic lock only has meaning across
 * separate transactions.
 */
@SpringBootTest
class BookingConcurrencyIT {

  private static final int CONTENDERS = 8;
  private static final String ORIGIN = "TCA";
  private static final String DEST = "TCB";

  @Autowired private BookingService bookingService;
  @Autowired private FlightRepository flightRepository;
  @Autowired private FareClassRepository fareClassRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private RouteRepository routeRepository;
  @Autowired private AirportRepository airportRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private BookingRepository bookingRepository;

  private UUID flightId;
  private UUID fareClassId;
  private AuthenticatedUser traveler;

  @BeforeEach
  void seedCommittedFixture() {
    cleanUp();

    AirportEntity origin = airportRepository.save(
        new AirportEntity(ORIGIN, "Contention Alpha", "Alpha", "Testland", 0.0, 0.0));
    AirportEntity dest = airportRepository.save(
        new AirportEntity(DEST, "Contention Beta", "Beta", "Testland", 1.0, 1.0));
    RouteEntity route = routeRepository.save(
        new RouteEntity(UUID.randomUUID(), origin, dest, 500));

    FlightEntity flight = flightRepository.save(new FlightEntity(
        UUID.randomUUID(), route, "BK999", Instant.now().plusSeconds(86_400)));
    flightId = flight.getId();

    FareClassEntity fareClass = fareClassRepository.save(new FareClassEntity(
        UUID.randomUUID(), flight, "Y",
        BigDecimal.valueOf(100), BigDecimal.valueOf(100), 1));
    fareClassId = fareClass.getId();

    inventoryRepository.save(new InventoryEntity(flightId, flight, 1, 1));

    UserEntity user = userRepository.save(new UserEntity(
        UUID.randomUUID(), "contention@bookero.local", "x", Role.TRAVELER));
    traveler = new AuthenticatedUser(user.getId(), user.getEmail(), Role.TRAVELER);
  }

  @AfterEach
  void cleanUp() {
    bookingRepository.deleteAll();
    inventoryRepository.deleteAll();
    fareClassRepository.deleteAll();
    flightRepository.deleteAll();
    routeRepository.deleteAll();
    airportRepository.deleteAllById(List.of(ORIGIN, DEST));
    userRepository.findByEmail("contention@bookero.local").ifPresent(userRepository::delete);
  }

  @Test
  void onlyOneOfManyConcurrentBookingsTakesTheLastSeat() throws InterruptedException {
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger booked = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    ConcurrentHashMap<Integer, Throwable> unexpected = new ConcurrentHashMap<>();

    ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
    for (int i = 0; i < CONTENDERS; i++) {
      final int id = i;
      pool.submit(() -> {
        try {
          start.await();
          bookingService.book(flightId, fareClassId, traveler);
          booked.incrementAndGet();
        } catch (ApiException e) {
          if (e.getHttpStatus() == HttpStatus.CONFLICT) {
            rejected.incrementAndGet();
          } else {
            unexpected.put(id, e);
          }
        } catch (Exception e) {
          unexpected.put(id, e);
        }
      });
    }

    start.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers did not finish");

    assertTrue(unexpected.isEmpty(), "unexpected failures: " + unexpected);
    assertEquals(1, booked.get(), "exactly one contender may take the single seat");
    assertEquals(CONTENDERS - 1, rejected.get(), "every other contender must see 409");
    assertEquals(0, inventoryRepository.findById(flightId).orElseThrow().getSeatsLeft(),
        "a sold-out flight rests at zero, never negative");
    assertEquals(1, bookingRepository.countByFlightId(flightId),
        "exactly one booking row may exist for the seat");
  }
}
