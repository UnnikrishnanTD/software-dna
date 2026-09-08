package com.softwaredna.repository;

import com.softwaredna.analysis.workspace.AnalysisWorkspace;
import com.softwaredna.analysis.workspace.RepositoryCheckout;
import com.softwaredna.analysis.workspace.WorkspaceManager;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.config.AnalysisLimits;
import com.softwaredna.config.GitHubProperties;
import com.softwaredna.repository.model.RepositoryCoordinates;
import com.softwaredna.repository.model.RepositoryMetadata;
import com.softwaredna.repository.provider.GitHubRepositoryProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises the provider and the clone path against the real GitHub API.
 *
 * <p>Tagged {@code live} because it needs network access. It is excluded from
 * the default build and run explicitly, so an offline machine still gets a
 * green suite while the integration itself is genuinely verified rather than
 * assumed.
 */
@Tag("live")
class LiveGitHubIntegrationTest {

    private static final RepositoryCoordinates HELLO_WORLD =
            RepositoryCoordinates.parse("octocat/Hello-World");

    private GitHubRepositoryProvider provider() {
        GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                System.getenv().getOrDefault("GITHUB_TOKEN", ""),
                Duration.ofSeconds(20));
        return new GitHubRepositoryProvider(RestClient.builder(), properties);
    }

    @Test
    void fetchesRealMetadataFromGitHub() {
        RepositoryMetadata metadata = provider().fetchMetadata(HELLO_WORLD);

        assertThat(metadata.coordinates().fullName()).isEqualTo("octocat/Hello-World");
        assertThat(metadata.defaultBranch()).isNotBlank();
        assertThat(metadata.createdAt()).isNotNull();
        assertThat(metadata.isPrivate()).isFalse();
        assertThat(metadata.sizeKilobytes()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reportsNotFoundForARepositoryThatDoesNotExist() {
        RepositoryCoordinates missing =
                RepositoryCoordinates.parse("octocat/definitely-not-a-real-repo-xyz-42");

        ApiException thrown = catchThrowableOfType(
                () -> provider().fetchMetadata(missing), ApiException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.code()).isIn(
                ErrorCode.REPOSITORY_NOT_FOUND, ErrorCode.PROVIDER_RATE_LIMITED);
    }

    @Test
    void clonesARealRepositoryIntoASandboxedWorkspace() throws IOException {
        AnalysisLimits limits = new AnalysisLimits(
                500L * 1024 * 1024, 2L * 1024 * 1024, 25_000, 20_000,
                Duration.ofMinutes(2), 300);

        WorkspaceManager manager = new WorkspaceManager(
                new com.softwaredna.config.WorkspaceProperties(
                        Files.createTempDirectory("sdna-live").toString(), false));
        manager.prepareRoot();

        RepositoryCheckout checkout = new RepositoryCheckout(limits);
        UUID analysisId = UUID.randomUUID();

        try (AnalysisWorkspace workspace = manager.create(analysisId)) {
            RepositoryMetadata metadata = provider().fetchMetadata(HELLO_WORLD);
            checkout.assertWithinSizeLimit(metadata.sizeKilobytes());

            RepositoryCheckout.Result result = checkout.clone(
                    HELLO_WORLD, HELLO_WORLD.cloneUrl(), metadata.defaultBranch(), workspace);

            assertThat(result.commitSha()).hasSize(40);
            assertThat(result.bytesOnDisk()).isPositive();
            assertThat(checkout.isUsable(workspace)).isTrue();
            assertThat(Files.isDirectory(workspace.checkout())).isTrue();

            // The clone URL that was used carries no credential.
            Path config = workspace.checkout().resolve(".git").resolve("config");
            assertThat(Files.readString(config)).doesNotContain("@github.com");

            // Path containment holds for anything the repository might name.
            assertThatThrownBy(() -> workspace.resolve("../../../etc/passwd"))
                    .isInstanceOf(ApiException.class);
            assertThat(workspace.resolve("README")).startsWith(workspace.checkout());
        }
    }

    @Test
    void refusesARepositoryLargerThanTheConfiguredLimit() {
        AnalysisLimits tinyLimit = new AnalysisLimits(
                1024, 2048, 100, 100, Duration.ofSeconds(30), 50);
        RepositoryCheckout checkout = new RepositoryCheckout(tinyLimit);

        ApiException thrown = catchThrowableOfType(
                () -> checkout.assertWithinSizeLimit(50_000), ApiException.class);

        assertThat(thrown.code()).isEqualTo(ErrorCode.REPOSITORY_TOO_LARGE);
    }
}
