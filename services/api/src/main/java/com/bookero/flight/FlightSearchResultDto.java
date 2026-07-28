package com.bookero.flight;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete flight result with all metadata, fare classes, and inventory.
 * Field names match the exact API contract expected by web clients and e2e tests.
 */
public record FlightSearchResultDto(
    UUID id,
    String flightNo,
    Instant departAt,
    AirportDto origin,
    AirportDto dest,
    Integer distanceKm,
    Integer seatsLeft,
    Integer seatsTotal,
    BigDecimal lowestFare,
    List<FareClassDto> fareClasses
) {
}
