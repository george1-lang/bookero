package com.bookero.simulation;

/**
 * Response DTO for POST /api/simulate.
 */
public record SimulationResponseDto(
    long demandSnapshots,
    long syntheticBookings,
    long durationMs
) {
}
