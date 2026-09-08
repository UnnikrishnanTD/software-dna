package com.softwaredna.analysis.engine;

/**
 * One measured value, or an explicit record that it could not be measured.
 *
 * <p>This type exists so that "not measured" can never decay into zero. A
 * repository with no coverage report and a repository with genuinely zero
 * coverage are different findings, and a scoring engine that cannot tell them
 * apart will confidently report the wrong one.
 */
public record Measurement(
        String key,
        Double value,
        String unit,
        boolean available,
        String unavailableReason
) {

    public static Measurement of(String key, double value, String unit) {
        return new Measurement(key, value, unit, true, null);
    }

    public static Measurement of(String key, double value) {
        return new Measurement(key, value, null, true, null);
    }

    /**
     * Records that a measurement was attempted and could not be made.
     *
     * @param reason shown to the reader, so an absent number is explained
     *               rather than silently missing
     */
    public static Measurement unavailable(String key, String reason) {
        return new Measurement(key, null, null, false, reason);
    }

    public double orElse(double fallback) {
        return available && value != null ? value : fallback;
    }

    public boolean isAvailable() {
        return available && value != null;
    }
}
