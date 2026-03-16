package com.revampes.Fault.modules.impl.render.fancydamagesplash;

public final class CompactDamageNumber {
    private static final int DEFAULT_PRECISION = 4;

    private static final int[] DECIMAL_DIGIT_ESTIMATES = new int[]{
            0, 1, 1, 1, 1, 2, 2, 2,
            3, 3, 3, 4, 4, 4, 4, 5,
            5, 5, 6, 6, 6, 7, 7, 7,
            7, 8, 8, 8, 9, 9, 9, 10,
            10, 10, 10, 11, 11, 11, 12, 12,
            12, 13, 13, 13, 13, 14, 14, 14,
            15, 15, 15, 16, 16, 16, 16, 17,
            17, 17, 18, 18, 18, 19, 19, 19,
    };

    private static final long[] TEN_POWERS = new long[]{
            1L, 10L, 100L,
            1000L, 10000L, 100000L,
            1000000L, 10000000L, 100000000L,
            1000000000L, 10000000000L, 100000000000L,
            1000000000000L, 10000000000000L, 100000000000000L,
            1000000000000000L, 10000000000000000L, 100000000000000000L,
            1000000000000000000L,
    };

    private CompactDamageNumber() {
    }

    public static String formatDamage(double damage) {
        return formatDamage(damage, DEFAULT_PRECISION);
    }

    public static String formatDamage(double damage, int maxPrecision) {
        long damageLong = (long) damage;
        return convertToCompactFormat(damageLong, maxPrecision);
    }

    private static String convertToCompactFormat(long damage, int maxPrecision) {
        long adjustedDamage = damage;
        int currentDigits = countDecimalDigits(Math.max(1, adjustedDamage));

        if (currentDigits > maxPrecision) {
            double roundingFactor = TEN_POWERS[currentDigits - maxPrecision];
            adjustedDamage = (long) (Math.round((double) adjustedDamage / roundingFactor) * roundingFactor);
        }

        if (adjustedDamage < 1_000L) return String.valueOf(adjustedDamage);
        if (adjustedDamage < 1_000_000L) return String.format("%.1fk", adjustedDamage / 1_000.0);
        if (adjustedDamage < 1_000_000_000L) return String.format("%.1fM", adjustedDamage / 1_000_000.0);
        if (adjustedDamage < 1_000_000_000_000L) return String.format("%.1fB", adjustedDamage / 1_000_000_000.0);
        if (adjustedDamage < 1_000_000_000_000_000L) return String.format("%.1fT", adjustedDamage / 1_000_000_000_000.0);
        return String.format("%.1fQ", adjustedDamage / 1_000_000_000_000_000.0);
    }

    private static int countBinaryDigits(long value) {
        return 64 - Long.numberOfLeadingZeros(value);
    }

    private static int countDecimalDigits(long value) {
        int estimate = DECIMAL_DIGIT_ESTIMATES[countBinaryDigits(value)];
        return estimate + ((value >= TEN_POWERS[estimate]) ? 1 : 0);
    }
}