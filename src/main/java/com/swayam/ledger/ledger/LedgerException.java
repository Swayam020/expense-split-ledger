package com.swayam.ledger.ledger;

/** Thrown when a request would violate a ledger invariant. */
public class LedgerException extends RuntimeException {

    public LedgerException(String message) {
        super(message);
    }
}
