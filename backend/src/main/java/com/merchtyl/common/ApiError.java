package com.merchtyl.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Standard error response returned by the Merchtyl API.")
public record ApiError(
        @Schema(description = "Stable machine-readable error code.", example = "validation_failed")
        String code,
        @Schema(description = "Human-readable error summary.", example = "Request validation failed")
        String message,
        @Schema(description = "HTTP status code.", example = "400")
        int status,
        @Schema(description = "Request path that failed.", example = "/api/v1/business-days")
        String path,
        @Schema(description = "HTTP method that failed.", example = "POST")
        String method,
        @Schema(description = "Correlation identifier for support and logs.", example = "b8f9428b-07d7-4d03-9c26-8a2ee89f44d3")
        String correlationId,
        @Schema(description = "Field-level validation errors, when available.")
        List<FieldViolation> violations,
        @Schema(description = "UTC timestamp when the error was generated.", type = "string", format = "date-time", example = "2026-07-29T12:00:00Z")
        Instant timestamp
) {
    @Schema(description = "Field-level validation error.")
    public record FieldViolation(
            @Schema(description = "Invalid request field.", example = "version")
            String field,
            @Schema(description = "Stable field validation code.", example = "PASSWORD_TOO_SHORT")
            String code,
            @Schema(description = "Validation message.", example = "must not be null")
            String message) {
    }
}
