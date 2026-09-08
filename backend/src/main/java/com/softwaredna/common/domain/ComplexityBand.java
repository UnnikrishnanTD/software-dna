package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Cyclomatic complexity banding.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum ComplexityBand {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    VERY_HIGH("very-high");

    private final String wireValue;

    ComplexityBand(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ComplexityBand fromWire(String value) {
        for (ComplexityBand candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown ComplexityBand: " + value);
    }

    public static ComplexityBand forScore(int complexityScore) {
        if (complexityScore >= 28) return VERY_HIGH;
        if (complexityScore >= 16) return HIGH;
        if (complexityScore >= 8) return MEDIUM;
        return LOW;
    }
}
