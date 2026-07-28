package com.bookero.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, UUID> {
}
