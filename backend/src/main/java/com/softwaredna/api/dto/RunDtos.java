package com.softwaredna.api.dto;

import com.softwaredna.common.domain.AnalysisStageId;
import com.softwaredna.common.domain.AnalysisStatus;
import com.softwaredna.common.domain.StageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request and status shapes for starting and following an analysis. */
public final class RunDtos {

    private RunDtos() {
    }

    @Schema(description = "Request to analyse a repository")
    public record StartAnalysisRequest(
            @NotBlank(message = "a repository reference is required")
            @Size(max = 512, message = "that repository reference is too long")
            @Schema(example = "github.com/octocat/Hello-World",
                    description = "A full URL, an SSH remote, or a bare owner/repository")
            String repositoryUrl
    ) {
    }

    @Schema(description = "Accepted analysis, returned immediately")
    public record StartAnalysisResponse(
            String analysisId,
            AnalysisStatus status,
            String repository
    ) {
    }

    @Schema(description = "One stage of the scan")
    public record StageDto(
            AnalysisStageId id,
            String label,
            String detail,
            StageStatus status,
            @Schema(description = "Present once the stage completes")
            String result
    ) {
    }

    /**
     * The shape the frontend's analysis-run view consumes.
     *
     * <p>{@code progress} is derived from completed stages, never from elapsed
     * time, so it cannot advance while nothing is happening.
     */
    @Schema(description = "Progress snapshot for an in-flight or finished analysis")
    public record AnalysisStatusResponse(
            String analysisId,
            String requestedUrl,
            String displayName,
            AnalysisStatus status,
            AnalysisStageId currentStage,
            double progress,
            String message,
            List<StageDto> stages,
            @Schema(description = "Present only when the analysis failed")
            String errorCode,
            String error
    ) {
    }
}
