package com.bookero.simulation;

/**
 * Request body for POST /api/simulate.
 * Intensity controls demand pressure: clamp 1-10, default 5.
 */
public record SimulationRequest(
    Integer intensity
) {
}
