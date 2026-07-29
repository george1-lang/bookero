package com.bookero.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response for a created booking (HTTP 201 POST /api/bookings). */
public record BookingResponseDto(
    UUID id,
    UUID flightId,
    String flightNo,
    UUID fareClassId,
    String fareClassCode,
    BigDecimal paidPrice,
    Instant createdAt
) {
}
