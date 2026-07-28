package com.bookero.simulation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Demand simulation endpoint. ANALYST role required.
 */
@RestController
@RequestMapping("/api/simulate")
@PreAuthorize("hasRole('ANALYST')")
public class SimulationController {

    private final DemandSimulator demandSimulator;

    public SimulationController(DemandSimulator demandSimulator) {
        this.demandSimulator = demandSimulator;
    }

    /**
     * POST /api/simulate
     * Simulate demand on all open flights.
     * Intensity defaults to 5, clamped to [1, 10].
     */
    @PostMapping
    public ResponseEntity<SimulationResponseDto> simulate(
        @RequestBody(required = false) SimulationRequest request
    ) {
        int intensity = (request != null && request.intensity() != null) ? request.intensity() : 5;
        SimulationResponseDto response = demandSimulator.simulate(intensity);
        return ResponseEntity.ok(response);
    }
}
