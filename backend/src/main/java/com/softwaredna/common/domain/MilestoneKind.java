package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Category of a notable event on the evolution timeline.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum MilestoneKind {
    ARCHITECTURE("architecture"),
    TECHNOLOGY("technology"),
    SCALE("scale"),
    QUALITY("quality"),
    INCIDENT("incident");

    private final String wireValue;

    MilestoneKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static MilestoneKind fromWire(String value) {
        for (MilestoneKind candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown MilestoneKind: " + value);
    }
}
