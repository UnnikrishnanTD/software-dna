package com.softwaredna.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on the two endpoints that cost real compute: starting an analysis
 * (clones a repository and runs static analysis over it) and asking the
 * Doctor a question. Both are keyed by client IP.
 *
 * <p>The defaults are generous for a genuine developer trying the product and
 * restrictive for a script hammering the endpoint: five analyses and thirty
 * questions per IP per hour.
 */
@ConfigurationProperties(prefix = "softwaredna.rate-limit")
public record RateLimitProperties(
        int analysesPerHour,
        int questionsPerHour
) {
    public RateLimitProperties {
        if (analysesPerHour <= 0) analysesPerHour = 5;
        if (questionsPerHour <= 0) questionsPerHour = 30;
    }
}
