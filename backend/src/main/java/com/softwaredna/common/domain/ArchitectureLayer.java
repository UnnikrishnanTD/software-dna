package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Horizontal band a node sits in; drives the layered layout.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum ArchitectureLayer {
    PRESENTATION("presentation"),
    APPLICATION("application"),
    DOMAIN("domain"),
    INFRASTRUCTURE("infrastructure"),
    EXTERNAL("external");

    private final String wireValue;

    ArchitectureLayer(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ArchitectureLayer fromWire(String value) {
        for (ArchitectureLayer candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown ArchitectureLayer: " + value);
    }
}
