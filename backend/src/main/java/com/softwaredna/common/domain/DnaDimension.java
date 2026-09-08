package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The eight dimensions of a Software DNA profile.
 *
 * The engine runs more analysis modules than this, but every module folds
 * its measurements into one of these eight; the frontend renders exactly
 * these keys and no others.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum DnaDimension {
    ARCHITECTURE("architecture"),
    MAINTAINABILITY("maintainability"),
    SECURITY("security"),
    PERFORMANCE("performance"),
    TESTING("testing"),
    DEPENDENCIES("dependencies"),
    DOCUMENTATION("documentation"),
    EVOLUTION("evolution");

    private final String wireValue;

    DnaDimension(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static DnaDimension fromWire(String value) {
        for (DnaDimension candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown DnaDimension: " + value);
    }

    /** Display label used in the UI. */
    public String label() {
        String lower = wireValue();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
