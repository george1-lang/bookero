package com.bookero.algorithms;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire shape shared by the Algorithm Lab and the live reprice endpoint - the two
 * must stay byte-identical so Lab measurements describe the production path.
 */
public record AlgorithmRunResponse(
    UUID runId,
    String algorithmKey,
    String status,
    Long durationMs,
    BigDecimal revenueDelta,
    Integer flightsAffected,
    List<PriceUpdate> priceUpdates,
    Map<String, Object> metrics,
    String message,
    Instant createdAt) {

  public static AlgorithmRunResponse of(AlgorithmRunEntity run, AlgorithmResult result) {
    return new AlgorithmRunResponse(
        run.getId(),
        run.getAlgorithmKey(),
        run.getStatus(),
        run.getDurationMs(),
        run.getRevenueDelta(),
        result.flightsAffected() == null ? 0 : result.flightsAffected(),
        result.priceUpdates() == null ? List.of() : result.priceUpdates(),
        result.metrics() == null ? Map.of() : result.metrics(),
        result.message(),
        run.getCreatedAt());
  }

  /** History rows carry no price updates; the detail lives in price_history. */
  public static AlgorithmRunResponse ofHistory(AlgorithmRunEntity run) {
    return new AlgorithmRunResponse(
        run.getId(),
        run.getAlgorithmKey(),
        run.getStatus(),
        run.getDurationMs(),
        run.getRevenueDelta(),
        0,
        List.of(),
        Map.of(),
        null,
        run.getCreatedAt());
  }
}
