package com.swayam.ledger.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** A person within one group. A human in two groups is two Member rows. */
@Entity
@Table(
        name = "member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "name"}))
public class Member {

    @Id
    @GeneratedValue
    private UUID id;

    /** LAZY on purpose: loading a member must not drag the whole group in. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @Column(nullable = false)
    private String name;

    protected Member() {
        // required by JPA
    }

    Member(ExpenseGroup group, String name) {
        this.group = group;
        this.name = name;
    }

    public UUID getId() { return id; }
    public ExpenseGroup getGroup() { return group; }
    public String getName() { return name; }
}
