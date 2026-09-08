
package com.softwaredna.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Hard ceilings applied to every analysis.
 *
 * A repository is untrusted input. These bounds are what stop a hostile or
 * merely enormous repository from exhausting disk, memory or CPU — see the
 * checks in the clone and scan stages, each of which fails the analysis with
 * a specific error code rather than degrading silently.
 */
@ConfigurationProperties(prefix = "softwaredna.limits")
public record AnalysisLimits(
        long maxRepositoryBytes,
        long maxFileBytes,
        int maxFilesScanned,
        int maxCommitsScanned,
        Duration cloneTimeout,
        int maxGraphNodes
) {
    public AnalysisLimits {
        if (maxRepositoryBytes <= 0) maxRepositoryBytes = 500L * 1024 * 1024;
        if (maxFileBytes <= 0) maxFileBytes = 2L * 1024 * 1024;
        if (maxFilesScanned <= 0) maxFilesScanned = 25_000;
        if (maxCommitsScanned <= 0) maxCommitsScanned = 20_000;
        if (cloneTimeout == null) cloneTimeout = Duration.ofMinutes(5);
        if (maxGraphNodes <= 0) maxGraphNodes = 300;
    }
}
