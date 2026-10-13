package io.github.edtechdevelopment.tutoring.presentation.error;

import java.util.List;
public record TutoringApiError(String code, String message, List<TutoringFieldError> fieldErrors, String requestId) {
    public TutoringApiError {
        fieldErrors = List.copyOf(fieldErrors);
    }
}
