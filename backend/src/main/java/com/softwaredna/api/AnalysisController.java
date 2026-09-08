package com.softwaredna.api;

import com.softwaredna.analysis.AnalysisSubmissionService;
import com.softwaredna.api.dto.AnalysisDtos;
import com.softwaredna.api.dto.RunDtos;
import com.softwaredna.common.domain.AnalysisStatus;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.common.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * The analysis API.
 *
 * <p>Submission returns immediately with an id; results and progress are read
 * back from that id. The aggregate endpoint returns the whole profile because
 * that is what the product's overview screen needs in one paint; the section
 * endpoints exist for clients that want one part of it without the rest.
 */
@RestController
@RequestMapping("/api/analyses")
@Tag(name = "Analyses", description = "Submit repositories and read their Software DNA")
public class AnalysisController {

    private final AnalysisSubmissionService submissions;
    private final AnalysisStatusService status;
    private final AnalysisReadService reads;

    public AnalysisController(AnalysisSubmissionService submissions,
                              AnalysisStatusService status,
                              AnalysisReadService reads) {
        this.submissions = submissions;
        this.status = status;
        this.reads = reads;
    }

    @PostMapping
    @Operation(summary = "Start an analysis",
            description = "Validates the repository, creates the analysis and queues it. "
                    + "Returns as soon as the analysis exists; poll the status endpoint "
                    + "for progress.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Analysis accepted"),
            @ApiResponse(responseCode = "400", description = "The reference is not a valid "
                    + "GitHub repository",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "The repository does not exist",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "The repository is too large "
                    + "to analyse",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "The provider rate limit was hit",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RunDtos.StartAnalysisResponse> start(
            @Valid @RequestBody RunDtos.StartAnalysisRequest request) {

        AnalysisSubmissionService.Submission submission =
                submissions.submit(request.repositoryUrl());

        // 202 with a Location header: the work is queued, not done, and the
        // header points at where the result will appear.
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(UriComponentsBuilder.fromPath("/api/analyses/{id}")
                        .buildAndExpand(submission.analysisId()).toUri())
                .body(new RunDtos.StartAnalysisResponse(
                        submission.analysisId().toString(),
                        AnalysisStatus.QUEUED,
                        submission.repositoryFullName()));
    }

    @GetMapping
    @Operation(summary = "List completed analyses",
            description = "Newest first. Analyses still running are not listed.")
    public List<AnalysisDtos.AnalysisSummary> list() {
        return reads.listCompleted();
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Progress of an analysis",
            description = "Progress is derived from completed stages, not elapsed time.")
    @ApiResponse(responseCode = "404", description = "No such analysis",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public RunDtos.AnalysisStatusResponse status(@PathVariable("id") String id) {
        return status.statusOf(parseId(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "The complete Software DNA profile",
            description = "Available once the analysis has completed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The profile"),
            @ApiResponse(responseCode = "404", description = "No such analysis",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The analysis has not finished",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AnalysisDtos.RepositoryAnalysisDto get(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an analysis",
            description = "Has no effect on an analysis that already finished.")
    public ResponseEntity<Void> cancel(@PathVariable("id") String id) {
        submissions.cancel(parseId(id));
        return ResponseEntity.noContent().build();
    }

    // ---- Section endpoints -------------------------------------------------
    // The aggregate above is what the product reads; these exist for clients
    // that want one section without paying for the whole profile.

    @GetMapping("/{id}/architecture")
    @Operation(summary = "The dependency graph alone")
    public AnalysisDtos.ArchitectureGraphDto architecture(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id)).architecture();
    }

    @GetMapping("/{id}/codebase")
    @Operation(summary = "The file tree alone")
    public AnalysisDtos.FileNodeDto codebase(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id)).codebase();
    }

    @GetMapping("/{id}/hotspots")
    @Operation(summary = "Ranked hotspots alone")
    public List<AnalysisDtos.HotspotDto> hotspots(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id)).hotspots();
    }

    @GetMapping("/{id}/dependencies")
    @Operation(summary = "The dependency profile alone")
    public AnalysisDtos.DependencyProfileDto dependencies(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id)).dependencies();
    }

    @GetMapping("/{id}/evolution")
    @Operation(summary = "The evolution timeline alone")
    public AnalysisDtos.EvolutionHistoryDto evolution(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id)).evolution();
    }

    @GetMapping("/{id}/recommendations")
    @Operation(summary = "Ranked remediation steps alone")
    public List<AnalysisDtos.RemediationStepDto> recommendations(@PathVariable("id") String id) {
        return reads.getAnalysis(parseId(id)).remediation();
    }

    /** Rejects a malformed id as a 404 rather than letting it become a 500. */
    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ANALYSIS_NOT_FOUND,
                    "No analysis exists with id " + id + ".");
        }
    }
}
