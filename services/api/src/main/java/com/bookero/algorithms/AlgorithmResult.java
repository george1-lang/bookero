package com.bookero.algorithms;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AlgorithmResult(
    String status,
    Long durationMs,
    BigDecimal revenueDelta,
    List<PriceUpdate> priceUpdates,
    Integer flightsAffected,
    String message,
    Map<String, Object> metrics
) {

  public static AlgorithmResult success(
      Long durationMs,
      BigDecimal revenueDelta,
      List<PriceUpdate> priceUpdates,
      Integer flightsAffected,
      Map<String, Object> metrics
  ) {
    return new AlgorithmResult(
        "SUCCESS",
        durationMs,
        revenueDelta,
        priceUpdates,
        flightsAffected,
        null,
        metrics
    );
  }

  public static AlgorithmResult failed(Long durationMs, String message) {
    return new AlgorithmResult(
        "FAILED",
        durationMs,
        null,
        List.of(),
        0,
        message,
        Map.of()
    );
  }

  /** Attaches an advisory note (e.g. a degraded data source) without changing status. */
  public AlgorithmResult withMessage(String note) {
    return note == null ? this
        : new AlgorithmResult(status, durationMs, revenueDelta, priceUpdates,
            flightsAffected, note, metrics);
  }
}
