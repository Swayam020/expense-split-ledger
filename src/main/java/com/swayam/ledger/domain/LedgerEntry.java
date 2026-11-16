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

    /** The expense that produced this entry. Null for a settlement-sourced entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    /** The settlement that produced this entry. Null for an expense-sourced entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;

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

    private LedgerEntry(ExpenseGroup group, Member member, Expense expense, Settlement settlement,
                        EntryType type, BigDecimal magnitude) {
        this.group = group;
        this.member = member;
        this.expense = expense;
        this.settlement = settlement;
        this.type = type;
        this.amount = type == EntryType.CREDIT ? magnitude.abs() : magnitude.abs().negate();
    }

    /** The member put money down on an expense: they are owed it back. */
    public static LedgerEntry credit(ExpenseGroup group, Member member, Expense expense, BigDecimal magnitude) {
        return new LedgerEntry(group, member, expense, null, EntryType.CREDIT, magnitude);
    }

    /** The member consumed value from an expense: they owe it. */
    public static LedgerEntry debit(ExpenseGroup group, Member member, Expense expense, BigDecimal magnitude) {
        return new LedgerEntry(group, member, expense, null, EntryType.DEBIT, magnitude);
    }

    /** The member paid a settlement: their debt shrinks, so it's a credit. */
    public static LedgerEntry creditForSettlement(ExpenseGroup group, Member member, Settlement settlement, BigDecimal magnitude) {
        return new LedgerEntry(group, member, null, settlement, EntryType.CREDIT, magnitude);
    }

    /** The member received a settlement: what they're owed shrinks, so it's a debit. */
    public static LedgerEntry debitForSettlement(ExpenseGroup group, Member member, Settlement settlement, BigDecimal magnitude) {
        return new LedgerEntry(group, member, null, settlement, EntryType.DEBIT, magnitude);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ExpenseGroup getGroup() { return group; }
    public Member getMember() { return member; }
    public Expense getExpense() { return expense; }
    public Settlement getSettlement() { return settlement; }
    public EntryType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }

    public enum EntryType { CREDIT, DEBIT }
}
