package com.bookero.flight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FlightRepository extends JpaRepository<FlightEntity, UUID> {
    @Query("""
        SELECT DISTINCT f FROM FlightEntity f
        JOIN FETCH f.route r
        JOIN FETCH r.origin
        JOIN FETCH r.destination
        WHERE r.origin.code = :originCode
        AND r.destination.code = :destCode
        AND f.departAt >= :dayStart AND f.departAt < :dayEnd
        ORDER BY f.departAt ASC
        """)
    List<FlightEntity> findByOriginDestAndDepartDate(String originCode, String destCode,
                                                    Instant dayStart, Instant dayEnd);

    /** Bookable routes on the seeded network, with the next departure on each. */
    @Query("""
        SELECT r.origin.code, r.origin.city, r.destination.code, r.destination.city,
               r.distanceKm, COUNT(f), MIN(f.departAt)
        FROM FlightEntity f JOIN f.route r
        WHERE f.departAt >= :from
        GROUP BY r.origin.code, r.origin.city, r.destination.code, r.destination.city, r.distanceKm
        ORDER BY COUNT(f) DESC, MIN(f.departAt) ASC
        """)
    List<Object[]> findBookableRoutes(Instant from);

    List<FlightEntity> findAllByDepartAtAfter(Instant departAt);

    long countByRouteId(UUID routeId);

    long countByDepartAtAfter(Instant departAt);
}
