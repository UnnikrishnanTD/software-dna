package com.softwaredna.api;

import com.softwaredna.api.dto.ComparisonDtos;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.common.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Comparison of two analyses.
 *
 * <p>A separate controller from the analysis endpoints so the literal
 * {@code /compare} path is unambiguous alongside {@code /{id}}.
 */
@RestController
@Tag(name = "Comparison", description = "Compare two completed analyses")
public class ComparisonController {

    private final ComparisonService comparisons;

    public ComparisonController(ComparisonService comparisons) {
        this.comparisons = comparisons;
    }

    @GetMapping("/api/analyses/compare")
    @Operation(summary = "Compare two completed analyses",
            description = "Dimensions unavailable on either side are reported as "
                    + "incomparable rather than counted as a loss.")
    @ApiResponse(responseCode = "404", description = "Either analysis is unknown",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ComparisonDtos.ComparisonResult compare(@RequestParam("left") String left,
                                                   @RequestParam("right") String right) {
        return comparisons.compare(parseId(left), parseId(right));
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ANALYSIS_NOT_FOUND,
                    "No analysis exists with id " + id + ".");
        }
    }
}
