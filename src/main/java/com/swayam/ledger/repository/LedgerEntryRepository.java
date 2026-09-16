package com.swayam.ledger.repository;

import com.swayam.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByGroupId(UUID groupId);

    /**
     * Balances are derived here, in one grouped query, rather than read from a
     * stored column. This is the whole point of the project.
     */
    @Query("""
            select e.member.id, e.member.name, coalesce(sum(e.amount), 0)
            from LedgerEntry e
            where e.group.id = :groupId
            group by e.member.id, e.member.name
            order by e.member.name
            """)
    List<Object[]> findBalancesByGroupId(@Param("groupId") UUID groupId);

    /** The zero-sum invariant, asserted against the database itself. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.group.id = :groupId")
    BigDecimal sumAmountsByGroupId(@Param("groupId") UUID groupId);
}
