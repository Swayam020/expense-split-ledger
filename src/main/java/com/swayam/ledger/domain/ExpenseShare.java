package com.swayam.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** One member's slice of one expense. Shares of an expense sum to its amount. */
@Entity
@Table(
        name = "expense_share",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "member_id"}))
public class ExpenseShare {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    protected ExpenseShare() {
        // required by JPA
    }

    ExpenseShare(Expense expense, Member member, BigDecimal amount) {
        this.expense = expense;
        this.member = member;
        this.amount = amount;
    }

    public UUID getId() { return id; }
    public Expense getExpense() { return expense; }
    public Member getMember() { return member; }
    public BigDecimal getAmount() { return amount; }
}
