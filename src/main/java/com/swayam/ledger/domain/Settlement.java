package com.swayam.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One settle-up between two members, keyed by the client's Idempotency-Key so a
 * retried or concurrently duplicated request replays this row instead of paying
 * the same debt twice.
 */
@Entity
@Table(name = "settlement", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class Settlement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id", nullable = false)
    private Member fromMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_member_id", nullable = false)
    private Member toMember;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
    private String idempotencyKey;

    /** SHA-256 of the request body, so a reused key with a different body is rejected rather than replayed. */
    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Settlement() {
        // required by JPA
    }

    public Settlement(ExpenseGroup group, Member fromMember, Member toMember, BigDecimal amount,
                      String idempotencyKey, String requestHash) {
        this.group = group;
        this.fromMember = fromMember;
        this.toMember = toMember;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ExpenseGroup getGroup() { return group; }
    public Member getFromMember() { return fromMember; }
    public Member getToMember() { return toMember; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
}
