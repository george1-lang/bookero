package com.bookero.simulation;

import com.bookero.auth.UserEntity;
import com.bookero.auth.UserRepository;
import com.bookero.booking.BookingEntity;
import com.bookero.booking.BookingRepository;
import com.bookero.common.ApiException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates the booking pressure the pricing algorithms react to.
 *
 * <p>Each flight is driven toward a target load factor derived from its demand score
 * and the requested intensity, so a single run produces a cabin that looks like a
 * real one rather than a handful of scattered seats. Runs are incremental: calling
 * simulate again pushes load further toward the ceiling without ever exceeding
 * capacity.
 *
 * <p>Seeded from a fixed value so a demo replays identically.
 */
@Service
public class DemandSimulator {

    private static final long RANDOM_SEED = 20260728L;
    private static final double BOOKING_WINDOW_DAYS = 14.0;
    private static final double SALES_WINDOW_DAYS = 21.0;
    private static final double MAX_LOAD = 0.97;
    /** Matches the elasticity the optimisation algorithm assumes; see docs/05-evaluation.md. */
    private static final double DEMAND_ELASTICITY = 1.4;
    private static final String SIMULATED_TRAVELER = "traveler@bookero.local";

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

    @Transactional
    public SimulationResponseDto simulate(int intensity) {
        long startMs = System.currentTimeMillis();
        int clamped = Math.clamp(intensity, 1, 10);
        Random rand = new Random(RANDOM_SEED + clamped);
        Instant now = Instant.now();

        List<FlightEntity> flights = flightRepository.findAllByDepartAtAfter(now);
        if (flights.isEmpty()) {
            return new SimulationResponseDto(0, 0, System.currentTimeMillis() - startMs);
        }

        List<UUID> flightIds = flights.stream().map(FlightEntity::getId).toList();
        Map<UUID, List<FareClassEntity>> faresByFlight = fareClassRepository.findAllByFlightIdIn(flightIds)
            .stream()
            .collect(Collectors.groupingBy(fc -> fc.getFlight().getId()));
        Map<UUID, InventoryEntity> inventoryByFlight = inventoryRepository.findAllById(flightIds)
            .stream()
            .collect(Collectors.toMap(InventoryEntity::getFlightId, i -> i));

        UserEntity traveler = userRepository.findByEmail(SIMULATED_TRAVELER)
            .orElseThrow(() -> ApiException.badRequest(
                "Simulated traveller " + SIMULATED_TRAVELER + " is missing; run the migrations."));

        List<BookingEntity> bookings = new ArrayList<>();
        List<DemandSnapshotEntity> snapshots = new ArrayList<>(flights.size());
        List<InventoryEntity> touched = new ArrayList<>();

        for (FlightEntity flight : flights) {
            List<FareClassEntity> fares = faresByFlight.getOrDefault(flight.getId(), List.of());
            InventoryEntity inventory = inventoryByFlight.get(flight.getId());
            if (fares.isEmpty() || inventory == null || inventory.getSeatsTotal() <= 0) {
                continue;
            }

            double demandScore = demandScore(flight, inventory, now, rand);
            snapshots.add(new DemandSnapshotEntity(UUID.randomUUID(), flight, demandScore, now));

            int seatsTotal = inventory.getSeatsTotal();
            int seatsSold = seatsTotal - inventory.getSeatsLeft();
            double priceResponse = priceResponse(fares);
            int targetSold =
                (int) Math.round(targetLoad(demandScore, clamped) * priceResponse * seatsTotal);
            int seatsToSell = Math.min(targetSold - seatsSold, inventory.getSeatsLeft());
            if (seatsToSell <= 0) {
                continue;
            }

            // Discount buckets sell first; that is precisely the dilution the protection
            // algorithms exist to prevent, so the simulation has to reproduce it.
            List<FareClassEntity> ladder = fares.stream()
                .sorted(Comparator.comparing(FareClassEntity::getBasePrice))
                .toList();

            for (int i = 0; i < seatsToSell; i++) {
                FareClassEntity fare = ladder.get(pickBucket(ladder.size(), rand));
                bookings.add(new BookingEntity(
                    UUID.randomUUID(), traveler, flight, fare, fare.getCurrentPrice(),
                    bookedAt(now, rand)));
            }

            inventory.setSeatsLeft(inventory.getSeatsLeft() - seatsToSell);
            touched.add(inventory);
        }

        bookingRepository.saveAll(bookings);
        inventoryRepository.saveAll(touched);
        demandSnapshotRepository.saveAll(snapshots);

        return new SimulationResponseDto(
            snapshots.size(), bookings.size(), System.currentTimeMillis() - startMs);
    }

    /**
     * Spreads a booking back over the sales window on a curve weighted toward the
     * present, so the revenue-by-day series has a shape instead of a single spike.
     */
    private Instant bookedAt(Instant now, Random rand) {
        double skewed = Math.pow(rand.nextDouble(), 2.2);
        long minutesBack = (long) (skewed * SALES_WINDOW_DAYS * 24 * 60);
        return now.minus(minutesBack, ChronoUnit.MINUTES);
    }

    /**
     * Willingness to pay: demand scales as {@code (currentFare / baseFare)^-E} against
     * the cabin's fare-weighted average. Without this the simulated traveller would buy
     * at any price, and no pricing strategy could be told apart from another.
     */
    private double priceResponse(List<FareClassEntity> fares) {
        double current = 0;
        double base = 0;
        for (FareClassEntity fare : fares) {
            current += fare.getCurrentPrice().doubleValue();
            base += fare.getBasePrice().doubleValue();
        }
        if (base <= 0) {
            return 1.0;
        }
        return Math.clamp(Math.pow(current / base, -DEMAND_ELASTICITY), 0.25, 1.8);
    }

    /** Load the cabin is driven toward: intensity sets the ceiling, demand scales it. */
    private double targetLoad(double demandScore, int intensity) {
        return Math.clamp(0.10 + 0.085 * intensity * (0.5 + demandScore), 0.0, MAX_LOAD);
    }

    /** Geometric preference for the cheapest open bucket. */
    private int pickBucket(int buckets, Random rand) {
        int index = 0;
        while (index < buckets - 1 && rand.nextDouble() > 0.62) {
            index++;
        }
        return index;
    }

    /** Demand in [0,1] from time pressure, how full the cabin already is, and noise. */
    private double demandScore(FlightEntity flight, InventoryEntity inventory, Instant now, Random rand) {
        long daysOut = ChronoUnit.DAYS.between(now, flight.getDepartAt());
        double urgency = Math.clamp(1.0 - daysOut / BOOKING_WINDOW_DAYS, 0.0, 1.0);
        double load = (double) (inventory.getSeatsTotal() - inventory.getSeatsLeft()) / inventory.getSeatsTotal();
        return Math.clamp(urgency * 0.4 + load * 0.35 + rand.nextDouble() * 0.25, 0.0, 1.0);
    }
}
