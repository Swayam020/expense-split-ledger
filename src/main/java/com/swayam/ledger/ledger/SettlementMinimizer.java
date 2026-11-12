package com.swayam.ledger.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Turns a group's net balances into the payments that would clear them.
 */
public final class SettlementMinimizer {

    /** A suggested payment: {@code fromMemberId} pays {@code amount} to {@code toMemberId}. */
    public record Transfer(UUID fromMemberId, String fromMemberName,
                           UUID toMemberId, String toMemberName,
                           BigDecimal amount) {
    }

    private SettlementMinimizer() {
    }

    /**
     * Pairs the largest creditor with the largest debtor until nobody is owed or owing.
     *
     * Every pairing zeroes at least one party, so only the final two can be zeroed by
     * the same transfer: for N non-zero starting balances this emits at most N - 1
     * transfers. That is a bound, not an optimum — the true minimum is the NP-hard
     * optimum account balancing problem, and a group is not going to wait on an
     * exponential search to save itself one payment.
     *
     * Balances that are already exactly zero are dropped, so an even member appears in
     * no transfer either side.
     */
    public static List<Transfer> minimize(List<LedgerService.Balance> balances) {
        List<Running> creditors = new ArrayList<>();
        List<Running> debtors = new ArrayList<>();
        for (LedgerService.Balance balance : balances) {
            int sign = balance.amount().signum();
            if (sign > 0) {
                creditors.add(new Running(balance, balance.amount()));
            } else if (sign < 0) {
                debtors.add(new Running(balance, balance.amount().negate()));
            }
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Running creditor = largest(creditors);
            Running debtor = largest(debtors);
            BigDecimal paired = creditor.remaining.min(debtor.remaining);
            transfers.add(new Transfer(debtor.balance.memberId(), debtor.balance.memberName(),
                    creditor.balance.memberId(), creditor.balance.memberName(),
                    paired.setScale(MoneySplitter.SCALE, RoundingMode.HALF_UP)));
            // The exact pairing, not the rounded one, settles the books: rounding up here
            // would leave a party owing a fraction of a paisa that can never be zeroed.
            creditor.remaining = creditor.remaining.subtract(paired);
            debtor.remaining = debtor.remaining.subtract(paired);
            // compareTo, not equals: zero at any scale is done.
            if (creditor.remaining.compareTo(BigDecimal.ZERO) == 0) {
                creditors.remove(creditor);
            }
            if (debtor.remaining.compareTo(BigDecimal.ZERO) == 0) {
                debtors.remove(debtor);
            }
        }
        return transfers;
    }

    private static Running largest(List<Running> parties) {
        return parties.stream().max(Comparator.comparing((Running p) -> p.remaining)).orElseThrow();
    }

    private static final class Running {
        private final LedgerService.Balance balance;
        private BigDecimal remaining;

        private Running(LedgerService.Balance balance, BigDecimal remaining) {
            this.balance = balance;
            this.remaining = remaining;
        }
    }
}
