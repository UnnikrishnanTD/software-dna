package com.softwaredna.dependency.model;

import com.softwaredna.common.domain.DependencyEcosystem;
import com.softwaredna.common.domain.DependencyStatus;
import com.softwaredna.common.domain.RiskLevel;

/**
 * One dependency, exactly as the manifest declares it.
 *
 * <p>{@code latestVersion} and {@code advisoryCount} are nullable on purpose.
 * Null means "not checked", which is a different claim from "up to date" or
 * "no known vulnerabilities". Nothing in the system may collapse the two: a
 * dependency that was never checked against a registry or a vulnerability
 * database must not be presented as clean.
 */
public record ResolvedDependency(
        String name,
        DependencyEcosystem ecosystem,
        String version,
        String latestVersion,
        DependencyStatus status,
        String license,
        boolean direct,
        String scope,
        Integer advisoryCount,
        String manifestPath,
        String note
) {

    /**
     * A dependency read from a manifest with no registry lookup performed.
     * Status is UNKNOWN because nothing has been compared against anything.
     */
    public static ResolvedDependency declared(String name, DependencyEcosystem ecosystem,
                                              String version, boolean direct,
                                              String scope, String manifestPath) {
        return new ResolvedDependency(name, ecosystem, version, null,
                DependencyStatus.UNKNOWN, null, direct, scope, null, manifestPath, null);
    }

    public ResolvedDependency withUsage(String note) {
        return new ResolvedDependency(name, ecosystem, version, latestVersion, status,
                license, direct, scope, advisoryCount, manifestPath, note);
    }

    /** Risk from what is actually known, never from an assumed clean bill. */
    public RiskLevel risk() {
        if (advisoryCount != null && advisoryCount > 0) {
            return status == DependencyStatus.MAJOR_BEHIND ? RiskLevel.CRITICAL : RiskLevel.HIGH;
        }
        return switch (status) {
            case DEPRECATED -> RiskLevel.HIGH;
            case MAJOR_BEHIND -> RiskLevel.MEDIUM;
            // UNKNOWN is not a risk finding; it is an absence of evidence.
            case MINOR_BEHIND, CURRENT, UNKNOWN -> RiskLevel.LOW;
        };
    }
}
