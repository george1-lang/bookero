package com.bookero.flight;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FareClassRepository extends JpaRepository<FareClassEntity, UUID> {
    List<FareClassEntity> findAllByFlightId(UUID flightId);

    List<FareClassEntity> findAllByFlightIdIn(Collection<UUID> flightIds);
}
