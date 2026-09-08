package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Coarse banding of a 0-100 score.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum HealthVerdict {
    CRITICAL("critical"),
    AT_RISK("at-risk"),
    FAIR("fair"),
    HEALTHY("healthy"),
    EXEMPLARY("exemplary");

    private final String wireValue;

    HealthVerdict(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static HealthVerdict fromWire(String value) {
        for (HealthVerdict candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown HealthVerdict: " + value);
    }

    /**
     * Bands a score using the same thresholds the frontend renders with, so
     * the label and the colour can never disagree.
     */
    public static HealthVerdict forScore(double score) {
        if (score >= 90) return EXEMPLARY;
        if (score >= 78) return HEALTHY;
        if (score >= 62) return FAIR;
        if (score >= 45) return AT_RISK;
        return CRITICAL;
    }
}
