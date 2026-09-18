package io.github.edtechdevelopment.identity.presentation.error.model;

public record FieldErrorResponse(
        String field,
        String code,
        String message
) {
}
