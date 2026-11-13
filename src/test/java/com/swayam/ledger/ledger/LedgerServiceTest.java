package com.swayam.ledger.ledger;

import com.swayam.ledger.TestcontainersConfiguration;
import com.swayam.ledger.domain.ExpenseGroup;
import com.swayam.ledger.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerServiceTest {

    @Autowired LedgerService ledger;

    @Test
    void theWorkedExampleFromTheReadme() {
        ExpenseGroup group = ledger.createGroup("Dinner", "INR", List.of("Alice", "Bob", "Carol"));
        List<Member> roster = group.getMembers();
        Member alice = roster.get(0);

        ledger.postEqualSplitExpense(group.getId(), alice.getId(), "Dinner",
                new BigDecimal("100.00"), roster.stream().map(Member::getId).toList(), Instant.now());

        assertThat(ledger.balancesOf(group.getId()))
                .extracting(LedgerService.Balance::memberName, LedgerService.Balance::amount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Alice", new BigDecimal("66.66")),
                        org.assertj.core.groups.Tuple.tuple("Bob", new BigDecimal("-33.33")),
                        org.assertj.core.groups.Tuple.tuple("Carol", new BigDecimal("-33.33")));
        assertThat(ledger.isBalanced(group.getId())).isTrue();
    }

    @Test
    void anEmptyGroupIsTriviallyBalanced() {
        ExpenseGroup group = ledger.createGroup("Quiet", "INR", List.of("Solo"));
        assertThat(ledger.isBalanced(group.getId())).isTrue();
        assertThat(ledger.balancesOf(group.getId())).isEmpty();
    }

    /**
     * The invariant across a generated sequence rather than a hand-picked case:
     * whatever gets posted, the books still sum to zero.
     */
    @Test
    void theLedgerSumsToZeroAcross1000GeneratedExpenses() {
        ExpenseGroup group = ledger.createGroup("Trip", "INR", List.of("A", "B", "C", "D", "E"));
        List<UUID> roster = group.getMembers().stream().map(Member::getId).toList();
        Random random = new Random(20260916L);

        for (int i = 0; i < 1000; i++) {
            UUID payer = roster.get(random.nextInt(roster.size()));
            List<UUID> participants = roster.subList(0, 1 + random.nextInt(roster.size()));
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 100_000), 2);

            ledger.postEqualSplitExpense(group.getId(), payer, "expense " + i,
                    amount, participants, Instant.now());

            assertThat(ledger.isBalanced(group.getId()))
                    .as("ledger balanced after posting %d", i)
                    .isTrue();
        }

        BigDecimal net = ledger.balancesOf(group.getId()).stream()
                .map(LedgerService.Balance::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aPayerOutsideTheGroupIsRejected() {
        ExpenseGroup group = ledger.createGroup("Closed", "INR", List.of("Alice", "Bob"));

        assertThatThrownBy(() -> ledger.postEqualSplitExpense(group.getId(), UUID.randomUUID(),
                "Nope", new BigDecimal("10.00"), null, Instant.now()))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("payer is not a member");
    }

    @Test
    void aNonPositiveAmountIsRejected() {
        ExpenseGroup group = ledger.createGroup("Zero", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);

        assertThatThrownBy(() -> ledger.postEqualSplitExpense(group.getId(), alice.getId(),
                "Free", new BigDecimal("0.00"), null, Instant.now()))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void duplicateMemberNamesAreRejected() {
        assertThatThrownBy(() -> ledger.createGroup("Twins", "INR", List.of("Alice", "Alice")))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("unique");
    }
}
