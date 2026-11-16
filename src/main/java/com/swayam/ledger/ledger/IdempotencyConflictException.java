package com.swayam.ledger.ledger;

/** The same Idempotency-Key was sent with a different request body. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
