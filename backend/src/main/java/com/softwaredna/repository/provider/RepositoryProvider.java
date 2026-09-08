package com.softwaredna.repository.provider;

import com.softwaredna.repository.model.RepositoryCoordinates;
import com.softwaredna.repository.model.RepositoryMetadata;

/**
 * A source of repositories.
 *
 * The analysis pipeline depends only on this interface, so adding GitLab or
 * Bitbucket later is an additional implementation rather than a change to the
 * engine. Provider-specific concepts must not leak past this boundary.
 */
public interface RepositoryProvider {

    /** The provider key this implementation serves, e.g. {@code github}. */
    String providerKey();

    /**
     * Fetches repository metadata.
     *
     * @throws com.softwaredna.common.exception.ApiException with
     *         {@code REPOSITORY_NOT_FOUND}, {@code REPOSITORY_ACCESS_DENIED},
     *         {@code PROVIDER_RATE_LIMITED} or {@code PROVIDER_UNAVAILABLE}
     */
    RepositoryMetadata fetchMetadata(RepositoryCoordinates coordinates);

    /** The URL to clone from. Never contains credentials. */
    String cloneUrl(RepositoryCoordinates coordinates);
}
