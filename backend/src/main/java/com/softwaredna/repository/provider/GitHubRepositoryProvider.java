package com.softwaredna.repository.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.config.GitHubProperties;
import com.softwaredna.repository.model.RepositoryCoordinates;
import com.softwaredna.repository.model.RepositoryMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Reads repository metadata from the GitHub REST API.
 *
 * <p>The access token, when configured, is attached here and nowhere else. It
 * is never returned in a response, never written to a log line, and never
 * included in a clone URL that might be persisted.
 *
 * <p>Only the two path segments produced by {@link RepositoryCoordinates} are
 * interpolated into the request URI, and both have already been constrained to
 * GitHub's own character set, so the URI cannot be redirected elsewhere.
 */
@Component
public class GitHubRepositoryProvider implements RepositoryProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryProvider.class);

    private final RestClient client;
    private final boolean authenticated;

    public GitHubRepositoryProvider(RestClient.Builder builder, GitHubProperties properties) {
        this.authenticated = properties.hasToken();
        RestClient.Builder configured = builder
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "software-dna-analyzer");

        if (authenticated) {
            configured = configured.defaultHeader(HttpHeaders.AUTHORIZATION,
                    "Bearer " + properties.token());
        } else {
            log.info("No GITHUB_TOKEN configured; using the unauthenticated API "
                    + "(60 requests/hour). Set GITHUB_TOKEN to raise the limit.");
        }
        this.client = configured.build();
    }

    @Override
    public String providerKey() {
        return RepositoryCoordinates.GITHUB;
    }

    @Override
    @Cacheable(cacheNames = "repositoryMetadata", key = "#coordinates.key()")
    public RepositoryMetadata fetchMetadata(RepositoryCoordinates coordinates) {
        log.debug("Fetching GitHub metadata for {}", coordinates.fullName());

        JsonNode body;
        try {
            body = client.get()
                    .uri("/repos/{owner}/{name}", coordinates.owner(), coordinates.name())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw translate(response.getStatusCode(),
                                response.getHeaders(), coordinates);
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, timeout. The cause may contain a
            // host name but never a credential.
            log.warn("GitHub unreachable while fetching {}: {}",
                    coordinates.fullName(), e.getMessage());
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    ErrorCode.PROVIDER_UNAVAILABLE.defaultMessage(), e);
        }

        if (body == null) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "GitHub returned an empty response.");
        }

        return new RepositoryMetadata(
                coordinates,
                text(body, "description"),
                textOr(body, "default_branch", "main"),
                text(body, "language"),
                body.path("stargazers_count").asInt(0),
                body.path("forks_count").asInt(0),
                body.path("size").asLong(0),
                body.path("archived").asBoolean(false),
                body.path("private").asBoolean(false),
                instant(body, "created_at"),
                instant(body, "pushed_at"));
    }

    @Override
    public String cloneUrl(RepositoryCoordinates coordinates) {
        // Always the public HTTPS URL. Embedding a token here would persist it
        // into git config inside the workspace.
        return coordinates.cloneUrl();
    }

    private ApiException translate(HttpStatusCode status, HttpHeaders headers,
                                   RepositoryCoordinates coordinates) {
        int code = status.value();

        if (code == 404) {
            return new ApiException(ErrorCode.REPOSITORY_NOT_FOUND,
                    "No repository found at " + coordinates.fullName() + ".");
        }
        if (code == 401) {
            return new ApiException(ErrorCode.REPOSITORY_ACCESS_DENIED,
                    "The configured GitHub credentials were rejected.");
        }
        if (code == 403 || code == 429) {
            // GitHub signals rate limiting with 403 plus a zero remaining
            // count, and with 429 for secondary limits.
            String remaining = headers.getFirst("x-ratelimit-remaining");
            if (code == 429 || "0".equals(remaining)) {
                return new ApiException(ErrorCode.PROVIDER_RATE_LIMITED,
                        authenticated
                                ? "The GitHub API rate limit has been reached. Try again later."
                                : "The unauthenticated GitHub rate limit has been reached. "
                                        + "Configure GITHUB_TOKEN to raise it.");
            }
            return new ApiException(ErrorCode.REPOSITORY_ACCESS_DENIED,
                    "Access to " + coordinates.fullName() + " was refused.");
        }
        if (code >= 500) {
            return new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "GitHub reported an error. Try again later.");
        }
        return new ApiException(ErrorCode.PROVIDER_UNAVAILABLE,
                "GitHub responded with an unexpected status.");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Exposed for the health indicator. */
    public boolean isAuthenticated() {
        return authenticated;
    }

    static Duration defaultTimeout() {
        return Duration.ofSeconds(20);
    }
}
