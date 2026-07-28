package com.bookero.simulation;

import com.bookero.airport.AirportEntity;
import com.bookero.airport.AirportRepository;
import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import com.bookero.route.RouteEntity;
import com.bookero.route.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seed service test: idempotency, seat allocation sums, fare class consistency.
 */
@SpringBootTest
@Transactional
class SeedServiceTest {

    @Autowired
    private SeedService seedService;

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

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        fareClassRepository.deleteAll();
        flightRepository.deleteAll();
        routeRepository.deleteAll();
        airportRepository.deleteAll();

        AirportEntity acc = new AirportEntity("ACC", "Accra", "Accra", "Ghana", 5.6, -0.2);
        AirportEntity lad = new AirportEntity("LAD", "Luanda", "Luanda", "Angola", -8.8, 13.2);
        AirportEntity dca = new AirportEntity("DCA", "Washington DC", "Washington", "USA", 38.9, -77.0);
        acc = airportRepository.save(acc);
        lad = airportRepository.save(lad);
        dca = airportRepository.save(dca);

        RouteEntity route1 = new RouteEntity(UUID.randomUUID(), acc, lad, 2000);
        RouteEntity route2 = new RouteEntity(UUID.randomUUID(), acc, dca, 6000);
        RouteEntity route3 = new RouteEntity(UUID.randomUUID(), lad, acc, 2000);
        route1 = routeRepository.save(route1);
        route2 = routeRepository.save(route2);
        route3 = routeRepository.save(route3);
    }

    @Test
    void seedCreatesFlights() {
        SeedResponseDto response = seedService.seed();

        assertNotNull(response);
        assertTrue(response.flights() > 0, "Should create flights");
        assertTrue(response.fareClasses() > 0, "Should create fare classes");
    }

    @Test
    void seedIsIdempotent() {
        SeedResponseDto first = seedService.seed();
        long firstFlightCount = first.flights();

        SeedResponseDto second = seedService.seed();
        long secondFlightCount = second.flights();

        assertEquals(firstFlightCount, secondFlightCount,
            "Calling seed twice should not duplicate flights");
    }

    @Test
    void seededFlightsHaveFourFareClasses() {
        seedService.seed();

        List<FlightEntity> flights = flightRepository.findAll();
        assertTrue(flights.size() > 0, "Should have seeded flights");

        for (FlightEntity flight : flights) {
            List<FareClassEntity> fareClasses = fareClassRepository.findAllByFlightId(flight.getId());
            assertEquals(4, fareClasses.size(), "Each flight should have 4 fare classes");

            Set<String> codes = new HashSet<>();
            for (FareClassEntity fc : fareClasses) {
                codes.add(fc.getCode());
            }

            assertTrue(codes.contains("Y"), "Should have Y class");
            assertTrue(codes.contains("B"), "Should have B class");
            assertTrue(codes.contains("M"), "Should have M class");
            assertTrue(codes.contains("J"), "Should have J class");
        }
    }

    @Test
    void seatsAllocatedSumsToSeatsTotal() {
        seedService.seed();

        List<InventoryEntity> inventories = inventoryRepository.findAll();
        assertTrue(inventories.size() > 0, "Should have inventory");

        for (InventoryEntity inventory : inventories) {
            List<FareClassEntity> fareClasses = fareClassRepository.findAllByFlightId(inventory.getFlightId());

            int allocatedTotal = fareClasses.stream()
                .mapToInt(FareClassEntity::getSeatsAllocated)
                .sum();

            assertEquals(inventory.getSeatsTotal(), allocatedTotal,
                "Fare class seat allocation should sum to total seats");
        }
    }

    @Test
    void currentPriceEqualsBasePriceAtSeed() {
        seedService.seed();

        List<FareClassEntity> fareClasses = fareClassRepository.findAll();
        assertTrue(fareClasses.size() > 0, "Should have fare classes");

        for (FareClassEntity fc : fareClasses) {
            assertEquals(fc.getBasePrice(), fc.getCurrentPrice(),
                "Current price should equal base price at seed time");
        }
    }

    @Test
    void basePriceIsPositive() {
        seedService.seed();

        List<FareClassEntity> fareClasses = fareClassRepository.findAll();

        for (FareClassEntity fc : fareClasses) {
            assertTrue(fc.getBasePrice().compareTo(BigDecimal.ZERO) > 0,
                "Base price should be positive");
            assertTrue(fc.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0,
                "Current price should be positive");
        }
    }
}
