package com.bookero.flight;

/**
 * Lightweight airport reference for flight search results.
 */
public record AirportDto(
    String code,
    String name,
    String city
) {
}
