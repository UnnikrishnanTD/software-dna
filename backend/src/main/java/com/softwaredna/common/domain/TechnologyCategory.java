package com.softwaredna.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a technology sits in the stack.
 *
 * The wire value is fixed by the frontend contract and must not drift.
 */
public enum TechnologyCategory {
    FRONTEND("frontend"),
    BACKEND("backend"),
    LANGUAGE("language"),
    DATASTORE("datastore"),
    INFRASTRUCTURE("infrastructure"),
    TOOLING("tooling");

    private final String wireValue;

    TechnologyCategory(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static TechnologyCategory fromWire(String value) {
        for (TechnologyCategory candidate : values()) {
            if (candidate.wireValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown TechnologyCategory: " + value);
    }
}
