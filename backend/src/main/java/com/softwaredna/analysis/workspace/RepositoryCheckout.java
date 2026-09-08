package com.softwaredna.analysis.workspace;

import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.config.AnalysisLimits;
import com.softwaredna.repository.model.RepositoryCoordinates;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Clones a repository into a workspace.
 *
 * <p>Cloning is done with JGit rather than by invoking the {@code git} binary.
 * That removes command injection as a category of risk entirely: there is no
 * shell, no argument string, and no opportunity for a reference like
 * {@code --upload-pack=...} to be interpreted as an option.
 *
 * <p>Three limits apply, and each fails the analysis with a specific code
 * rather than degrading quietly: an up-front size check against provider
 * metadata, a wall-clock timeout, and a post-clone measurement of what
 * actually landed on disk.
 */
@Component
public class RepositoryCheckout {

    private static final Logger log = LoggerFactory.getLogger(RepositoryCheckout.class);

    private final AnalysisLimits limits;

    public RepositoryCheckout(AnalysisLimits limits) {
        this.limits = limits;
    }

    /** The result of a successful clone. */
    public record Result(String branch, String commitSha, long bytesOnDisk) {
    }

    /**
     * Rejects a repository before any network transfer, using the size the
     * provider reported. Cheap, and it stops the obvious resource exhaustion
     * case at the door.
     */
    public void assertWithinSizeLimit(long reportedKilobytes) {
        long reportedBytes = reportedKilobytes * 1024L;
        if (reportedBytes > limits.maxRepositoryBytes()) {
            throw new ApiException(ErrorCode.REPOSITORY_TOO_LARGE,
                    "That repository is %d MB, which exceeds the %d MB limit for analysis."
                            .formatted(reportedBytes / (1024 * 1024),
                                    limits.maxRepositoryBytes() / (1024 * 1024)));
        }
    }

    public Result clone(RepositoryCoordinates coordinates, String cloneUrl,
                        String branch, AnalysisWorkspace workspace) {
        Path target = workspace.checkout();
        log.info("Cloning {} ({}) into workspace {}",
                coordinates.fullName(), branch, workspace.analysisId());

        long startedAt = System.currentTimeMillis();
        try {
            runWithTimeout(() -> doClone(cloneUrl, branch, target), limits.cloneTimeout());
        } catch (TimeoutException e) {
            safeDelete(target);
            throw new ApiException(ErrorCode.CLONE_TIMEOUT,
                    "Cloning %s took longer than %d seconds."
                            .formatted(coordinates.fullName(), limits.cloneTimeout().toSeconds()));
        }

        long bytes = measure(workspace);
        if (bytes > limits.maxRepositoryBytes()) {
            safeDelete(target);
            throw new ApiException(ErrorCode.REPOSITORY_TOO_LARGE,
                    "The checked-out repository exceeds the %d MB analysis limit."
                            .formatted(limits.maxRepositoryBytes() / (1024 * 1024)));
        }

        String head = resolveHead(target);
        log.info("Cloned {} in {} ms ({} MB on disk, HEAD {})",
                coordinates.fullName(), System.currentTimeMillis() - startedAt,
                bytes / (1024 * 1024), abbreviate(head));

        return new Result(branch, head, bytes);
    }

    private Void doClone(String cloneUrl, String branch, Path target) {
        try (Git git = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(target.toFile())
                // Only the branch being analysed. Cloning every branch can
                // multiply the transfer for no analytical gain.
                .setCloneAllBranches(false)
                .setBranch(branch)
                .setBranchesToClone(java.util.List.of(Constants.R_HEADS + branch))
                .setTimeout((int) limits.cloneTimeout().toSeconds())
                .call()) {
            return null;
        } catch (InvalidRemoteException e) {
            throw new ApiException(ErrorCode.REPOSITORY_NOT_FOUND,
                    "The repository could not be found or is not accessible.", e);
        } catch (TransportException e) {
            // Message may name the host but never carries a credential, since
            // the clone URL we build has none.
            log.warn("Clone transport failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.CLONE_FAILED,
                    "The repository could not be cloned.", e);
        } catch (GitAPIException e) {
            throw new ApiException(ErrorCode.CLONE_FAILED,
                    "The repository could not be cloned.", e);
        }
    }

    /**
     * Runs the clone on its own thread so it can be abandoned on timeout.
     * JGit's own timeout covers individual socket operations, not the whole
     * transfer, so a slow-drip response would otherwise hang indefinitely.
     */
    private void runWithTimeout(Callable<Void> task, Duration timeout) throws TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "repository-clone");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Void> future = executor.submit(task);
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCode.ANALYSIS_FAILED, "The analysis was interrupted.", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ApiException apiException) {
                    throw apiException;
                }
                throw new ApiException(ErrorCode.CLONE_FAILED,
                        "The repository could not be cloned.", cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private long measure(AnalysisWorkspace workspace) {
        try {
            return workspace.sizeOnDisk();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                    "Could not measure the cloned repository.", e);
        }
    }

    private String resolveHead(Path target) {
        try (Git git = Git.open(target.toFile())) {
            Repository repository = git.getRepository();
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                throw new ApiException(ErrorCode.REPOSITORY_EMPTY,
                        "The repository has no commits to analyse.");
            }
            return head.name();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.CLONE_FAILED,
                    "The cloned repository could not be opened.", e);
        }
    }

    private void safeDelete(Path path) {
        try {
            AnalysisWorkspace.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Could not clean up {} after a failed clone", path);
        }
    }

    private static String abbreviate(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }

    /** Deletes the workspace, ignoring failures. Used by the pipeline's finally block. */
    public void discard(AnalysisWorkspace workspace) {
        try {
            workspace.close();
        } catch (IOException e) {
            log.warn("Could not clean workspace {}", workspace.analysisId());
        }
    }

    /** True when the checkout looks like a usable git working tree. */
    public boolean isUsable(AnalysisWorkspace workspace) {
        return Files.isDirectory(workspace.checkout().resolve(".git"));
    }
}
