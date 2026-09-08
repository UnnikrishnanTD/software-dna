package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How dangerous a unit is to change.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum RiskLevel {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String wireValue;

    RiskLevel(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static RiskLevel fromWire(String value) {
        for (RiskLevel candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown RiskLevel: " + value);
    }

    /** Bands a 0-100 composite risk score. */
    public static RiskLevel forScore(double riskScore) {
        if (riskScore >= 72) return CRITICAL;
        if (riskScore >= 55) return HIGH;
        if (riskScore >= 35) return MEDIUM;
        return LOW;
    }
}
