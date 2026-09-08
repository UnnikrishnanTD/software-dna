package com.softwaredna.analysis.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a score by subtracting explained penalties from a perfect start.
 *
 * <p>Every deduction carries the reason and the measured value that caused it,
 * so a score is never a bare number: it is an audit trail. A module that
 * cannot explain a deduction cannot make one.
 */
public final class ScoreCard {

    private double score = 100;
    private final List<String> strengths = new ArrayList<>();
    private final List<String> watchItems = new ArrayList<>();
    private final List<Measurement> measurements = new ArrayList<>();
    private final Map<String, String> evidence = new LinkedHashMap<>();
    private double confidence = 100;

    /** Records a measurement without affecting the score. */
    public ScoreCard measure(Measurement measurement) {
        measurements.add(measurement);
        if (!measurement.isAvailable() && measurement.unavailableReason() != null) {
            evidence.put(measurement.key(), "unavailable: " + measurement.unavailableReason());
        } else if (measurement.isAvailable()) {
            evidence.put(measurement.key(), formatValue(measurement));
        }
        return this;
    }

    /**
     * Deducts points and records why.
     *
     * @param points  how much to deduct, clamped at the cap
     * @param cap     the most this single factor may ever remove, so no one
     *                signal can dominate the whole dimension
     * @param watch   the finding shown to the reader, or null for a deduction
     *                that is real but not worth surfacing on its own
     */
    public ScoreCard penalise(double points, double cap, String watch) {
        double applied = Math.min(Math.max(points, 0), cap);
        if (applied > 0) {
            score -= applied;
            if (watch != null) {
                watchItems.add(watch);
            }
        }
        return this;
    }

    /** Records something the repository does well. */
    public ScoreCard commend(String strength) {
        strengths.add(strength);
        return this;
    }

    /**
     * Lowers confidence because an input was missing.
     *
     * <p>Confidence and score are separate on purpose: a repository can score
     * well on the evidence available while that evidence is thin.
     */
    public ScoreCard reduceConfidence(double points, String reason) {
        confidence = Math.max(0, confidence - points);
        evidence.put("confidence:" + reason, "-" + (int) points);
        return this;
    }

    public double score() {
        return Math.max(0, Math.min(100, score));
    }

    public double confidence() {
        return Math.max(0, Math.min(100, confidence));
    }

    public List<String> strengths() {
        return List.copyOf(strengths);
    }

    public List<String> watchItems() {
        return List.copyOf(watchItems);
    }

    public List<Measurement> measurements() {
        return List.copyOf(measurements);
    }

    public Map<String, String> evidence() {
        return Map.copyOf(evidence);
    }

    private static String formatValue(Measurement measurement) {
        double value = measurement.value();
        String number = value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format("%.2f", value);
        return measurement.unit() == null ? number : number + " " + measurement.unit();
    }

    /** Linear penalty: nothing below {@code good}, full {@code cap} at {@code bad}. */
    public static double ramp(double value, double good, double bad, double cap) {
        if (Double.isNaN(value)) return 0;
        if (value <= good) return 0;
        if (value >= bad) return cap;
        return cap * ((value - good) / (bad - good));
    }

    /** Linear penalty for values that should be high rather than low. */
    public static double inverseRamp(double value, double good, double bad, double cap) {
        if (Double.isNaN(value)) return 0;
        if (value >= good) return 0;
        if (value <= bad) return cap;
        return cap * ((good - value) / (good - bad));
    }
}
