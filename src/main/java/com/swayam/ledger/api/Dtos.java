package com.swayam.ledger.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response shapes for the API.
 *
 * Entities are never serialised directly: a JPA entity on the wire leaks the
 * schema, drags lazy associations into the JSON, and welds the public contract
 * to the database. These records are the contract instead.
 */
public final class Dtos {

    private Dtos() {
    }

    public record CreateGroupRequest(
            @NotBlank String name,
            @NotBlank @Size(min = 3, max = 3, message = "must be a 3-letter ISO-4217 code") String currency,
            @NotEmpty List<@NotBlank String> members) {
    }

    public record CreateExpenseRequest(
            @NotNull UUID payerId,
            @NotBlank String description,
            @NotNull
            @DecimalMin(value = "0.00", inclusive = false, message = "must be positive")
            @Digits(integer = 15, fraction = 2) BigDecimal amount,
            List<UUID> participantIds,
            Instant incurredAt) {
    }

    public record MemberResponse(UUID id, String name) {
    }

    public record GroupResponse(UUID id, String name, String currency, List<MemberResponse> members) {
    }

    public record ShareResponse(UUID memberId, String memberName, BigDecimal amount) {
    }

    public record ExpenseResponse(UUID id, String description, BigDecimal amount, UUID payerId,
                                  Instant incurredAt, List<ShareResponse> shares) {
    }

    public record BalanceResponse(UUID memberId, String memberName, BigDecimal amount) {
    }

    public record GroupBalancesResponse(UUID groupId, boolean balanced, List<BalanceResponse> balances) {
    }

    public record ApiError(String error, String message, Instant timestamp) {
    }
}
