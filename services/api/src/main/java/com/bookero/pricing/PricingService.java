package com.bookero.pricing;

import com.bookero.algorithms.AlgorithmRunResponse;
import com.bookero.algorithms.AlgorithmRunService;
import com.bookero.common.ApiException;
import org.springframework.stereotype.Service;

/**
 * Live repricing. Delegates straight to {@link AlgorithmRunService} so this endpoint
 * and the Algorithm Lab execute the same code and return the same payload.
 */
@Service
public class PricingService {

  private final AlgorithmRunService algorithmRunService;

  public PricingService(AlgorithmRunService algorithmRunService) {
    this.algorithmRunService = algorithmRunService;
  }

  public AlgorithmRunResponse reprice(RepriceRequest request) {
    if (request == null || request.algorithmKey() == null || request.algorithmKey().isBlank()) {
      throw ApiException.badRequest("algorithmKey is required");
    }
    return algorithmRunService.execute(request.algorithmKey(), request.flightIds());
  }
}
