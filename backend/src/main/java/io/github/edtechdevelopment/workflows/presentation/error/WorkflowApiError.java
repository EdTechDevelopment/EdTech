package io.github.edtechdevelopment.workflows.presentation.error;

import java.util.List;

public record WorkflowApiError(String code, String message, List<WorkflowFieldError> fieldErrors, String requestId) {

    public WorkflowApiError {
        fieldErrors = List.copyOf(fieldErrors);
    }
}

