package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Rough cost of acting on a recommendation.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum Effort {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String wireValue;

    Effort(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static Effort fromWire(String value) {
        for (Effort candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown Effort: " + value);
    }
}
