package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The eight stages of a scan.
 *
 * These ids are fixed by the frontend, which renders one row per stage. The
 * backend pipeline is modelled directly on them rather than translating from
 * a separate internal vocabulary.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum AnalysisStageId {
    CONNECT("connect"),
    TECHNOLOGIES("technologies"),
    ARCHITECTURE("architecture"),
    DEPENDENCIES("dependencies"),
    COMPLEXITY("complexity"),
    TESTS("tests"),
    HISTORY("history"),
    SYNTHESIS("synthesis");

    private final String wireValue;

    AnalysisStageId(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static AnalysisStageId fromWire(String value) {
        for (AnalysisStageId candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown AnalysisStageId: " + value);
    }
}
