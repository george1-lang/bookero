package com.bookero.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {
    List<BookingEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<BookingEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByFlightId(UUID flightId);
}
