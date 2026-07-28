package com.bookero.algorithms;

import java.util.UUID;

public record AlgorithmDto(
    String key,
    String displayName,
    String family,
    String description,
    Long lastDurationMs,
    java.math.BigDecimal lastRevenueDelta,
    String lastStatus,
    java.time.Instant lastRunAt
) {
}
