package com.swayam.ledger.repository;

import com.swayam.ledger.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);
}
