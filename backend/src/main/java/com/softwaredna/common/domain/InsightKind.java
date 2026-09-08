package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Whether a finding is good news, bad news, or neither.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum InsightKind {
    STRENGTH("strength"),
    RISK("risk"),
    OPPORTUNITY("opportunity"),
    OBSERVATION("observation");

    private final String wireValue;

    InsightKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static InsightKind fromWire(String value) {
        for (InsightKind candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown InsightKind: " + value);
    }
}
