package com.bookero.pricing;

import com.bookero.algorithms.AlgorithmRunResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@PreAuthorize("hasRole('ANALYST')")
public class PricingController {

  private final PricingService pricingService;

  public PricingController(PricingService pricingService) {
    this.pricingService = pricingService;
  }

  @PostMapping("/reprice")
  public ResponseEntity<AlgorithmRunResponse> reprice(@RequestBody RepriceRequest request) {
    return ResponseEntity.ok(pricingService.reprice(request));
  }
}
