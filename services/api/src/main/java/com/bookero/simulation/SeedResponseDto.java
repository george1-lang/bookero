package com.bookero.simulation;

/**
 * Response DTO for POST /api/simulate/seed.
 */
public record SeedResponseDto(
    long flights,
    long fareClasses,
    long routes,
    String hub,
    String note
) {
}
