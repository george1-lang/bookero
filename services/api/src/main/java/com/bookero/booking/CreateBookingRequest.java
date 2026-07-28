package com.bookero.booking;

import java.util.UUID;

/**
 * Request body for POST /api/bookings.
 */
public record CreateBookingRequest(
    UUID flightId,
    UUID fareClassId
) {
}
