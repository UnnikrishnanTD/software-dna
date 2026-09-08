package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How far behind a dependency is.
 *
 * {@code CURRENT} is only ever assigned when a registry lookup actually
 * confirmed the latest version. Without a lookup the status is UNKNOWN,
 * because "not checked" and "up to date" are different claims.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum DependencyStatus {
    CURRENT("current"),
    MINOR_BEHIND("minor-behind"),
    MAJOR_BEHIND("major-behind"),
    DEPRECATED("deprecated"),
    UNKNOWN("unknown");

    private final String wireValue;

    DependencyStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static DependencyStatus fromWire(String value) {
        for (DependencyStatus candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown DependencyStatus: " + value);
    }
}
