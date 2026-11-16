package com.swayam.ledger.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneySplitterTest {

    @Test
    void theAwkwardCaseFromTheReadme() {
        List<BigDecimal> shares = MoneySplitter.splitEvenly(new BigDecimal("100.00"), 3);

        assertThat(shares).containsExactly(
                new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @CsvSource({
            "100.00, 3", "0.01, 2", "0.05, 3", "10.00, 4", "999.99, 7",
            "1.00, 3", "20.00, 6", "0.10, 3", "123.45, 11", "5000.00, 13"
    })
    void sharesAlwaysSumBackToTheTotal(String total, int ways) {
        assertThat(sum(MoneySplitter.splitEvenly(new BigDecimal(total), ways)))
                .isEqualByComparingTo(total);
    }

    @ParameterizedTest
    @CsvSource({"100.00, 3", "0.05, 3", "999.99, 7", "123.45, 11"})
    void noShareIsMoreThanOneMinorUnitFromAnother(String total, int ways) {
        List<BigDecimal> shares = MoneySplitter.splitEvenly(new BigDecimal(total), ways);
        BigDecimal max = shares.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal min = shares.stream().min(BigDecimal::compareTo).orElseThrow();

        assertThat(max.subtract(min)).isLessThanOrEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void theSameInputAlwaysProducesTheSameRows() {
        assertThat(MoneySplitter.splitEvenly(new BigDecimal("100.00"), 3))
                .isEqualTo(MoneySplitter.splitEvenly(new BigDecimal("100.00"), 3));
    }

    @Test
    void splittingAcrossNobodyIsRejected() {
        assertThatThrownBy(() -> MoneySplitter.splitEvenly(BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
