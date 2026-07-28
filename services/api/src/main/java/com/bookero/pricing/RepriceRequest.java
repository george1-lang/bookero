package com.bookero.pricing;

import java.util.List;
import java.util.UUID;

public record RepriceRequest(
    String algorithmKey,
    List<UUID> flightIds
) {
}
