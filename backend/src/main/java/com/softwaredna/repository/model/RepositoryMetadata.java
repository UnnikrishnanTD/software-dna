package com.softwaredna.repository.model;

import java.time.Instant;

/**
 * Repository facts fetched from the provider, as opposed to anything derived
 * from the source itself.
 */
public record RepositoryMetadata(
        RepositoryCoordinates coordinates,
        String description,
        String defaultBranch,
        String primaryLanguage,
        int stars,
        int forks,
        long sizeKilobytes,
        boolean archived,
        boolean isPrivate,
        Instant createdAt,
        Instant pushedAt
) {
}
