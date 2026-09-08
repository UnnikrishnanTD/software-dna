package com.softwaredna.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every endpoint returns.
 *
 * Deliberately contains no stack trace, no exception class name and no
 * internal identifiers.
 */
@Schema(description = "Structured error returned by every endpoint")
public record ApiError(
        @Schema(example = "2026-09-08T09:15:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "INVALID_REPOSITORY_URL") String code,
        @Schema(example = "That does not look like a supported repository reference.")
        String message,
        @Schema(example = "/api/analyses") String path,
        @Schema(description = "Field-level problems, when the request failed validation")
        List<FieldProblem> errors
) {
    public record FieldProblem(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }

    public static ApiError of(int status, String code, String message, String path,
                              List<FieldProblem> errors) {
        return new ApiError(Instant.now(), status, code, message, path, errors);
    }
}
