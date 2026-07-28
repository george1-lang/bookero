package com.bookero.flight;

import com.bookero.airport.AirportEntity;
import com.bookero.airport.AirportRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flight search service tests: batch loading, unknown codes, DTO shape.
 */
@SpringBootTest
@Transactional
class FlightSearchServiceTest {

    @Autowired
    private FlightSearchService flightSearchService;

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

    private String searchDate;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        fareClassRepository.deleteAll();
        flightRepository.deleteAll();
        routeRepository.deleteAll();
        airportRepository.deleteAll();

        AirportEntity origin = new AirportEntity("ACC", "Accra", "Accra", "Ghana", 5.6, -0.2);
        AirportEntity dest = new AirportEntity("LAD", "Luanda", "Luanda", "Angola", -8.8, 13.2);
        origin = airportRepository.save(origin);
        dest = airportRepository.save(dest);

        RouteEntity route = new RouteEntity(UUID.randomUUID(), origin, dest, 2000);
        route = routeRepository.save(route);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        searchDate = tomorrow.toString();
        Instant departAt = tomorrow.atStartOfDay(ZoneId.of("UTC")).toInstant().plusSeconds(36000);

        FlightEntity flight = new FlightEntity(UUID.randomUUID(), route, "BK001", departAt);
        flight = flightRepository.save(flight);

        FareClassEntity y = new FareClassEntity(UUID.randomUUID(), flight, "Y", BigDecimal.valueOf(100), BigDecimal.valueOf(100), 30);
        FareClassEntity b = new FareClassEntity(UUID.randomUUID(), flight, "B", BigDecimal.valueOf(150), BigDecimal.valueOf(150), 30);
        y = fareClassRepository.save(y);
        b = fareClassRepository.save(b);

        InventoryEntity inventory = new InventoryEntity(flight.getId(), flight, 120, 100);
        inventory = inventoryRepository.save(inventory);
    }

    @Test
    void searchFindsFlightByOriginDestAndDate() {
        List<FlightSearchResultDto> results = flightSearchService.search("ACC", "LAD", searchDate);

        assertFalse(results.isEmpty(), "Should find at least one flight");
        FlightSearchResultDto flight = results.get(0);

        assertEquals("BK001", flight.flightNo());
        assertEquals("ACC", flight.origin().code());
        assertEquals("LAD", flight.dest().code());
        assertNotNull(flight.departAt());
        assertEquals(120, flight.seatsTotal());
        assertEquals(100, flight.seatsLeft());

        assertFalse(flight.fareClasses().isEmpty(), "Should have fare classes");
        assertTrue(flight.fareClasses().stream().anyMatch(f -> f.code().equals("Y")));
        assertTrue(flight.fareClasses().stream().anyMatch(f -> f.code().equals("B")));
    }

    @Test
    void searchReturnsEmptyListForUnknownOrigin() {
        List<FlightSearchResultDto> results = flightSearchService.search("XXX", "LAD", searchDate);
        assertTrue(results.isEmpty(), "Should return empty list for unknown origin");
    }

    @Test
    void searchReturnsEmptyListForUnknownDestination() {
        List<FlightSearchResultDto> results = flightSearchService.search("ACC", "YYY", searchDate);
        assertTrue(results.isEmpty(), "Should return empty list for unknown destination");
    }

    @Test
    void getByIdReturnsFlightWithFareClasses() {
        FlightEntity flight = flightRepository.findAll().get(0);

        Optional<FlightSearchResultDto> result = flightSearchService.getById(flight.getId());

        assertTrue(result.isPresent());
        FlightSearchResultDto dto = result.get();

        assertEquals("BK001", dto.flightNo());
        assertFalse(dto.fareClasses().isEmpty(), "Should have fare classes");
        assertEquals(2, dto.fareClasses().size());

        BigDecimal lowestFare = dto.lowestFare();
        assertTrue(lowestFare.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getByIdReturnsEmptyForNonexistentFlight() {
        Optional<FlightSearchResultDto> result = flightSearchService.getById(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }
}
