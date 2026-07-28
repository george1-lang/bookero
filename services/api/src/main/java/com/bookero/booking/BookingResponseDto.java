package com.bookero.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a created booking (HTTP 201 POST /api/bookings).
 */
public record BookingResponseDto(
    UUID bookingId,
    UUID userId,
    UUID flightId,
    UUID fareClassId,
    BigDecimal paidPrice,
    Instant createdAt
) {
}
