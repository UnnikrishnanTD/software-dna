package com.softwaredna.api;

import com.softwaredna.analysis.entity.AnalysisEntity;
import com.softwaredna.analysis.entity.AnalysisJpaRepository;
import com.softwaredna.analysis.persistence.AnalysisStageStore;
import com.softwaredna.api.dto.RunDtos;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.repository.entity.RepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serves progress for an in-flight analysis.
 *
 * <p>This is what the frontend polls while a scan runs. Polling is an
 * implementation detail of the transport: the Angular service turns these
 * snapshots back into the same observable stream it always consumed, so
 * replacing polling with server-sent events later changes nothing above this
 * boundary.
 */
@Service
public class AnalysisStatusService {

    private final AnalysisJpaRepository analyses;
    private final RepositoryJpaRepository repositories;
    private final AnalysisStageStore stages;

    public AnalysisStatusService(AnalysisJpaRepository analyses,
                                 RepositoryJpaRepository repositories,
                                 AnalysisStageStore stages) {
        this.analyses = analyses;
        this.repositories = repositories;
        this.stages = stages;
    }

    @Transactional(readOnly = true)
    public RunDtos.AnalysisStatusResponse statusOf(UUID analysisId) {
        AnalysisEntity analysis = analyses.findById(analysisId).orElseThrow(
                () -> new ApiException(ErrorCode.ANALYSIS_NOT_FOUND,
                        "No analysis exists with id " + analysisId + "."));

        List<RunDtos.StageDto> stageRows = stages.findByAnalysis(analysisId).stream()
                .map(row -> new RunDtos.StageDto(row.id(), row.label(), row.detail(),
                        row.status(), row.result()))
                .toList();

        String displayName = repositories.findById(analysis.getRepositoryId())
                .map(repository -> repository.getFullName())
                .orElse(analysis.getRequestedUrl());

        return new RunDtos.AnalysisStatusResponse(
                analysis.getId().toString(),
                analysis.getRequestedUrl(),
                displayName,
                analysis.getStatus(),
                analysis.getCurrentStage(),
                analysis.getProgress() == null ? 0 : analysis.getProgress().doubleValue(),
                analysis.getStatusMessage(),
                stageRows,
                analysis.getErrorCode(),
                analysis.getErrorMessage());
    }
}
