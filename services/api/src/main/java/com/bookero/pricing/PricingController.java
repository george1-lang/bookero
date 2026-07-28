package com.bookero.pricing;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/pricing")
@PreAuthorize("hasRole('ANALYST')")
public class PricingController {

  private final PricingService pricingService;

  public PricingController(PricingService pricingService) {
    this.pricingService = pricingService;
  }

  @PostMapping("/reprice")
  public ResponseEntity<Map<String, Object>> reprice(@RequestBody RepriceRequest request) {
    var response = pricingService.reprice(request);
    return ResponseEntity.ok(response);
  }
}
