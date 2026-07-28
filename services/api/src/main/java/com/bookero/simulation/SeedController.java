package com.bookero.simulation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulation seeding endpoint. ANALYST role required.
 */
@RestController
@RequestMapping("/api/simulate")
@PreAuthorize("hasRole('ANALYST')")
public class SeedController {

    private final SeedService seedService;

    public SeedController(SeedService seedService) {
        this.seedService = seedService;
    }

    /**
     * POST /api/simulate/seed
     * Seed a hub-and-spoke flight network. Idempotent: calling twice does not duplicate flights.
     */
    @PostMapping("/seed")
    public ResponseEntity<SeedResponseDto> seed() {
        SeedResponseDto response = seedService.seed();
        return ResponseEntity.ok(response);
    }
}
