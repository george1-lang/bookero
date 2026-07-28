package com.bookero.booking;

import com.bookero.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Booking endpoints. TRAVELER role required.
 * POST returns HTTP 201 on success, 409 on oversell, 400/404 on validation errors.
 * GET returns HTTP 200 with paginated list.
 */
@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('TRAVELER')")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /api/bookings
     * Book a seat on a flight for the current traveler.
     * Returns 201 on success, 409 on oversell, 400/404 on validation errors.
     */
    @PostMapping
    public ResponseEntity<BookingResponseDto> createBooking(@RequestBody CreateBookingRequest request) {
        var user = CurrentUser.get();
        BookingResponseDto booking = bookingService.book(request.flightId(), request.fareClassId(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    /**
     * GET /api/bookings/me
     * List the current traveler's bookings, paginated, newest first.
     * Default page size is 50.
     */
    @GetMapping("/me")
    public ResponseEntity<List<BookingListItemDto>> getMyBookings(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        var user = CurrentUser.get();
        List<BookingListItemDto> bookings = bookingService.getMyBookings(user.id(), page, size);
        return ResponseEntity.ok(bookings);
    }
}
