package com.swayam.ledger.api;

import com.swayam.ledger.api.Dtos.ApiError;
import com.swayam.ledger.ledger.LedgerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * One place that turns exceptions into JSON, so controllers stay free of
 * try/catch and every error response has the same shape.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    /** A broken invariant is the caller's fault, not a server fault: 422, not 500. */
    @ExceptionHandler(LedgerException.class)
    ResponseEntity<ApiError> onLedgerException(LedgerException e) {
        HttpStatus status = e.getMessage().startsWith("no such")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
                .body(new ApiError(status.getReasonPhrase(), e.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ApiError("Bad Request", detail, Instant.now()));
    }
}
