package com.softwaredna.analysis.entity;

import com.softwaredna.common.domain.AnalysisStageId;
import com.softwaredna.common.domain.AnalysisStatus;
import com.softwaredna.common.domain.HealthVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One analysis run, from submission to result.
 *
 * <p>The row is created when the request is accepted, so the id returned by
 * {@code POST /api/analyses} is the same id the client polls and later reads
 * results from. A separate job table would be a one-to-one with no independent
 * lifecycle of its own.
 */
@Entity
@Table(name = "analysis")
public class AnalysisEntity {

    @Id
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "requested_url", nullable = false, length = 1024)
    private String requestedUrl;

    @Column(name = "requested_ref")
    private String requestedRef;

    @Column(name = "resolved_commit_sha", length = 64)
    private String resolvedCommitSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 32)
    private AnalysisStageId currentStage;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal progress = BigDecimal.ZERO;

    @Column(name = "status_message", length = 512)
    private String statusMessage;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "total_lines")
    private Integer totalLines;

    @Column(name = "total_modules")
    private Integer totalModules;

    @Column(name = "total_services")
    private Integer totalServices;

    @Column(name = "total_components")
    private Integer totalComponents;

    @Column(name = "total_commits")
    private Integer totalCommits;

    @Column(name = "total_contributors")
    private Integer totalContributors;

    /** Null when no coverage report was found; never defaulted to zero. */
    @Column(name = "test_coverage", precision = 5, scale = 2)
    private BigDecimal testCoverage;

    @Column(name = "coverage_source", length = 32)
    private String coverageSource;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_verdict", length = 32)
    private HealthVerdict overallVerdict;

    /** Null until enough analyses exist to place this one against them. */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "incomplete_dimensions", nullable = false, columnDefinition = "jsonb")
    private List<String> incompleteDimensions = List.of();

    protected AnalysisEntity() {
    }

    public AnalysisEntity(UUID id, UUID repositoryId, String requestedUrl, String requestedRef) {
        this.id = id;
        this.repositoryId = repositoryId;
        this.requestedUrl = requestedUrl;
        this.requestedRef = requestedRef;
        this.status = AnalysisStatus.QUEUED;
        this.queuedAt = Instant.now();
    }

    public void markRunning() {
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markFailed(String code, String message) {
        this.status = AnalysisStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        finish();
    }

    public void markCompleted() {
        this.status = AnalysisStatus.COMPLETED;
        this.progress = BigDecimal.ONE;
        finish();
    }

    public void markCancelled() {
        this.status = AnalysisStatus.CANCELLED;
        finish();
    }

    private void finish() {
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getRequestedUrl() {
        return requestedUrl;
    }

    public String getRequestedRef() {
        return requestedRef;
    }

    public void setRequestedRef(String requestedRef) {
        this.requestedRef = requestedRef;
    }

    public String getResolvedCommitSha() {
        return resolvedCommitSha;
    }

    public void setResolvedCommitSha(String resolvedCommitSha) {
        this.resolvedCommitSha = resolvedCommitSha;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public AnalysisStageId getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(AnalysisStageId currentStage) {
        this.currentStage = currentStage;
    }

    public BigDecimal getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = BigDecimal.valueOf(Math.max(0, Math.min(1, progress)))
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getTotalFiles() {
        return totalFiles;
    }

    public Integer getTotalLines() {
        return totalLines;
    }

    public Integer getTotalModules() {
        return totalModules;
    }

    public Integer getTotalServices() {
        return totalServices;
    }

    public Integer getTotalComponents() {
        return totalComponents;
    }

    public Integer getTotalCommits() {
        return totalCommits;
    }

    public Integer getTotalContributors() {
        return totalContributors;
    }

    public BigDecimal getTestCoverage() {
        return testCoverage;
    }

    public String getCoverageSource() {
        return coverageSource;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public HealthVerdict getOverallVerdict() {
        return overallVerdict;
    }

    public BigDecimal getPercentile() {
        return percentile;
    }

    public void setPercentile(Double percentile) {
        this.percentile = percentile == null ? null : BigDecimal.valueOf(percentile);
    }

    public List<String> getIncompleteDimensions() {
        return incompleteDimensions;
    }

    public void setIncompleteDimensions(List<String> incompleteDimensions) {
        this.incompleteDimensions = incompleteDimensions == null ? List.of() : incompleteDimensions;
    }

    public void recordStats(int files, int lines, int modules, int services, int components,
                            int commits, int contributors,
                            Double coverage, String coverageSource) {
        this.totalFiles = files;
        this.totalLines = lines;
        this.totalModules = modules;
        this.totalServices = services;
        this.totalComponents = components;
        this.totalCommits = commits;
        this.totalContributors = contributors;
        this.testCoverage = coverage == null ? null : BigDecimal.valueOf(coverage);
        this.coverageSource = coverageSource;
    }

    public void recordScore(double overall, HealthVerdict verdict) {
        this.overallScore = BigDecimal.valueOf(overall);
        this.overallVerdict = verdict;
    }
}
