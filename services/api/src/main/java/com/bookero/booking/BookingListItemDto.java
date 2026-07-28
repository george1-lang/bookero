package com.bookero.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Item in the traveler's booking list (GET /api/bookings/me).
 */
public record BookingListItemDto(
    UUID id,
    String flightNo,
    String origin,
    String dest,
    Instant departAt,
    String fareClassCode,
    BigDecimal paidPrice,
    Instant createdAt
) {
}
