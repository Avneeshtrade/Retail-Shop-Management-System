package com.retailshop.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static BigDecimal of(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** amount * percent / 100, rounded to cents. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return of(nz(amount).multiply(nz(percent)).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }
}
