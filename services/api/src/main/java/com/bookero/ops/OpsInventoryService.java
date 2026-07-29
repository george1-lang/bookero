package com.bookero.ops;

import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Operations service for inventory aggregation. Batch-loads to avoid N+1.
 */
@Service
@Transactional(readOnly = true)
public class OpsInventoryService {

    private final FlightRepository flightRepository;
    private final InventoryRepository inventoryRepository;
    private final FareClassRepository fareClassRepository;

    public OpsInventoryService(
        FlightRepository flightRepository,
        InventoryRepository inventoryRepository,
        FareClassRepository fareClassRepository
    ) {
        this.flightRepository = flightRepository;
        this.inventoryRepository = inventoryRepository;
        this.fareClassRepository = fareClassRepository;
    }

    /**
     * Get all inventory states, sorted by departure time.
     * Batch-loads flights, inventory, and fare classes to avoid N+1.
     */
    public List<InventoryFlightDto> getAllInventory() {
        List<InventoryEntity> inventories = inventoryRepository.findAll();

        if (inventories.isEmpty()) {
            return List.of();
        }

        Set<UUID> flightIds = inventories.stream()
            .map(InventoryEntity::getFlightId)
            .collect(Collectors.toSet());

        Map<UUID, FlightEntity> flightMap = flightRepository.findAllById(flightIds)
            .stream()
            .collect(Collectors.toMap(FlightEntity::getId, f -> f));

        Map<UUID, List<FareClassEntity>> fareClassesByFlight = fareClassRepository.findAllByFlightIdIn(flightIds)
            .stream()
            .collect(Collectors.groupingBy(f -> f.getFlight().getId()));

        List<InventoryFlightDto> results = new ArrayList<>();

        for (InventoryEntity inventory : inventories) {
            FlightEntity flight = flightMap.get(inventory.getFlightId());

            if (flight == null) {
                continue;
            }

            List<FareClassEntity> fareClasses = fareClassesByFlight.getOrDefault(flight.getId(), List.of());

            double loadFactor = (double) (inventory.getSeatsTotal() - inventory.getSeatsLeft())
                / inventory.getSeatsTotal();

            InventoryFlightDto dto = new InventoryFlightDto(
                flight.getId(),
                flight.getFlightNo(),
                flight.getRoute().getOrigin().getCode(),
                flight.getRoute().getDestination().getCode(),
                flight.getDepartAt(),
                inventory.getSeatsTotal(),
                inventory.getSeatsLeft(),
                loadFactor,
                fareClasses.stream()
                    .sorted(java.util.Comparator.comparing(FareClassEntity::getBasePrice))
                    .map(f -> new InventoryFareClassDto(
                        f.getId(),
                        f.getCode(),
                        f.getCurrentPrice(),
                        f.getBasePrice(),
                        f.getSeatsAllocated()
                    ))
                    .collect(Collectors.toList())
            );

            results.add(dto);
        }

        results.sort(Comparator.comparing(InventoryFlightDto::departAt));

        return results;
    }
}
