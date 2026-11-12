package com.swayam.ledger.ledger;

import com.swayam.ledger.TestcontainersConfiguration;
import com.swayam.ledger.domain.ExpenseGroup;
import com.swayam.ledger.domain.Member;
import com.swayam.ledger.domain.Settlement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementServiceTest {

    @Autowired SettlementService settlements;
    @Autowired LedgerService ledger;

    @Test
    void settlingReducesBothMembersTowardsZeroAndKeepsTheLedgerBalanced() {
        ExpenseGroup group = ledger.createGroup("Goa", "INR", List.of("Alice", "Bob"));
        List<Member> roster = group.getMembers();
        Member alice = roster.get(0);
        Member bob = roster.get(1);

        ledger.postEqualSplitExpense(group.getId(), alice.getId(), "Hotel",
                new BigDecimal("100.00"), null, java.time.Instant.now());
        // Bob owes Alice 50.00. He settles it in full.
        settlements.settle(group.getId(), bob.getId(), alice.getId(), new BigDecimal("50.00"), "key-1");

        assertThat(ledger.balancesOf(group.getId()))
                .extracting(LedgerService.Balance::memberName, LedgerService.Balance::amount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Alice", BigDecimal.ZERO.setScale(2)),
                        org.assertj.core.groups.Tuple.tuple("Bob", BigDecimal.ZERO.setScale(2)));
        assertThat(ledger.isBalanced(group.getId())).isTrue();
    }

    @Test
    void replayingTheSameKeyAndBodyReturnsTheOriginalWithoutASecondSettlement() {
        ExpenseGroup group = ledger.createGroup("Replay", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);
        Member bob = group.getMembers().get(1);

        Settlement first = settlements.settle(group.getId(), bob.getId(), alice.getId(),
                new BigDecimal("20.00"), "replay-key");
        Settlement second = settlements.settle(group.getId(), bob.getId(), alice.getId(),
                new BigDecimal("20.00"), "replay-key");

        assertThat(second.getId()).isEqualTo(first.getId());
        // Only one settlement's worth of movement happened, not two.
        assertThat(ledger.balancesOf(group.getId()))
                .extracting(LedgerService.Balance::amount)
                .containsExactlyInAnyOrder(new BigDecimal("20.00"), new BigDecimal("-20.00"));
    }

    @Test
    void reusingAKeyWithADifferentBodyIsAConflict() {
        ExpenseGroup group = ledger.createGroup("Conflict", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);
        Member bob = group.getMembers().get(1);

        settlements.settle(group.getId(), bob.getId(), alice.getId(), new BigDecimal("20.00"), "shared-key");

        assertThatThrownBy(() -> settlements.settle(group.getId(), bob.getId(), alice.getId(),
                new BigDecimal("30.00"), "shared-key"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different request");
    }

    @Test
    void oneThousandConcurrentDuplicateRequestsProduceExactlyOneSettlement() throws Exception {
        ExpenseGroup group = ledger.createGroup("Race", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);
        Member bob = group.getMembers().get(1);

        // 1,000 attempts through a bounded pool, not 1,000 raw OS threads — the HikariCP
        // pool backing the datasource is the real ceiling on in-flight requests anyway.
        int attempts = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(64);
        try {
            List<Callable<Settlement>> tasks = IntStream.range(0, attempts)
                    .<Callable<Settlement>>mapToObj(i -> () -> settlements.settle(
                            group.getId(), bob.getId(), alice.getId(), new BigDecimal("15.00"), "race-key"))
                    .toList();

            List<Future<Settlement>> results = pool.invokeAll(tasks);
            UUID firstId = results.get(0).get().getId();
            for (Future<Settlement> result : results) {
                assertThat(result.get().getId()).isEqualTo(firstId);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(ledger.balancesOf(group.getId()))
                .extracting(LedgerService.Balance::amount)
                .containsExactlyInAnyOrder(new BigDecimal("15.00"), new BigDecimal("-15.00"));
        assertThat(ledger.isBalanced(group.getId())).isTrue();
    }

    @Test
    void cannotSettleWithYourself() {
        ExpenseGroup group = ledger.createGroup("Solo", "INR", List.of("Alice"));
        Member alice = group.getMembers().get(0);

        assertThatThrownBy(() -> settlements.settle(group.getId(), alice.getId(), alice.getId(),
                new BigDecimal("10.00"), "k"))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void aNonMemberCannotBeSettledWith() {
        ExpenseGroup group = ledger.createGroup("Closed", "INR", List.of("Alice"));
        Member alice = group.getMembers().get(0);

        assertThatThrownBy(() -> settlements.settle(group.getId(), alice.getId(), UUID.randomUUID(),
                new BigDecimal("10.00"), "k"))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void aNonPositiveSettlementAmountIsRejected() {
        ExpenseGroup group = ledger.createGroup("Zero", "INR", List.of("Alice", "Bob"));
        Member alice = group.getMembers().get(0);
        Member bob = group.getMembers().get(1);

        assertThatThrownBy(() -> settlements.settle(group.getId(), bob.getId(), alice.getId(),
                new BigDecimal("0.00"), "k"))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("positive");
    }
}
