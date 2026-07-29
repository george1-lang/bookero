package com.bookero.algorithms;

import com.bookero.flight.FareClassEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Shared fare arithmetic. Every algorithm prices off {@code base_price} rather than
 * the price the previous run left behind, so runs are idempotent and comparable.
 */
final class Fares {

  /** No fare may move outside this band of its published base fare. */
  static final double MIN_MULTIPLIER = 0.70;
  static final double MAX_MULTIPLIER = 2.50;

  private Fares() {
  }

  static BigDecimal repriceFromBase(FareClassEntity fare, double multiplier) {
    double bounded = Math.clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    BigDecimal price = fare.getBasePrice()
        .multiply(BigDecimal.valueOf(bounded))
        .setScale(2, RoundingMode.HALF_UP);
    // A fare of zero would let seats leave for nothing; the floor is one currency unit.
    return price.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : price;
  }

  /** BigDecimal equality is scale-sensitive, so movement is always tested with compareTo. */
  static boolean moved(FareClassEntity fare, BigDecimal candidate) {
    return fare.getCurrentPrice().compareTo(candidate) != 0;
  }

  static PriceUpdate update(FareClassEntity fare, BigDecimal newPrice) {
    return new PriceUpdate(
        fare.getFlight().getId(),
        fare.getFlight().getFlightNo(),
        fare.getCode(),
        fare.getCurrentPrice(),
        newPrice);
  }

  static PriceUpdate update(FareClassEntity fare, BigDecimal newPrice, Integer seatsAllocated) {
    return new PriceUpdate(
        fare.getFlight().getId(),
        fare.getFlight().getFlightNo(),
        fare.getCode(),
        fare.getCurrentPrice(),
        newPrice,
        seatsAllocated);
  }

  /** Cheapest cabin first. */
  static List<FareClassEntity> byBaseFareAscending(List<FareClassEntity> fares) {
    return fares.stream().sorted(Comparator.comparing(FareClassEntity::getBasePrice)).toList();
  }

  /** Dearest cabin first - the order EMSR protection is computed in. */
  static List<FareClassEntity> byBaseFareDescending(List<FareClassEntity> fares) {
    return fares.stream()
        .sorted(Comparator.comparing(FareClassEntity::getBasePrice).reversed())
        .toList();
  }
}
