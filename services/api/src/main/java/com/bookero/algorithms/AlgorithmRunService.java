package com.bookero.algorithms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The single execution path for every algorithm. Both the Algorithm Lab
 * ({@code POST /api/algorithms/{key}/run}) and live repricing
 * ({@code POST /api/pricing/reprice}) go through here, so Lab measurements describe
 * exactly what production does.
 *
 * <p>Deliberately not transactional: an unknown key must surface as 404 before any
 * transaction opens, and a failing algorithm must leave a FAILED audit row behind
 * even though its own work is rolled back.
 */
@Service
public class AlgorithmRunService {

  private static final Logger log = LoggerFactory.getLogger(AlgorithmRunService.class);

  private final AlgorithmRegistry algorithmRegistry;
  private final AlgorithmRunStore store;

  public AlgorithmRunService(AlgorithmRegistry algorithmRegistry, AlgorithmRunStore store) {
    this.algorithmRegistry = algorithmRegistry;
    this.store = store;
  }

  public AlgorithmRunResponse execute(String algorithmKey, List<UUID> flightIds) {
    Algorithm algorithm = algorithmRegistry.get(algorithmKey);

    long startNanos = System.nanoTime();
    try {
      return store.runAndRecord(algorithm, flightIds);
    } catch (Exception e) {
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
      String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.error("algorithm {} failed after {} ms: {}", algorithmKey, durationMs, message, e);
      // Prices are untouched by the rolled-back transaction; the Lab shows the failure.
      return store.recordFailure(algorithmKey, message, durationMs);
    }
  }
}
