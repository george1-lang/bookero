package com.bookero.flight;

/**
 * Request DTO for flight search. The `date` field is interpreted as a UTC calendar day
 * in yyyy-MM-dd format.
 */
public record FlightSearchRequest(
    String origin,
    String dest,
    String date
) {
}
