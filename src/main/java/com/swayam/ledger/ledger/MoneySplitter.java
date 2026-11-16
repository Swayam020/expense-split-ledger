package com.swayam.ledger.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an amount into N shares that sum back to exactly the original.
 *
 * ₹100 three ways is ₹33.33 x 3 = ₹99.99. The stray paisa cannot be dropped
 * (the ledger stops balancing) and cannot be left to rounding (the same input
 * would produce different rows on different runs).
 *
 * The rule, stated once so it can be relied on: divide down to the currency's
 * minor unit, then hand the leftover minor units out one each, in order, from
 * the first share. It is deterministic, and it never distributes more than one
 * extra unit to any share.
 */
public final class MoneySplitter {

    /** Minor-unit scale. 2 for INR/USD-style currencies. */
    public static final int SCALE = 2;

    private MoneySplitter() {
    }

    public static List<BigDecimal> splitEvenly(BigDecimal total, int ways) {
        if (ways <= 0) {
            throw new IllegalArgumentException("cannot split across " + ways + " ways");
        }
        BigDecimal amount = total.setScale(SCALE, RoundingMode.HALF_UP);

        // Work in whole minor units so the arithmetic is exact integer division.
        BigDecimal minorUnits = amount.movePointRight(SCALE);
        BigDecimal[] divideAndRemainder = minorUnits.divideAndRemainder(BigDecimal.valueOf(ways));
        long base = divideAndRemainder[0].longValueExact();
        long leftover = divideAndRemainder[1].longValueExact();

        List<BigDecimal> shares = new ArrayList<>(ways);
        for (int i = 0; i < ways; i++) {
            long units = base + (i < leftover ? 1 : 0);
            shares.add(BigDecimal.valueOf(units).movePointLeft(SCALE).setScale(SCALE, RoundingMode.UNNECESSARY));
        }
        return shares;
    }
}
