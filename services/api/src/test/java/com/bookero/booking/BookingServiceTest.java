package com.bookero.booking;

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
import com.bookero.airport.AirportEntity;
import com.bookero.airport.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Single-threaded booking rules: capacity, ownership and lookup failures.
 * Contention is covered separately by {@link BookingConcurrencyIT}.
 */
@SpringBootTest
@Transactional
class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private FareClassRepository fareClassRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private UUID flightId;
    private UUID fareClassId;
    private UUID userId;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        inventoryRepository.deleteAll();
        fareClassRepository.deleteAll();
        flightRepository.deleteAll();
        routeRepository.deleteAll();
        airportRepository.deleteAll();
        userRepository.deleteAll();

        AirportEntity originAirport = new AirportEntity("ACC", "Accra", "Accra", "Ghana", 5.6, -0.2);
        AirportEntity destAirport = new AirportEntity("LAD", "Luanda", "Luanda", "Angola", -8.8, 13.2);
        originAirport = airportRepository.save(originAirport);
        destAirport = airportRepository.save(destAirport);

        RouteEntity route = new RouteEntity(UUID.randomUUID(), originAirport, destAirport, 2000);
        route = routeRepository.save(route);

        Instant departAt = Instant.now().plusSeconds(3600);
        FlightEntity flight = new FlightEntity(UUID.randomUUID(), route, "BK001", departAt);
        flight = flightRepository.save(flight);
        flightId = flight.getId();

        FareClassEntity fareClass = new FareClassEntity(
            UUID.randomUUID(),
            flight,
            "Y",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(100),
            1
        );
        fareClass = fareClassRepository.save(fareClass);
        fareClassId = fareClass.getId();

        InventoryEntity inventory = new InventoryEntity(flightId, flight, 1, 1);
        inventory = inventoryRepository.save(inventory);

        UserEntity userEntity = new UserEntity(
            UUID.randomUUID(),
            "traveler@bookero.local",
            "hashed",
            Role.TRAVELER
        );
        userEntity = userRepository.save(userEntity);
        userId = userEntity.getId();
        user = new AuthenticatedUser(userId, "traveler@bookero.local", Role.TRAVELER);
    }

    @Test
    void bookingBeyondCapacityIsRejectedWithConflict() {
        bookingService.book(flightId, fareClassId, user);

        ApiException ex = assertThrows(ApiException.class, () ->
            bookingService.book(flightId, fareClassId, user)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals(0, inventoryRepository.findById(flightId).orElseThrow().getSeatsLeft());
    }

    @Test
    void bookingFailsWhenFlightNotFound() {
        UUID nonexistentFlightId = UUID.randomUUID();

        ApiException ex = assertThrows(ApiException.class, () ->
            bookingService.book(nonexistentFlightId, fareClassId, user)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void bookingFailsWhenFareClassNotFound() {
        UUID nonexistentFareClassId = UUID.randomUUID();

        ApiException ex = assertThrows(ApiException.class, () ->
            bookingService.book(flightId, nonexistentFareClassId, user)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void bookingFailsWhenFareClassDoesNotBelongToFlight() {
        AirportEntity kumasi = airportRepository.save(
            new AirportEntity("KMS", "Kumasi", "Kumasi", "Ghana", 6.71, -1.59));
        AirportEntity tamale = airportRepository.save(
            new AirportEntity("TML", "Tamale", "Tamale", "Ghana", 9.56, -0.86));
        RouteEntity otherRoute = routeRepository.save(
            new RouteEntity(UUID.randomUUID(), kumasi, tamale, 320));

        FlightEntity otherFlight = flightRepository.save(
            new FlightEntity(UUID.randomUUID(), otherRoute, "BK002", Instant.now()));

        FareClassEntity foreignFareClass = fareClassRepository.save(new FareClassEntity(
            UUID.randomUUID(),
            otherFlight,
            "B",
            BigDecimal.valueOf(200),
            BigDecimal.valueOf(200),
            1
        ));

        ApiException ex = assertThrows(ApiException.class, () ->
            bookingService.book(flightId, foreignFareClass.getId(), user)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }
}
