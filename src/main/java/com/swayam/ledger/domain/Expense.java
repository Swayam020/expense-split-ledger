package com.swayam.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One thing somebody paid for, plus how it was split.
 *
 * The amount is BigDecimal with an explicit scale, never double: 0.1 + 0.2
 * is not 0.3 in binary floating point, and a ledger that cannot add up is
 * not a ledger.
 */
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    /** Who actually put the money down. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Member payer;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "incurred_at", nullable = false)
    private Instant incurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {
        // required by JPA
    }

    public Expense(ExpenseGroup group, Member payer, String description,
                   BigDecimal amount, Instant incurredAt) {
        this.group = group;
        this.payer = payer;
        this.description = description;
        this.amount = amount;
        this.incurredAt = incurredAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public ExpenseShare addShare(Member member, BigDecimal shareAmount) {
        ExpenseShare share = new ExpenseShare(this, member, shareAmount);
        shares.add(share);
        return share;
    }

    /**
     * The invariant this entity exists to protect: the shares must account for
     * the whole expense and nothing more. compareTo, not equals, because
     * BigDecimal.equals("10.00", "10.0000") is false on scale alone.
     */
    public boolean sharesReconcile() {
        BigDecimal total = shares.stream()
                .map(ExpenseShare::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.compareTo(amount) == 0;
    }

    public UUID getId() { return id; }
    public ExpenseGroup getGroup() { return group; }
    public Member getPayer() { return payer; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public Instant getIncurredAt() { return incurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ExpenseShare> getShares() { return shares; }
}
