package com.bookero.route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {
    @Query("SELECT r FROM RouteEntity r WHERE r.origin.code = :originCode AND r.destination.code = :destCode")
    Optional<RouteEntity> findByOriginCodeAndDestCode(String originCode, String destCode);

    @Query("SELECT r FROM RouteEntity r WHERE r.origin.code = :originCode")
    List<RouteEntity> findAllByOriginCode(String originCode);
}
