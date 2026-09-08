package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle of a single scan stage.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum StageStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETE("complete"),
    FAILED("failed");

    private final String wireValue;

    StageStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static StageStatus fromWire(String value) {
        for (StageStatus candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown StageStatus: " + value);
    }
}
