package com.bookero.algorithms;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlgorithmRunRepository extends JpaRepository<AlgorithmRunEntity, UUID> {
    List<AlgorithmRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
        SELECT a FROM AlgorithmRunEntity a
        WHERE a.algorithmKey = :algorithmKey
        ORDER BY a.createdAt DESC
        LIMIT 1
        """)
    Optional<AlgorithmRunEntity> findLatestByKey(String algorithmKey);
}
