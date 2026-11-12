package com.swayam.ledger.ledger;

import com.swayam.ledger.ledger.LedgerService.Balance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementMinimizerTest {

    @Test
    void twoMembersProduceExactlyOneTransfer() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();

        List<SettlementMinimizer.Transfer> transfers = SettlementMinimizer.minimize(List.of(
                new Balance(aliceId, "Alice", new BigDecimal("50.00")),
                new Balance(bobId, "Bob", new BigDecimal("-50.00"))));

        assertThat(transfers).hasSize(1);
        SettlementMinimizer.Transfer transfer = transfers.get(0);
        assertThat(transfer.fromMemberId()).isEqualTo(bobId);
        assertThat(transfer.fromMemberName()).isEqualTo("Bob");
        assertThat(transfer.toMemberId()).isEqualTo(aliceId);
        assertThat(transfer.toMemberName()).isEqualTo("Alice");
        assertThat(transfer.amount()).isEqualByComparingTo("50.00");
        assertThat(transfer.amount().scale()).isEqualTo(MoneySplitter.SCALE);
    }

    @Test
    void allZeroBalancesProduceNoTransfers() {
        List<SettlementMinimizer.Transfer> transfers = SettlementMinimizer.minimize(List.of(
                new Balance(UUID.randomUUID(), "Alice", new BigDecimal("0.00")),
                new Balance(UUID.randomUUID(), "Bob", new BigDecimal("0.00"))));

        assertThat(transfers).isEmpty();
    }

    @Test
    void tenMembersWithArbitraryBalancesNeedAtMostNineTransfers() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(UUID.randomUUID());
        }
        List<String> names = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
        List<String> balances = List.of("120.00", "80.00", "50.00", "30.00", "10.00",
                "-100.00", "-90.00", "-60.00", "-25.00", "-15.00");

        List<Balance> starting = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            starting.add(new Balance(ids.get(i), names.get(i), new BigDecimal(balances.get(i))));
        }
        // The greedy pairing preserves the totals, so this precondition is what makes
        // the per-member assertions below meaningful.
        assertThat(sum(starting)).isEqualByComparingTo("0.00");

        List<SettlementMinimizer.Transfer> transfers = SettlementMinimizer.minimize(starting);

        assertThat(transfers).hasSizeLessThanOrEqualTo(ids.size() - 1);

        // Counting transfers proves nothing on its own: every member must end up
        // exactly where the ledger said they were.
        Map<UUID, BigDecimal> settled = new HashMap<>();
        for (SettlementMinimizer.Transfer t : transfers) {
            settled.merge(t.toMemberId(), t.amount(), BigDecimal::add);
            settled.merge(t.fromMemberId(), t.amount().negate(), BigDecimal::add);
        }
        for (int i = 0; i < ids.size(); i++) {
            BigDecimal net = settled.getOrDefault(ids.get(i), BigDecimal.ZERO);
            assertThat(net).isEqualByComparingTo(balances.get(i));
        }
    }

    @Test
    void aMemberWithAZeroBalanceIsExcludedEntirely() {
        UUID carolId = UUID.randomUUID();

        List<SettlementMinimizer.Transfer> transfers = SettlementMinimizer.minimize(List.of(
                new Balance(UUID.randomUUID(), "Alice", new BigDecimal("25.00")),
                new Balance(carolId, "Carol", new BigDecimal("0.00")),
                new Balance(UUID.randomUUID(), "Bob", new BigDecimal("-25.00"))));

        assertThat(transfers).isNotEmpty();
        List<UUID> parties = new ArrayList<>();
        for (SettlementMinimizer.Transfer t : transfers) {
            parties.add(t.fromMemberId());
            parties.add(t.toMemberId());
        }
        assertThat(parties).doesNotContain(carolId);
    }

    private static BigDecimal sum(List<Balance> balances) {
        return balances.stream().map(Balance::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
