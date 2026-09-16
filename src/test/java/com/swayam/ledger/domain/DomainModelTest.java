package com.swayam.ledger.domain;

import com.swayam.ledger.TestcontainersConfiguration;
import com.swayam.ledger.repository.ExpenseGroupRepository;
import com.swayam.ledger.repository.ExpenseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DomainModelTest {

    @Autowired ExpenseGroupRepository groups;
    @Autowired ExpenseRepository expenses;
    @Autowired EntityManager entityManager;

    @Test
    void persistsAGroupWithMembersAndReadsItBack() {
        ExpenseGroup group = new ExpenseGroup("Goa trip", "INR");
        group.addMember("Alice");
        group.addMember("Bob");

        ExpenseGroup saved = groups.saveAndFlush(group);
        entityManager.clear();

        ExpenseGroup found = groups.findById(saved.getId()).orElseThrow();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getMembers())
                .extracting(Member::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void anExpenseSplitEquallyReconcilesWithItsShares() {
        ExpenseGroup group = new ExpenseGroup("Dinner", "INR");
        Member alice = group.addMember("Alice");
        Member bob = group.addMember("Bob");
        groups.saveAndFlush(group);

        Expense dinner = new Expense(
                group, alice, "Pizza", new BigDecimal("100.0000"), Instant.now());
        dinner.addShare(alice, new BigDecimal("50.0000"));
        dinner.addShare(bob, new BigDecimal("50.0000"));

        Expense saved = expenses.saveAndFlush(dinner);
        entityManager.clear();

        Expense found = expenses.findById(saved.getId()).orElseThrow();
        assertThat(found.getShares()).hasSize(2);
        assertThat(found.sharesReconcile()).isTrue();
    }

    @Test
    void sharesThatDoNotSumToTheAmountFailToReconcile() {
        ExpenseGroup group = new ExpenseGroup("Cab", "INR");
        Member alice = group.addMember("Alice");
        Member bob = group.addMember("Bob");

        Expense cab = new Expense(
                group, alice, "Airport cab", new BigDecimal("100.0000"), Instant.now());
        cab.addShare(alice, new BigDecimal("50.0000"));
        cab.addShare(bob, new BigDecimal("40.0000"));

        assertThat(cab.sharesReconcile()).isFalse();
    }

    @Test
    void reconciliationComparesValueNotScale() {
        ExpenseGroup group = new ExpenseGroup("Scale", "INR");
        Member alice = group.addMember("Alice");

        Expense e = new Expense(
                group, alice, "Tea", new BigDecimal("10.00"), Instant.now());
        e.addShare(alice, new BigDecimal("10.0000"));

        // BigDecimal.equals would say false here; compareTo says true.
        assertThat(e.sharesReconcile()).isTrue();
    }

    @Test
    void expensesAreFoundByGroupNewestFirst() {
        ExpenseGroup group = new ExpenseGroup("Trip", "INR");
        Member alice = group.addMember("Alice");
        groups.saveAndFlush(group);

        Instant now = Instant.now();
        Expense older = new Expense(group, alice, "Fuel", new BigDecimal("500.0000"), now.minusSeconds(3600));
        older.addShare(alice, new BigDecimal("500.0000"));
        Expense newer = new Expense(group, alice, "Tolls", new BigDecimal("200.0000"), now);
        newer.addShare(alice, new BigDecimal("200.0000"));
        expenses.saveAll(List.of(older, newer));
        expenses.flush();
        entityManager.clear();

        assertThat(expenses.findByGroupIdOrderByIncurredAtDesc(group.getId()))
                .extracting(Expense::getDescription)
                .containsExactly("Tolls", "Fuel");
    }
}
