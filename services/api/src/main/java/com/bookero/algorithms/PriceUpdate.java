package com.bookero.algorithms;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One fare movement produced by an algorithm. {@code seatsAllocated} is null unless
 * the algorithm also revises booking limits (seat-protection algorithms do).
 */
public record PriceUpdate(
    UUID flightId,
    String flightNo,
    String fareClassCode,
    BigDecimal oldPrice,
    BigDecimal newPrice,
    Integer seatsAllocated
) {

  public PriceUpdate(UUID flightId, String flightNo, String fareClassCode,
                     BigDecimal oldPrice, BigDecimal newPrice) {
    this(flightId, flightNo, fareClassCode, oldPrice, newPrice, null);
  }
}
