package com.swayam.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable row of the ledger. Never updated, never deleted — a correction
 * is a new pair of entries, so the history stays auditable.
 *
 * Sign convention: the stored amount is signed. A credit is positive (the member
 * is owed), a debit is negative (the member owes). A member's net position is
 * therefore SUM(amount) over their own rows, and the whole group's entries sum
 * to zero.
 */
@Entity
@Table(name = "ledger_entry", indexes = {
        @Index(name = "idx_ledger_entry_group", columnList = "group_id"),
        @Index(name = "idx_ledger_entry_member", columnList = "member_id")
})
public class LedgerEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** The expense that produced this entry. Null once settlements exist (M4). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryType type;

    /** Signed: positive for CREDIT, negative for DEBIT. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // required by JPA
    }

    private LedgerEntry(ExpenseGroup group, Member member, Expense expense,
                        EntryType type, BigDecimal magnitude) {
        this.group = group;
        this.member = member;
        this.expense = expense;
        this.type = type;
        this.amount = type == EntryType.CREDIT ? magnitude.abs() : magnitude.abs().negate();
    }

    /** The member put money down: they are owed it back. */
    public static LedgerEntry credit(ExpenseGroup group, Member member, Expense expense, BigDecimal magnitude) {
        return new LedgerEntry(group, member, expense, EntryType.CREDIT, magnitude);
    }

    /** The member consumed value: they owe it. */
    public static LedgerEntry debit(ExpenseGroup group, Member member, Expense expense, BigDecimal magnitude) {
        return new LedgerEntry(group, member, expense, EntryType.DEBIT, magnitude);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ExpenseGroup getGroup() { return group; }
    public Member getMember() { return member; }
    public Expense getExpense() { return expense; }
    public EntryType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }

    public enum EntryType { CREDIT, DEBIT }
}
