package com.bookero.flight;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fare class details in a flight search result.
 */
public record FareClassDto(
    UUID id,
    String code,
    BigDecimal currentPrice,
    BigDecimal basePrice,
    Integer seatsAllocated
) {
}
