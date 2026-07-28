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
        AND CAST(f.departAt AS DATE) = CAST(:departDate AS DATE)
        ORDER BY f.departAt ASC
        """)
    List<FlightEntity> findByOriginDestAndDepartDate(String originCode, String destCode, Instant departDate);

    List<FlightEntity> findAllByDepartAtAfter(Instant departAt);
}
