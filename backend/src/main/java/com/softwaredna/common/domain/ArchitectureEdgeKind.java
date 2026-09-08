package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The nature of a dependency between two nodes.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum ArchitectureEdgeKind {
    IMPORTS("imports"),
    CALLS("calls"),
    IMPLEMENTS("implements"),
    READS("reads");

    private final String wireValue;

    ArchitectureEdgeKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ArchitectureEdgeKind fromWire(String value) {
        for (ArchitectureEdgeKind candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown ArchitectureEdgeKind: " + value);
    }
}
