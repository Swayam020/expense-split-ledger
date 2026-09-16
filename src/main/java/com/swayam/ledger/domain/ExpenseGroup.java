package com.swayam.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A set of people who share expenses. Deliberately has no balance column:
 * balances are derived from ledger entries, never stored. See README.
 *
 * Mapped to "expense_group" because GROUP is a reserved SQL word.
 */
@Entity
@Table(name = "expense_group")
public class ExpenseGroup {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** ISO-4217 code. One currency per group keeps the ledger single-unit. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Member> members = new ArrayList<>();

    protected ExpenseGroup() {
        // required by JPA
    }

    public ExpenseGroup(String name, String currency) {
        this.name = name;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Keeps both sides of the association in step. */
    public Member addMember(String memberName) {
        Member member = new Member(this, memberName);
        members.add(member);
        return member;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Member> getMembers() { return members; }
}
