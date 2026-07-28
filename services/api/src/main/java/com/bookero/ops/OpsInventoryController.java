package com.bookero.ops;

import com.bookero.analytics.AnalyticsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operations endpoints for inventory and metrics. ANALYST role required.
 */
@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasRole('ANALYST')")
public class OpsInventoryController {

    private final OpsInventoryService inventoryService;
    private final AnalyticsClient analyticsClient;

    public OpsInventoryController(OpsInventoryService inventoryService, AnalyticsClient analyticsClient) {
        this.inventoryService = inventoryService;
        this.analyticsClient = analyticsClient;
    }

    /**
     * GET /api/ops/inventory
     * Return current inventory state for all flights, sorted by departure time.
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryFlightDto>> getInventory() {
        List<InventoryFlightDto> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(inventory);
    }

    /**
     * GET /api/ops/metrics
     * Proxy to analytics service for revenue metrics.
     * Returns HTTP 200 even when analytics is down, with available=false.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        var analyticsMetrics = analyticsClient.revenueMetrics();

        if (analyticsMetrics.isPresent()) {
            var metrics = new HashMap<>(analyticsMetrics.get());
            metrics.put("available", true);
            return ResponseEntity.ok(metrics);
        } else {
            var fallback = new HashMap<String, Object>();
            fallback.put("available", false);
            fallback.put("totalRevenue", 0);
            fallback.put("baselineRevenue", 0);
            fallback.put("revenueDelta", 0);
            fallback.put("revenueDeltaPct", 0);
            fallback.put("loadFactor", 0);
            fallback.put("avgFare", 0);
            fallback.put("seatsSold", 0);
            fallback.put("seatsTotal", 0);
            fallback.put("bookingCount", 0);
            fallback.put("revenueByDay", List.of());
            return ResponseEntity.ok(fallback);
        }
    }
}
