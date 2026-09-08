package com.softwaredna.analysis.workspace;

import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/**
 * A sandbox directory for one analysis.
 *
 * <p>Every read performed by the engine goes through {@link #resolve}, which
 * refuses to return a path outside the workspace. A repository can therefore
 * contain a symlink to {@code /etc/passwd} or a file named {@code ../../x} and
 * still not be able to reach anything the analysis should not see.
 */
public final class AnalysisWorkspace implements AutoCloseable {

    private final UUID analysisId;
    private final Path root;
    private final Path checkout;
    private final boolean keepAfterClose;

    AnalysisWorkspace(UUID analysisId, Path root, boolean keepAfterClose) {
        this.analysisId = analysisId;
        this.root = root.toAbsolutePath().normalize();
        this.checkout = this.root.resolve("checkout");
        this.keepAfterClose = keepAfterClose;
    }

    public UUID analysisId() {
        return analysisId;
    }

    /** The directory the repository is cloned into. */
    public Path checkout() {
        return checkout;
    }

    public Path root() {
        return root;
    }

    /**
     * Resolves a repository-relative path inside the checkout.
     *
     * @throws ApiException if the result would escape the workspace
     */
    public Path resolve(String relativePath) {
        Path resolved = checkout.resolve(relativePath).normalize();
        if (!resolved.startsWith(checkout)) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                    "The repository referenced a path outside its own tree.");
        }
        return resolved;
    }

    /** True when the path lies within the checkout after normalisation. */
    public boolean contains(Path candidate) {
        return candidate.toAbsolutePath().normalize().startsWith(checkout);
    }

    /** Total bytes currently on disk beneath the workspace. */
    public long sizeOnDisk() throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            // Do not follow symlinks when measuring; a link to a
                            // huge file outside the tree is not our storage.
                            return Files.isSymbolicLink(path) ? 0 : Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
    }

    @Override
    public void close() throws IOException {
        if (keepAfterClose) {
            return;
        }
        deleteRecursively(root);
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    // NOFOLLOW semantics: deleting a symlink removes the link,
                    // never its target.
                    Files.deleteIfExists(entry);
                } catch (IOException ignored) {
                    // A file we cannot remove should not fail the analysis.
                }
            });
        }
    }
}
