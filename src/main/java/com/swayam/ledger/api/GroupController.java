package com.swayam.ledger.api;

import com.swayam.ledger.api.Dtos.*;
import com.swayam.ledger.domain.Expense;
import com.swayam.ledger.domain.ExpenseGroup;
import com.swayam.ledger.domain.Member;
import com.swayam.ledger.domain.Settlement;
import com.swayam.ledger.ledger.LedgerService;
import com.swayam.ledger.ledger.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final LedgerService ledger;
    private final SettlementService settlements;

    public GroupController(LedgerService ledger, SettlementService settlements) {
        this.ledger = ledger;
        this.settlements = settlements;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                     UriComponentsBuilder uri) {
        ExpenseGroup group = ledger.createGroup(request.name(), request.currency(), request.members());
        URI location = uri.path("/api/groups/{id}").buildAndExpand(group.getId()).toUri();
        return ResponseEntity.created(location).body(toResponse(group));
    }

    @PostMapping("/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse addExpense(@PathVariable UUID groupId,
                                      @Valid @RequestBody CreateExpenseRequest request) {
        Expense expense = ledger.postEqualSplitExpense(groupId, request.payerId(), request.description(),
                request.amount(), request.participantIds(), request.incurredAt());
        return toResponse(expense);
    }

    @GetMapping("/{groupId}/balances")
    public GroupBalancesResponse balances(@PathVariable UUID groupId) {
        List<BalanceResponse> balances = ledger.balancesOf(groupId).stream()
                .map(b -> new BalanceResponse(b.memberId(), b.memberName(), b.amount()))
                .toList();
        return new GroupBalancesResponse(groupId, ledger.isBalanced(groupId), balances);
    }

    @PostMapping("/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse settle(@PathVariable UUID groupId,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody CreateSettlementRequest request) {
        Settlement settlement = settlements.settle(groupId, request.fromMemberId(), request.toMemberId(),
                request.amount(), idempotencyKey);
        return toResponse(settlement);
    }

    /**
     * Read-only suggestion: nothing is posted here. A client that wants to act on a
     * transfer calls the idempotent {@code POST /{groupId}/settlements} for it.
     */
    @GetMapping("/{groupId}/settlements/suggested")
    public List<SuggestedTransferResponse> suggestedSettlements(@PathVariable UUID groupId) {
        return ledger.suggestedSettlements(groupId).stream()
                .map(t -> new SuggestedTransferResponse(t.fromMemberId(), t.fromMemberName(),
                        t.toMemberId(), t.toMemberName(), t.amount()))
                .toList();
    }

    private static GroupResponse toResponse(ExpenseGroup group) {
        List<MemberResponse> members = group.getMembers().stream()
                .map(m -> new MemberResponse(m.getId(), m.getName()))
                .toList();
        return new GroupResponse(group.getId(), group.getName(), group.getCurrency(), members);
    }

    private static ExpenseResponse toResponse(Expense expense) {
        List<ShareResponse> shares = expense.getShares().stream()
                .map(s -> new ShareResponse(s.getMember().getId(), s.getMember().getName(), s.getAmount()))
                .toList();
        return new ExpenseResponse(expense.getId(), expense.getDescription(), expense.getAmount(),
                expense.getPayer().getId(), expense.getIncurredAt(), shares);
    }

    private static SettlementResponse toResponse(Settlement settlement) {
        return new SettlementResponse(settlement.getId(), settlement.getGroup().getId(),
                settlement.getFromMember().getId(), settlement.getToMember().getId(),
                settlement.getAmount(), settlement.getCreatedAt());
    }
}
