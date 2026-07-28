package com.bookero.algorithms;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceUpdate(
    UUID flightId,
    String flightNo,
    String fareClassCode,
    BigDecimal oldPrice,
    BigDecimal newPrice
) {
}
