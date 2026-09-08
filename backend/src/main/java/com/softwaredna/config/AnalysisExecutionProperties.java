
package com.softwaredna.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Sizing for the pool that runs analyses in the background. */
@ConfigurationProperties(prefix = "softwaredna.analysis")
public record AnalysisExecutionProperties(
        int workerThreads,
        int queueCapacity
) {
    public AnalysisExecutionProperties {
        if (workerThreads <= 0) workerThreads = 2;
        if (queueCapacity <= 0) queueCapacity = 50;
    }
}
