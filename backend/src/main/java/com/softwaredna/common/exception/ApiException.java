package com.softwaredna.common.exception;

/**
 * Base for every failure that should reach the client as a structured error.
 *
 * Anything else that escapes a controller is treated as an internal error and
 * reported without detail, so an unexpected exception can never leak
 * implementation information.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static ApiException notFound(ErrorCode code, String what) {
        return new ApiException(code, what);
    }
}
