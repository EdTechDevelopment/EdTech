package io.github.edtechdevelopment.identity.presentation.error.model;

import java.util.List;

public record ApiError(
        ErrorCode code,
        String message,
        List<FieldErrorResponse> fieldErrors,
        String requestId
) {

    public ApiError {
        fieldErrors = List.copyOf(fieldErrors);
    }
}
