package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a node in the architecture graph represents.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum ArchitectureNodeKind {
    APP("app"),
    MODULE("module"),
    COMPONENT("component"),
    SERVICE("service"),
    STORE("store"),
    API("api"),
    DATASTORE("datastore"),
    EXTERNAL("external");

    private final String wireValue;

    ArchitectureNodeKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ArchitectureNodeKind fromWire(String value) {
        for (ArchitectureNodeKind candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown ArchitectureNodeKind: " + value);
    }
}
