package com.metromind.api;

import com.metromind.api.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Translates API failures into the project's small, consistent error body.
 *
 * <p>Every handler returns {@code {"error": "<code>", "message": "<text>"}} and
 * never leaks a stack trace or internal implementation detail. Unknown enum
 * values (an unsupported algorithm or metric) arrive as
 * {@link InvalidFormatException} from Jackson; the target type distinguishes
 * which field was bad so the response carries the correct code.</p>
 */
@RestControllerAdvice
public final class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Errors intentionally thrown by {@link RouteService}. */
    @ExceptionHandler(RouteApiException.class)
    public ResponseEntity<ApiError> handleRouteApi(RouteApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getError(), ex.getMessage()));
    }

    /**
     * Body that Jackson could not deserialize: malformed JSON, a missing whole
     * body, or an enum value that is not one of the supported values.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException ife) {
            Class<?> target = ife.getTargetType();
            if (target == Algorithm.class) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("UNSUPPORTED_ALGORITHM",
                                "Unsupported algorithm: " + ife.getValue()
                                        + " (supported: BFS, DIJKSTRA, ASTAR)"));
            }
            if (target == com.metromind.routing.RouteMetric.class) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("UNSUPPORTED_METRIC",
                                "Unsupported metric: " + ife.getValue()
                                        + " (supported: DISTANCE, TRAVEL_TIME)"));
            }
        }
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST",
                        "Request body is not valid JSON for the routes endpoint"));
    }

    /** A request used an HTTP verb the endpoint does not support (e.g. GET). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED",
                        ex.getMethod() + " is not supported for this endpoint"));
    }

    /** Last-resort guard so an internal failure cannot leak details to a client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling an API request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}