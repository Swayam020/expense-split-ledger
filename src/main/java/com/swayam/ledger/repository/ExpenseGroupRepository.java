package com.swayam.ledger.repository;

import com.swayam.ledger.domain.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, UUID> {
}
