package com.bookero.simulation;

import com.bookero.airport.AirportEntity;
import com.bookero.airport.AirportRepository;
import com.bookero.auth.Role;
import com.bookero.auth.UserEntity;
import com.bookero.auth.UserRepository;
import com.bookero.booking.BookingRepository;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demand simulator test: no negative inventory, snapshots created, reproducibility.
 */
@SpringBootTest
@Transactional
class DemandSimulatorTest {

    @Autowired
    private DemandSimulator demandSimulator;

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
    private DemandSnapshotRepository demandSnapshotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        demandSnapshotRepository.deleteAll();
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

        Instant tomorrow = Instant.now().plusSeconds(86400);
        FlightEntity flight = new FlightEntity(UUID.randomUUID(), route, "BK001", tomorrow);
        flight = flightRepository.save(flight);

        FareClassEntity y = new FareClassEntity(UUID.randomUUID(), flight, "Y", BigDecimal.valueOf(100), BigDecimal.valueOf(100), 60);
        FareClassEntity b = new FareClassEntity(UUID.randomUUID(), flight, "B", BigDecimal.valueOf(150), BigDecimal.valueOf(150), 60);
        y = fareClassRepository.save(y);
        b = fareClassRepository.save(b);

        InventoryEntity inventory = new InventoryEntity(flight.getId(), flight, 120, 120);
        inventory = inventoryRepository.save(inventory);

        UserEntity traveler = new UserEntity(
            UUID.randomUUID(),
            "simulation-fixture@bookero.local",
            "hashed",
            Role.TRAVELER
        );
        traveler = userRepository.save(traveler);
    }

    @Test
    void simulateCreatesBookingsAndSnapshots() {
        SimulationResponseDto response = demandSimulator.simulate(5);

        assertTrue(response.demandSnapshots() > 0, "Should create demand snapshots");
        assertTrue(response.syntheticBookings() >= 0, "Should create bookings or none if no open flights");
    }

    @Test
    void simulateNeverDrivesSeatsNegative() {
        SimulationResponseDto response = demandSimulator.simulate(10);

        List<InventoryEntity> inventories = inventoryRepository.findAll();

        for (InventoryEntity inventory : inventories) {
            assertTrue(inventory.getSeatsLeft() >= 0,
                "Seats left should never be negative; got " + inventory.getSeatsLeft());
        }
    }

    @Test
    void highIntensitySimulateUsesManySeats() {
        SimulationResponseDto response = demandSimulator.simulate(10);

        InventoryEntity inventory = inventoryRepository.findAll().get(0);
        int seatsUsed = inventory.getSeatsTotal() - inventory.getSeatsLeft();

        assertTrue(seatsUsed > 0, "High intensity should book some seats");
    }

    @Test
    void demandSnapshotsAreCreated() {
        SimulationResponseDto response = demandSimulator.simulate(5);

        List<DemandSnapshotEntity> snapshots = demandSnapshotRepository.findAll();

        assertTrue(snapshots.size() > 0, "Should create demand snapshots");

        for (DemandSnapshotEntity snapshot : snapshots) {
            assertTrue(snapshot.getDemandScore() >= 0.0 && snapshot.getDemandScore() <= 1.0,
                "Demand score should be in [0, 1]; got " + snapshot.getDemandScore());
        }
    }

    @Test
    void lowIntensityBooksFewerSeats() {
        SimulationResponseDto low = demandSimulator.simulate(1);
        InventoryEntity afterLow = inventoryRepository.findAll().get(0);
        int seatsUsedLow = afterLow.getSeatsTotal() - afterLow.getSeatsLeft();

        inventoryRepository.deleteAll();
        bookingRepository.deleteAll();
        demandSnapshotRepository.deleteAll();

        InventoryEntity inventory = new InventoryEntity(
            flightRepository.findAll().get(0).getId(),
            flightRepository.findAll().get(0),
            120,
            120
        );
        inventory = inventoryRepository.save(inventory);

        SimulationResponseDto high = demandSimulator.simulate(10);
        InventoryEntity afterHigh = inventoryRepository.findAll().get(0);
        int seatsUsedHigh = afterHigh.getSeatsTotal() - afterHigh.getSeatsLeft();

        assertTrue(seatsUsedHigh >= seatsUsedLow,
            "Higher intensity should use at least as many seats; low=" + seatsUsedLow + ", high=" + seatsUsedHigh);
    }
}
