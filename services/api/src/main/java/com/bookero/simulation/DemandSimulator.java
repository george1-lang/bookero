package com.bookero.simulation;

import com.bookero.auth.UserEntity;
import com.bookero.auth.UserRepository;
import com.bookero.booking.BookingEntity;
import com.bookero.booking.BookingRepository;
import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Demand simulator: creates synthetic bookings and demand snapshots.
 * Uses seeded Random for reproducibility. Stops at zero seats (never oversells).
 */
@Service
public class DemandSimulator {

    private final FlightRepository flightRepository;
    private final FareClassRepository fareClassRepository;
    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final DemandSnapshotRepository demandSnapshotRepository;
    private final UserRepository userRepository;

    public DemandSimulator(
        FlightRepository flightRepository,
        FareClassRepository fareClassRepository,
        InventoryRepository inventoryRepository,
        BookingRepository bookingRepository,
        DemandSnapshotRepository demandSnapshotRepository,
        UserRepository userRepository
    ) {
        this.flightRepository = flightRepository;
        this.fareClassRepository = fareClassRepository;
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
        this.demandSnapshotRepository = demandSnapshotRepository;
        this.userRepository = userRepository;
    }

    /**
     * Simulate demand on all open flights.
     * Intensity in [1, 10] controls booking pressure.
     * Uses seeded Random(0) for reproducible runs.
     */
    @Transactional
    public SimulationResponseDto simulate(int intensity) {
        long startMs = System.currentTimeMillis();

        int clampedIntensity = Math.max(1, Math.min(10, intensity));
        Random rand = new Random(0);

        List<FlightEntity> openFlights = flightRepository.findAllByDepartAtAfter(Instant.now());

        Map<UUID, List<FareClassEntity>> fareClassesByFlight = new HashMap<>();
        Map<UUID, InventoryEntity> inventoryByFlight = new HashMap<>();

        for (FlightEntity flight : openFlights) {
            fareClassesByFlight.put(flight.getId(), fareClassRepository.findAllByFlightId(flight.getId()));
            inventoryRepository.findById(flight.getId()).ifPresent(inv ->
                inventoryByFlight.put(flight.getId(), inv)
            );
        }

        UserEntity seedUser = userRepository.findByEmail("traveler@bookero.local")
            .orElse(null);

        int totalBookings = 0;

        for (FlightEntity flight : openFlights) {
            List<FareClassEntity> fareClasses = fareClassesByFlight.getOrDefault(flight.getId(), List.of());
            InventoryEntity inventory = inventoryByFlight.get(flight.getId());

            if (fareClasses.isEmpty() || inventory == null) {
                continue;
            }

            double demandScore = computeDemandScore(flight, inventory, rand);
            int bookingsToCreate = (int) (demandScore * inventory.getSeatsTotal() * clampedIntensity / 10.0);

            for (int i = 0; i < bookingsToCreate && inventory.getSeatsLeft() > 0; i++) {
                FareClassEntity fareClass = fareClasses.get(rand.nextInt(fareClasses.size()));

                if (seedUser != null) {
                    BookingEntity booking = new BookingEntity(
                        UUID.randomUUID(),
                        seedUser,
                        flight,
                        fareClass,
                        fareClass.getCurrentPrice(),
                        Instant.now()
                    );

                    bookingRepository.save(booking);
                    inventory.setSeatsLeft(inventory.getSeatsLeft() - 1);
                    totalBookings++;
                }
            }

            if (inventory.getSeatsLeft() >= 0) {
                inventoryRepository.save(inventory);
            }

            DemandSnapshotEntity snapshot = new DemandSnapshotEntity(
                UUID.randomUUID(),
                flight,
                demandScore,
                Instant.now()
            );

            demandSnapshotRepository.save(snapshot);
        }

        long endMs = System.currentTimeMillis();

        return new SimulationResponseDto(
            openFlights.size(),
            totalBookings,
            endMs - startMs
        );
    }

    /**
     * Compute demand score in [0, 1] based on days-to-departure, load factor, and randomness.
     * Seeded Random is passed in for reproducibility.
     */
    private double computeDemandScore(FlightEntity flight, InventoryEntity inventory, Random rand) {
        Instant now = Instant.now();
        long daysToDepart = ChronoUnit.DAYS.between(now, flight.getDepartAt());

        double daysFactor = Math.max(0, 1.0 - (daysToDepart / 14.0));
        double loadFactor = (double) (inventory.getSeatsTotal() - inventory.getSeatsLeft()) / inventory.getSeatsTotal();
        double randomComponent = rand.nextDouble();

        return Math.clamp((daysFactor * 0.4 + loadFactor * 0.4 + randomComponent * 0.2), 0.0, 1.0);
    }
}
