package com.bookero.flight;

import com.bookero.common.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Flight search and detail endpoints. Authentication required but no role restriction.
 */
@RestController
@RequestMapping("/api/flights")
@PreAuthorize("isAuthenticated()")
public class FlightController {

    private final FlightSearchService flightSearchService;

    public FlightController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    /**
     * GET /api/flights/search?origin=&dest=&date=
     * Returns HTTP 200 with [] when nothing matches (including unknown airport codes).
     */
    @GetMapping("/search")
    public ResponseEntity<List<FlightSearchResultDto>> search(
        @RequestParam String origin,
        @RequestParam String dest,
        @RequestParam String date
    ) {
        List<FlightSearchResultDto> results = flightSearchService.search(origin, dest, date);
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/flights/routes
     * The routes the carrier flies, used to populate the search suggestions.
     */
    @GetMapping("/routes")
    public ResponseEntity<List<RouteOptionDto>> routes() {
        return ResponseEntity.ok(flightSearchService.bookableRoutes());
    }

    /**
     * GET /api/flights/{id}
     * Returns 404 when flight not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightSearchResultDto> getById(@PathVariable UUID id) {
        return flightSearchService.getById(id)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> ApiException.notFound("Flight " + id + " not found"));
    }
}
