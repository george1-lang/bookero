package com.bookero.booking;

import com.bookero.auth.AuthenticatedUser;
import com.bookero.common.ApiException;
import com.bookero.common.BookingCreatedEvent;
import com.bookero.flight.FareClassEntity;
import com.bookero.flight.FareClassRepository;
import com.bookero.flight.FlightEntity;
import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import com.bookero.inventory.InventoryRepository;
import com.bookero.auth.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Booking service with pessimistic locking to prevent oversell.
 * Transaction sequence: SELECT FOR UPDATE → check seats → decrement → INSERT booking → COMMIT.
 * After commit, publishes BookingCreatedEvent for optional post-booking reprice.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final InventoryRepository inventoryRepository;
    private final FlightRepository flightRepository;
    private final FareClassRepository fareClassRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(
        BookingRepository bookingRepository,
        InventoryRepository inventoryRepository,
        FlightRepository flightRepository,
        FareClassRepository fareClassRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.inventoryRepository = inventoryRepository;
        this.flightRepository = flightRepository;
        this.fareClassRepository = fareClassRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Book a seat on a flight for a specific fare class.
     * Transactional with pessimistic lock to ensure oversell protection.
     * Returns HTTP 409 (Conflict) if no seats available.
     * Returns HTTP 404 if flight or fare class not found.
     * Returns HTTP 400 if fare class does not belong to the flight.
     */
    @Transactional
    public BookingResponseDto book(UUID flightId, UUID fareClassId, AuthenticatedUser user) {
        FlightEntity flight = flightRepository.findById(flightId)
            .orElseThrow(() -> ApiException.notFound("Flight " + flightId + " not found"));

        FareClassEntity fareClass = fareClassRepository.findById(fareClassId)
            .orElseThrow(() -> ApiException.notFound("Fare class " + fareClassId + " not found"));

        if (!fareClass.getFlight().getId().equals(flightId)) {
            throw ApiException.badRequest("Fare class " + fareClassId + " does not belong to flight " + flightId);
        }

        // Pessimistic lock: acquire exclusive lock on the inventory row.
        InventoryEntity inventory = inventoryRepository.findByIdForUpdate(flightId)
            .orElseThrow(() -> ApiException.notFound("Inventory for flight " + flightId + " not found"));

        // Check oversell: if no seats left, reject with 409.
        if (inventory.getSeatsLeft() < 1) {
            throw ApiException.conflict("No seats available on flight " + flight.getFlightNo());
        }

        // Decrement seats.
        inventory.setSeatsLeft(inventory.getSeatsLeft() - 1);
        inventoryRepository.save(inventory);

        // Create booking at current price.
        var bookingId = UUID.randomUUID();
        var userEntity = userRepository.findById(user.id())
            .orElseThrow(() -> ApiException.notFound("User not found"));

        BookingEntity booking = new BookingEntity(
            bookingId,
            userEntity,
            flight,
            fareClass,
            fareClass.getCurrentPrice(),
            Instant.now()
        );

        bookingRepository.save(booking);

        // Publish event after transaction commits.
        eventPublisher.publishEvent(new BookingCreatedEvent(
            flightId,
            fareClassId,
            inventory.getSeatsLeft()
        ));

        return new BookingResponseDto(
            bookingId,
            flightId,
            flight.getFlightNo(),
            fareClassId,
            fareClass.getCode(),
            booking.getPaidPrice(),
            booking.getCreatedAt()
        );
    }

    /**
     * Get traveler's bookings, paginated, newest first.
     */
    @Transactional(readOnly = true)
    public List<BookingListItemDto> getMyBookings(UUID userId, int page, int size) {
        var bookings = bookingRepository.findAllByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200)));

        return bookings.stream()
            .map(b -> new BookingListItemDto(
                b.getId(),
                b.getFlight().getFlightNo(),
                b.getFlight().getRoute().getOrigin().getCode(),
                b.getFlight().getRoute().getDestination().getCode(),
                b.getFlight().getDepartAt(),
                b.getFareClass().getCode(),
                b.getPaidPrice(),
                b.getCreatedAt()
            ))
            .toList();
    }
}
