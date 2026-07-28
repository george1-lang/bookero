package com.bookero.common;

import java.util.UUID;

/**
 * Published after a booking commits. Decouples the booking transaction from the
 * optional post-booking reprice so neither package depends on the other.
 */
public record BookingCreatedEvent(UUID flightId, UUID fareClassId, int seatsLeftAfter) {
}
