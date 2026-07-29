package com.bookero.flight;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A bookable route on the seeded network, offered to travellers so they never have
 * to guess which airport pairs the carrier actually flies.
 */
public record RouteOptionDto(
    String origin,
    String originCity,
    String dest,
    String destCity,
    Integer distanceKm,
    long departures,
    Instant nextDepartAt,
    BigDecimal lowestFare
) {
}
