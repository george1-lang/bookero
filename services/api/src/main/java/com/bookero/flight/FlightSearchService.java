package com.bookero.flight;

import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import com.bookero.route.RouteEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flight search service with efficient batch loading to avoid N+1 queries.
 */
@Service
@Transactional(readOnly = true)
public class FlightSearchService {

    private final FlightRepository flightRepository;
    private final FareClassRepository fareClassRepository;
    private final InventoryRepository inventoryRepository;

    public FlightSearchService(
        FlightRepository flightRepository,
        FareClassRepository fareClassRepository,
        InventoryRepository inventoryRepository
    ) {
        this.flightRepository = flightRepository;
        this.fareClassRepository = fareClassRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Search flights by origin, destination, and departure date (UTC day).
     * Returns empty list for unknown airport codes.
     * Batch-loads fare classes and inventory for all matching flights.
     */
    public List<FlightSearchResultDto> search(String originCode, String destCode, String dateStr) {
        LocalDate localDate = LocalDate.parse(dateStr);
        Instant dayStart = localDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant dayEnd = localDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();

        List<FlightEntity> flights = flightRepository.findByOriginDestAndDepartDate(originCode, destCode, dayStart);

        if (flights.isEmpty()) {
            return List.of();
        }

        Set<UUID> flightIds = flights.stream().map(FlightEntity::getId).collect(Collectors.toSet());

        Map<UUID, List<FareClassEntity>> fareClassesByFlight = fareClassRepository.findAllByFlightIdIn(flightIds)
            .stream()
            .collect(Collectors.groupingBy(f -> f.getFlight().getId()));

        Map<UUID, InventoryEntity> inventoryByFlight = inventoryRepository.findAllById(flightIds)
            .stream()
            .collect(Collectors.toMap(InventoryEntity::getFlightId, i -> i));

        return flights.stream()
            .map(f -> toDto(f, fareClassesByFlight.getOrDefault(f.getId(), List.of()),
                inventoryByFlight.get(f.getId())))
            .collect(Collectors.toList());
    }

    /**
     * Get a single flight by ID with all fare classes and inventory.
     * Returns empty Optional if flight not found.
     */
    public Optional<FlightSearchResultDto> getById(UUID flightId) {
        return flightRepository.findById(flightId)
            .flatMap(flight -> {
                List<FareClassEntity> fareClasses = fareClassRepository.findAllByFlightId(flightId);
                Optional<InventoryEntity> inventory = inventoryRepository.findById(flightId);

                if (inventory.isEmpty()) {
                    return Optional.empty();
                }

                return Optional.of(toDto(flight, fareClasses, inventory.get()));
            });
    }

    private FlightSearchResultDto toDto(FlightEntity flight, List<FareClassEntity> fareClasses,
                                        InventoryEntity inventory) {
        RouteEntity route = flight.getRoute();

        AirportDto origin = new AirportDto(
            route.getOrigin().getCode(),
            route.getOrigin().getName(),
            route.getOrigin().getCity()
        );

        AirportDto dest = new AirportDto(
            route.getDestination().getCode(),
            route.getDestination().getName(),
            route.getDestination().getCity()
        );

        List<FareClassDto> fareDtos = fareClasses.stream()
            .map(f -> new FareClassDto(
                f.getId(),
                f.getCode(),
                f.getCurrentPrice(),
                f.getBasePrice(),
                f.getSeatsAllocated()
            ))
            .collect(Collectors.toList());

        BigDecimal lowestFare = fareClasses.stream()
            .map(FareClassEntity::getCurrentPrice)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        return new FlightSearchResultDto(
            flight.getId(),
            flight.getFlightNo(),
            flight.getDepartAt(),
            origin,
            dest,
            route.getDistanceKm(),
            inventory.getSeatsLeft(),
            inventory.getSeatsTotal(),
            lowestFare,
            fareDtos
        );
    }
}
