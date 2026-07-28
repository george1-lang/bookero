package com.bookero.pricing;

import com.bookero.algorithms.AlgorithmRunEntity;
import com.bookero.algorithms.AlgorithmRunService;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PricingService {

  private final AlgorithmRunService algorithmRunService;
  private final PriceHistoryRepository priceHistoryRepository;

  public PricingService(
      AlgorithmRunService algorithmRunService,
      PriceHistoryRepository priceHistoryRepository
  ) {
    this.algorithmRunService = algorithmRunService;
    this.priceHistoryRepository = priceHistoryRepository;
  }

  public Map<String, Object> reprice(RepriceRequest request) {
    var run = algorithmRunService.execute(request.algorithmKey(), request.flightIds());
    return buildResponse(run);
  }

  private Map<String, Object> buildResponse(AlgorithmRunEntity run) {
    // Load price updates from price_history for this run
    var priceUpdates = priceHistoryRepository.findAllByAlgorithmRunId(run.getId())
        .stream()
        .map(ph -> Map.ofEntries(
            Map.entry("flightId", ph.getFlight().getId()),
            Map.entry("flightNo", ph.getFlight().getFlightNo()),
            Map.entry("fareClassCode", ph.getFareClassCode()),
            Map.entry("oldPrice", null),
            Map.entry("newPrice", ph.getPrice())
        ))
        .toList();

    return Map.ofEntries(
        Map.entry("runId", run.getId()),
        Map.entry("algorithmKey", run.getAlgorithmKey()),
        Map.entry("status", run.getStatus()),
        Map.entry("durationMs", run.getDurationMs()),
        Map.entry("revenueDelta", run.getRevenueDelta()),
        Map.entry("flightsAffected", priceUpdates.size()),
        Map.entry("priceUpdates", priceUpdates),
        Map.entry("metrics", Map.of()),
        Map.entry("message", null)
    );
  }
}
