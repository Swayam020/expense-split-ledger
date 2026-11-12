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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MoneySplitterAuditTest {

    @Autowired LedgerService ledger;

    @Test
    void anExpenseAmountWithMoreThanTwoDecimalPlacesIsRejectedWithASpecificMessage() {
        ExpenseGroup group = ledger.createGroup("Precision", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);
        List<UUID> everyone = group.getMembers().stream().map(Member::getId).toList();

        assertThatThrownBy(() -> ledger.postEqualSplitExpense(group.getId(), alice.getId(),
                "Sub-rupee precision", new BigDecimal("100.1234"), everyone, Instant.now()))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("at most 2 decimal places")
                .hasMessageNotContaining("shares do not sum");
    }

    @Test
    void anAmountWithTrailingZeroesAtExtraScaleIsStillAccepted() {
        ExpenseGroup group = ledger.createGroup("Precision", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);
        List<UUID> everyone = group.getMembers().stream().map(Member::getId).toList();

        ledger.postEqualSplitExpense(group.getId(), alice.getId(), "Trailing zeroes",
                new BigDecimal("100.500"), everyone, Instant.now());

        // isEqualByComparingTo, not equals: Alice's credit keeps the raw scale-3 amount
        // while both debits carry the rounded scale-2 shares.
        assertThat(netOf(group.getId(), alice.getId())).isEqualByComparingTo("50.25");
        assertThat(netOf(group.getId(), group.getMembers().get(1).getId())).isEqualByComparingTo("-50.25");
    }

    @Test
    void splittingAVerySmallAmountAcrossManyMembersProducesZeroSharesWithoutBreakingBalance() {
        ExpenseGroup group = ledger.createGroup("Paisa", "INR", List.of("A", "B", "C", "D", "E"));
        Member a = group.getMembers().get(0);
        List<UUID> everyone = group.getMembers().stream().map(Member::getId).toList();

        ledger.postEqualSplitExpense(group.getId(), a.getId(), "Two paisas",
                new BigDecimal("0.02"), everyone, Instant.now());

        assertThat(ledger.isBalanced(group.getId())).isTrue();
    }

    /**
     * 15 integer digits plus the 2-digit minor-unit shift is 17 digits, comfortably
     * inside Long.MAX_VALUE's ~19, so the splitter's long arithmetic cannot overflow.
     */
    @Test
    void theLargestAmountTheApiAcceptsNeverOverflowsMinorUnitConversion() {
        ExpenseGroup group = ledger.createGroup("Ceiling", "INR", List.of("A", "B", "C"));
        Member a = group.getMembers().get(0);
        List<UUID> everyone = group.getMembers().stream().map(Member::getId).toList();

        ledger.postEqualSplitExpense(group.getId(), a.getId(), "At the API ceiling",
                new BigDecimal("999999999999999.99"), everyone, Instant.now());

        assertThat(ledger.isBalanced(group.getId())).isTrue();
    }

    private BigDecimal netOf(UUID groupId, UUID memberId) {
        return ledger.balancesOf(groupId).stream()
                .filter(b -> b.memberId().equals(memberId))
                .map(LedgerService.Balance::amount)
                .findFirst()
                .orElseThrow();
    }
}
