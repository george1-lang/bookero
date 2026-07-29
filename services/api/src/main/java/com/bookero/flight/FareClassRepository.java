package com.bookero.flight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FareClassRepository extends JpaRepository<FareClassEntity, UUID> {
    List<FareClassEntity> findAllByFlightId(UUID flightId);

    List<FareClassEntity> findAllByFlightIdIn(Collection<UUID> flightIds);

    @Query("""
        SELECT fc FROM FareClassEntity fc
        JOIN FETCH fc.flight f JOIN FETCH f.route r
        JOIN FETCH r.origin JOIN FETCH r.destination
        WHERE f.departAt >= :from
        """)
    List<FareClassEntity> findAllUpcoming(Instant from);
}
