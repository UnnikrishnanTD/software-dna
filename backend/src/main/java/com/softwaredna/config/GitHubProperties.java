
package com.softwaredna.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * GitHub access configuration.
 *
 * The token is read from the environment and never leaves the server: it is
 * not returned by any endpoint, not written to logs, and not included in
 * error responses.
 */
@Validated
@ConfigurationProperties(prefix = "softwaredna.github")
public record GitHubProperties(
        @NotBlank String apiUrl,
        String token,
        Duration requestTimeout
) {
    public GitHubProperties {
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(20);
        }
        if (token == null) {
            token = "";
        }
    }

    public boolean hasToken() {
        return !token.isBlank();
    }

    /** Never include the token itself in any string representation. */
    @Override
    public String toString() {
        return "GitHubProperties[apiUrl=%s, token=%s, requestTimeout=%s]"
                .formatted(apiUrl, hasToken() ? "<set>" : "<absent>", requestTimeout);
    }
}
