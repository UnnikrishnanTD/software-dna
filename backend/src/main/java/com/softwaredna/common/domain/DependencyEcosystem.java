package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Package manager a dependency comes from.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum DependencyEcosystem {
    NPM("npm"),
    MAVEN("maven"),
    DOCKER("docker"),
    SYSTEM("system");

    private final String wireValue;

    DependencyEcosystem(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static DependencyEcosystem fromWire(String value) {
        for (DependencyEcosystem candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown DependencyEcosystem: " + value);
    }
}
