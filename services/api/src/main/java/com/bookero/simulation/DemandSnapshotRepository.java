package com.bookero.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface DemandSnapshotRepository extends JpaRepository<DemandSnapshotEntity, UUID> {
    @Query("""
        SELECT d FROM DemandSnapshotEntity d
        WHERE (d.flight.id, d.at) IN (
            SELECT d2.flight.id, MAX(d2.at)
            FROM DemandSnapshotEntity d2
            GROUP BY d2.flight.id
        )
        ORDER BY d.at DESC
        """)
    List<DemandSnapshotEntity> findLatestPerFlight();
}
