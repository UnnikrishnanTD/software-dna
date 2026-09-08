package com.softwaredna.api;

import com.softwaredna.ai.AiProviderRegistry;
import com.softwaredna.api.dto.AnalysisDtos;
import com.softwaredna.api.dto.DoctorDtos;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.common.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Software Doctor.
 *
 * <p>The provider receives the structured analysis, never the repository's
 * source. That keeps a customer's code off any third-party service and keeps
 * the request small enough to be answered cheaply.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Doctor", description = "Ask questions about a completed analysis")
public class DoctorController {

    private final AiProviderRegistry providers;
    private final AnalysisReadService reads;

    public DoctorController(AiProviderRegistry providers, AnalysisReadService reads) {
        this.providers = providers;
        this.reads = reads;
    }

    @PostMapping("/questions")
    @Operation(summary = "Ask a question about an analysis",
            description = "The answer is a list of typed blocks so the client can render "
                    + "metrics and plans as structured output rather than prose. Every "
                    + "response names the provider that produced it.")
    @ApiResponse(responseCode = "404", description = "No such analysis",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public DoctorDtos.DoctorMessage ask(@Valid @RequestBody DoctorDtos.QuestionRequest request) {
        UUID analysisId;
        try {
            analysisId = UUID.fromString(request.analysisId());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ANALYSIS_NOT_FOUND,
                    "No analysis exists with id " + request.analysisId() + ".");
        }

        AnalysisDtos.RepositoryAnalysisDto analysis = reads.getAnalysis(analysisId);
        return providers.provider().answer(analysis, request.question());
    }
}
