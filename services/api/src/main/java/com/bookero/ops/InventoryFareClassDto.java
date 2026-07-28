package com.bookero.ops;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fare class details in an inventory snapshot.
 */
public record InventoryFareClassDto(
    UUID id,
    String code,
    BigDecimal currentPrice,
    BigDecimal basePrice,
    Integer seatsAllocated
) {
}
