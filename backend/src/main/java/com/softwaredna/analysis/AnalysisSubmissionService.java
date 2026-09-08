package com.softwaredna.analysis;

import com.softwaredna.analysis.entity.AnalysisEntity;
import com.softwaredna.analysis.entity.AnalysisJpaRepository;
import com.softwaredna.analysis.persistence.AnalysisStageStore;
import com.softwaredna.common.domain.AnalysisStatus;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.repository.entity.RepositoryEntity;
import com.softwaredna.repository.entity.RepositoryJpaRepository;
import com.softwaredna.repository.model.RepositoryCoordinates;
import com.softwaredna.repository.model.RepositoryMetadata;
import com.softwaredna.repository.provider.RepositoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Accepts an analysis request and hands it to the background pool.
 *
 * <p>The request returns as soon as the row exists, so a caller is never held
 * open for the length of a clone and a scan. Validation that can fail fast —
 * the reference is malformed, the repository does not exist, it is too large —
 * happens synchronously, so those come back as a proper error rather than as a
 * failed job the caller has to poll to discover.
 */
@Service
public class AnalysisSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisSubmissionService.class);

    private final RepositoryProvider provider;
    private final RepositoryJpaRepository repositories;
    private final AnalysisJpaRepository analyses;
    private final AnalysisStageStore stages;
    private final AnalysisOrchestrator orchestrator;
    private final ThreadPoolTaskExecutor executor;

    public AnalysisSubmissionService(RepositoryProvider provider,
                                     RepositoryJpaRepository repositories,
                                     AnalysisJpaRepository analyses,
                                     AnalysisStageStore stages,
                                     AnalysisOrchestrator orchestrator,
                                     ThreadPoolTaskExecutor analysisExecutor) {
        this.provider = provider;
        this.repositories = repositories;
        this.analyses = analyses;
        this.stages = stages;
        this.orchestrator = orchestrator;
        this.executor = analysisExecutor;
    }

    public record Submission(UUID analysisId, String repositoryFullName) {
    }

    @Transactional
    public Submission submit(String repositoryUrl) {
        // Parsing is the security boundary: everything downstream trusts it.
        RepositoryCoordinates coordinates = RepositoryCoordinates.parse(repositoryUrl);

        // Fetched now so a missing or inaccessible repository is a 404 on this
        // request, not a failure the caller discovers by polling.
        RepositoryMetadata metadata = provider.fetchMetadata(coordinates);

        RepositoryEntity repository = repositories
                .findByProviderAndOwnerIgnoreCaseAndNameIgnoreCase(
                        coordinates.provider(), coordinates.owner(), coordinates.name())
                .orElseGet(() -> new RepositoryEntity(UUID.randomUUID(),
                        coordinates.provider(), coordinates.owner(), coordinates.name(),
                        coordinates.httpsUrl()));

        repository.setDefaultBranch(metadata.defaultBranch());
        repository.setDescription(metadata.description());
        repository.setPrimaryLanguage(metadata.primaryLanguage());
        repository.setStars(metadata.stars());
        repository.setForks(metadata.forks());
        repository.setRemoteCreatedAt(metadata.createdAt());
        repository.setLastCommitAt(metadata.pushedAt());
        repositories.save(repository);

        UUID analysisId = UUID.randomUUID();
        AnalysisEntity analysis = new AnalysisEntity(analysisId, repository.getId(),
                repositoryUrl, metadata.defaultBranch());

        // Flush before seeding the stages. The stage rows are written with
        // plain JDBC in this same transaction, and JPA would otherwise defer
        // this insert until commit — leaving the stage insert to fail against
        // a foreign key whose target does not exist yet.
        analyses.saveAndFlush(analysis);
        stages.seed(analysisId);

        // Enqueue only once the transaction has committed. Starting sooner
        // would let the worker read a row that does not exist yet.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        enqueue(analysisId, coordinates);
                    }
                });

        log.info("Accepted analysis {} for {}", analysisId, coordinates.fullName());
        return new Submission(analysisId, coordinates.fullName());
    }

    private void enqueue(UUID analysisId, RepositoryCoordinates coordinates) {
        try {
            executor.execute(() -> orchestrator.run(analysisId, coordinates));
        } catch (RejectedExecutionException e) {
            // The queue is full. Recording the failure is better than dropping
            // the request silently; the caller sees it on its first poll.
            log.warn("Analysis {} rejected: the queue is full", analysisId);
            analyses.findById(analysisId).ifPresent(analysis -> {
                analysis.markFailed(ErrorCode.ANALYSIS_QUEUE_FULL.name(),
                        ErrorCode.ANALYSIS_QUEUE_FULL.defaultMessage());
                analyses.save(analysis);
            });
        }
    }

    /** Marks a queued or running analysis as cancelled. */
    @Transactional
    public void cancel(UUID analysisId) {
        AnalysisEntity analysis = analyses.findById(analysisId).orElseThrow(
                () -> new ApiException(ErrorCode.ANALYSIS_NOT_FOUND,
                        "No analysis exists with id " + analysisId + "."));

        if (analysis.getStatus().isTerminal()) {
            return;
        }
        analysis.markCancelled();
        analyses.save(analysis);
        log.info("Analysis {} cancelled", analysisId);
    }

    public long activeCount() {
        return analyses.countByStatusIn(
                java.util.List.of(AnalysisStatus.QUEUED, AnalysisStatus.RUNNING));
    }
}
