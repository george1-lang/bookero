package com.bookero.ops;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inventory snapshot for one flight in the ops dashboard.
 */
public record InventoryFlightDto(
    UUID flightId,
    String flightNo,
    String origin,
    String dest,
    Instant departAt,
    Integer seatsTotal,
    Integer seatsLeft,
    Double loadFactor,
    List<InventoryFareClassDto> fareClasses
) {
}
