package com.softwaredna.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every failure the API can report, with the status it maps to.
 *
 * Codes are stable strings the frontend may branch on; messages are written
 * for a developer reading them in a UI, never leaking internals.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request was not valid."),
    INVALID_REPOSITORY_URL(HttpStatus.BAD_REQUEST,
            "That does not look like a supported repository reference."),
    UNSUPPORTED_HOST(HttpStatus.BAD_REQUEST,
            "Only repositories hosted on the configured provider can be analysed."),

    REPOSITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "That repository could not be found."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "No analysis exists with that id."),
    ANALYSIS_NOT_COMPLETE(HttpStatus.CONFLICT,
            "That analysis has not finished yet."),

    REPOSITORY_ACCESS_DENIED(HttpStatus.FORBIDDEN,
            "The repository exists but could not be accessed."),
    PROVIDER_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
            "The repository provider rate limit has been reached. Try again later."),
    CLIENT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Try again shortly."),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY,
            "The repository provider could not be reached."),

    REPOSITORY_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY,
            "The repository exceeds the size this service will analyse."),
    REPOSITORY_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY,
            "The repository contains no analysable source files."),
    CLONE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY,
            "The repository could not be cloned."),
    CLONE_TIMEOUT(HttpStatus.UNPROCESSABLE_ENTITY,
            "Cloning the repository took too long."),

    ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
            "The analysis could not be completed."),
    ANALYSIS_QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE,
            "Too many analyses are already queued. Try again shortly."),

    AI_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY,
            "The assistant could not be reached."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong while handling the request.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
