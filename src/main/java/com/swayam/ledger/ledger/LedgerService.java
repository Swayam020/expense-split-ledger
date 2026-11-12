package com.swayam.ledger.ledger;

import com.swayam.ledger.domain.*;
import com.swayam.ledger.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Posts expenses to the ledger and derives balances from it.
 *
 * Posting an expense is one transaction: the expense, its shares and its ledger
 * entries are written together or not at all. A half-posted expense would leave
 * the books unbalanced, and there is no balance column to repair afterwards.
 */
@Service
public class LedgerService {

    private final ExpenseGroupRepository groups;
    private final MemberRepository members;
    private final ExpenseRepository expenses;
    private final LedgerEntryRepository entries;

    public LedgerService(ExpenseGroupRepository groups, MemberRepository members,
                         ExpenseRepository expenses, LedgerEntryRepository entries) {
        this.groups = groups;
        this.members = members;
        this.expenses = expenses;
        this.entries = entries;
    }

    @Transactional
    public ExpenseGroup createGroup(String name, String currency, List<String> memberNames) {
        if (memberNames == null || memberNames.isEmpty()) {
            throw new LedgerException("a group needs at least one member");
        }
        if (new HashSet<>(memberNames).size() != memberNames.size()) {
            throw new LedgerException("member names must be unique within a group");
        }
        ExpenseGroup group = new ExpenseGroup(name, currency);
        memberNames.forEach(group::addMember);
        return groups.save(group);
    }

    /**
     * Records an expense split equally between the given members.
     *
     * Writes one credit to the payer for the full amount and one debit to each
     * participant for their share. Credits and debits are equal by construction,
     * so the group's entries still sum to zero afterwards.
     */
    @Transactional
    public Expense postEqualSplitExpense(UUID groupId, UUID payerId, String description,
                                         BigDecimal amount, List<UUID> participantIds, Instant incurredAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerException("expense amount must be positive");
        }
        // stripTrailingZeros first so 100.500 passes: extra scale with no extra
        // precision is not the error being guarded against.
        if (amount.stripTrailingZeros().scale() > MoneySplitter.SCALE) {
            throw new LedgerException("expense amount must have at most " + MoneySplitter.SCALE + " decimal places");
        }
        ExpenseGroup group = groups.findById(groupId)
                .orElseThrow(() -> new LedgerException("no such group: " + groupId));

        Map<UUID, Member> byId = new LinkedHashMap<>();
        members.findByGroupId(groupId).forEach(m -> byId.put(m.getId(), m));

        Member payer = byId.get(payerId);
        if (payer == null) {
            throw new LedgerException("payer is not a member of this group");
        }
        List<UUID> participants = (participantIds == null || participantIds.isEmpty())
                ? new ArrayList<>(byId.keySet())
                : participantIds;
        for (UUID id : participants) {
            if (!byId.containsKey(id)) {
                throw new LedgerException("participant is not a member of this group: " + id);
            }
        }

        Expense expense = new Expense(group, payer, description, amount,
                incurredAt == null ? Instant.now() : incurredAt);

        List<BigDecimal> shares = MoneySplitter.splitEvenly(amount, participants.size());
        for (int i = 0; i < participants.size(); i++) {
            expense.addShare(byId.get(participants.get(i)), shares.get(i));
        }
        if (!expense.sharesReconcile()) {
            throw new LedgerException("shares do not sum to the expense amount");
        }
        Expense saved = expenses.save(expense);

        List<LedgerEntry> postings = new ArrayList<>();
        postings.add(LedgerEntry.credit(group, payer, saved, amount));
        for (ExpenseShare share : saved.getShares()) {
            postings.add(LedgerEntry.debit(group, share.getMember(), saved, share.getAmount()));
        }
        entries.saveAll(postings);
        return saved;
    }

    /** Net position per member, summed from the ledger. Positive means owed. */
    @Transactional(readOnly = true)
    public List<Balance> balancesOf(UUID groupId) {
        if (!groups.existsById(groupId)) {
            throw new LedgerException("no such group: " + groupId);
        }
        return entries.findBalancesByGroupId(groupId).stream()
                .map(row -> new Balance((UUID) row[0], (String) row[1],
                        ((BigDecimal) row[2]).setScale(MoneySplitter.SCALE, java.math.RoundingMode.HALF_UP)))
                .toList();
    }

    /** True when the group's entries sum to zero, as they always should. */
    @Transactional(readOnly = true)
    public boolean isBalanced(UUID groupId) {
        BigDecimal sum = entries.sumAmountsByGroupId(groupId);
        return sum == null || sum.compareTo(BigDecimal.ZERO) == 0;
    }

    public record Balance(UUID memberId, String memberName, BigDecimal amount) {
    }
}
