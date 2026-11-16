package com.swayam.ledger.ledger;

import com.swayam.ledger.domain.ExpenseGroup;
import com.swayam.ledger.domain.Member;
import com.swayam.ledger.domain.Settlement;
import com.swayam.ledger.repository.ExpenseGroupRepository;
import com.swayam.ledger.repository.MemberRepository;
import com.swayam.ledger.repository.SettlementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Settles a debt between two members behind an Idempotency-Key.
 *
 * The key alone only proves "this is a retry" — it says nothing about whether
 * it is a retry *of this request*. So every settlement also stores a hash of
 * its body, and a key reused with a different from/to/amount is rejected
 * rather than silently replayed.
 *
 * Two duplicate requests can also arrive genuinely concurrently, both missing
 * the cheap existence check below. The actual safety net is the database's
 * unique constraint on the key: {@link SettlementWriter#insert} does the write
 * in its own transaction, and whichever request loses the race catches the
 * constraint violation and replays the winner's row instead of erroring out.
 */
@Service
public class SettlementService {

    private final ExpenseGroupRepository groups;
    private final MemberRepository members;
    private final SettlementRepository settlements;
    private final SettlementWriter writer;

    public SettlementService(ExpenseGroupRepository groups, MemberRepository members,
                             SettlementRepository settlements, SettlementWriter writer) {
        this.groups = groups;
        this.members = members;
        this.settlements = settlements;
        this.writer = writer;
    }

    public Settlement settle(UUID groupId, UUID fromMemberId, UUID toMemberId, BigDecimal amount,
                             String idempotencyKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerException("settlement amount must be positive");
        }
        if (fromMemberId.equals(toMemberId)) {
            throw new LedgerException("cannot settle with yourself");
        }
        ExpenseGroup group = groups.findById(groupId)
                .orElseThrow(() -> new LedgerException("no such group: " + groupId));
        Member from = memberOf(group, fromMemberId);
        Member to = memberOf(group, toMemberId);
        String requestHash = hash(groupId, fromMemberId, toMemberId, amount);

        Optional<Settlement> existing = settlements.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        try {
            return writer.insert(group, from, to, amount, idempotencyKey, requestHash);
        } catch (DataIntegrityViolationException raced) {
            // Another concurrent request won the insert; replay its row instead of failing this one.
            return replayOrConflict(settlements.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> raced), requestHash);
        }
    }

    private Member memberOf(ExpenseGroup group, UUID memberId) {
        return members.findByGroupId(group.getId()).stream()
                .filter(m -> m.getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new LedgerException("not a member of this group: " + memberId));
    }

    private Settlement replayOrConflict(Settlement existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency-Key already used with a different request");
        }
        return existing;
    }

    private static String hash(UUID groupId, UUID fromId, UUID toId, BigDecimal amount) {
        String canonical = groupId + "|" + fromId + "|" + toId + "|"
                + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
