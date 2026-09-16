package com.swayam.ledger.api;

import com.swayam.ledger.api.Dtos.*;
import com.swayam.ledger.domain.Expense;
import com.swayam.ledger.domain.ExpenseGroup;
import com.swayam.ledger.domain.Member;
import com.swayam.ledger.ledger.LedgerService;
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

    public GroupController(LedgerService ledger) {
        this.ledger = ledger;
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
}
