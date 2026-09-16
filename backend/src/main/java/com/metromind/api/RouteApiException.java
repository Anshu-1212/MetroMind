package com.metromind.api;

import org.springframework.http.HttpStatus;

/**
 * A client-facing API error that travels from the service layer to the HTTP
 * response.
 *
 * <p>Carries the HTTP status, a stable machine-readable code (see
 * {@link com.metromind.api.dto.ApiError}), and a message. The
 * {@link ApiExceptionHandler} translates it into an {@code ApiError} JSON body
 * — no stack trace ever reaches the response.</p>
 */
public final class RouteApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    /**
     * @param status  the HTTP status to respond with
     * @param error   the stable error code
     * @param message the human-readable message
     */
    public RouteApiException(HttpStatus status, String error, String message) {
        super(message);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.error = error;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }
}