package com.swayam.ledger.ledger;

import com.swayam.ledger.domain.*;
import com.swayam.ledger.repository.LedgerEntryRepository;
import com.swayam.ledger.repository.SettlementRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inserts exactly one settlement and its ledger entries, in its own transaction.
 *
 * Kept as a separate bean from {@link SettlementService} so a unique-constraint
 * violation on a concurrent duplicate key rolls back only this transaction, never
 * the caller's. A self-invoked {@code @Transactional} method on the same class
 * would bypass the Spring proxy and run in the caller's transaction instead —
 * Postgres aborts that whole transaction on the first failed statement, which
 * would leave the caller unable to even look the winning row back up.
 */
@Component
class SettlementWriter {

    private final SettlementRepository settlements;
    private final LedgerEntryRepository entries;

    SettlementWriter(SettlementRepository settlements, LedgerEntryRepository entries) {
        this.settlements = settlements;
        this.entries = entries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Settlement insert(ExpenseGroup group, Member from, Member to, BigDecimal amount,
                      String idempotencyKey, String requestHash) {
        // Flushed immediately so the unique-key violation surfaces here, inside the
        // try/catch in SettlementService, rather than silently at commit time.
        Settlement saved = settlements.saveAndFlush(
                new Settlement(group, from, to, amount, idempotencyKey, requestHash));
        entries.saveAll(List.of(
                LedgerEntry.creditForSettlement(group, from, saved, amount),
                LedgerEntry.debitForSettlement(group, to, saved, amount)));
        return saved;
    }
}
