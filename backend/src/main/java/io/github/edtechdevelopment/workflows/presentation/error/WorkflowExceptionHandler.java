package io.github.edtechdevelopment.workflows.presentation.error;

import io.github.edtechdevelopment.identity.api.exception.IdentityCommandException;
import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.workflows.application.exception.WorkflowException;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice(basePackages = "io.github.edtechdevelopment.workflows.presentation")
public class WorkflowExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<WorkflowApiError> validation(MethodArgumentNotValidException exception) {
        List<WorkflowFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new WorkflowFieldError(field.getField(), field.getCode(), field.getDefaultMessage()))
                .sorted(Comparator.comparing(WorkflowFieldError::field)).toList();
        return error(400, "VALIDATION_ERROR", "Request validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<WorkflowApiError> unreadable() {
        return error(400, "VALIDATION_ERROR", "Request body is malformed or contains an unsupported value", List.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<WorkflowApiError> invalidInput() {
        return error(400, "VALIDATION_ERROR", "Request data is invalid", List.of());
    }

    @ExceptionHandler(WorkflowException.class)
    ResponseEntity<WorkflowApiError> workflowFailure(WorkflowException exception) {
        int status = switch (exception.reason()) {
            case ROLE_PROFILE_MISMATCH -> 400;
            case ACCOUNT_UNAVAILABLE -> 404;
            case ACCOUNT_NOT_ACTIVE -> 403;
            case ROLE_ALREADY_ENABLED, IDEMPOTENCY_CONFLICT -> 409;
            case PROFILE_INCONSISTENCY -> 500;
        };
        List<WorkflowFieldError> fields = exception.invalidFields().stream().sorted()
                .map(field -> new WorkflowFieldError(field, "Invalid", "Invalid value")).toList();
        return error(status, exception.reason().name(), "The operation cannot be completed", fields);
    }

    @ExceptionHandler(IdentityCommandException.class)
    ResponseEntity<WorkflowApiError> identityFailure(IdentityCommandException exception) {
        return switch (exception.reason()) {
            case INVALID_DATA -> error(400, "VALIDATION_ERROR", "Account data is invalid", List.of());
            case EMAIL_ALREADY_EXISTS -> error(409, "EMAIL_ALREADY_EXISTS", "Email is already registered", List.of());
            case ACCOUNT_STATE_CONFLICT -> error(409, "ACCOUNT_STATE_CONFLICT", "Account state changed or the role already exists", List.of());
        };
    }

    @ExceptionHandler(InvalidProfileDataException.class)
    ResponseEntity<WorkflowApiError> invalidProfile(InvalidProfileDataException exception, HttpServletRequest request) {
        String prefix = "";
        if (request.getRequestURI().equals(request.getContextPath() + "/api/v1/auth/register") && exception.profileType() != null) {
            prefix = exception.profileType() == ProfileTypeView.TEACHER ? "teacherProfile." : "studentProfile.";
        }
        String fieldPrefix = prefix;
        List<WorkflowFieldError> fields = exception.invalidFields().stream().sorted()
                .map(field -> new WorkflowFieldError(fieldPrefix + field, "Invalid", "Invalid value")).toList();
        return error(400, "VALIDATION_ERROR", "Profile data is invalid", fields);
    }

    @ExceptionHandler(UnknownSubjectsException.class)
    ResponseEntity<WorkflowApiError> unknownSubjects() {
        return error(400, "UNKNOWN_SUBJECTS", "Selected subjects do not exist", List.of());
    }

    @ExceptionHandler(ProfileAlreadyExistsException.class)
    ResponseEntity<WorkflowApiError> profileConflict() {
        return error(409, "PROFILE_ALREADY_EXISTS", "Profile already exists", List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<WorkflowApiError> storageFailure() {
        return error(503, "INTERNAL_ERROR", "Storage is temporarily unavailable", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<WorkflowApiError> unexpected(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        LOGGER.error("Workflow failed, requestId={}, exceptionType={}", requestId, exception.getClass().getName());
        return ResponseEntity.status(500).body(new WorkflowApiError(
                "INTERNAL_ERROR", "An internal error occurred", List.of(), requestId));
    }

    private ResponseEntity<WorkflowApiError> error(int status, String code, String message, List<WorkflowFieldError> fields) {
        return ResponseEntity.status(status).body(new WorkflowApiError(code, message, fields, UUID.randomUUID().toString()));
    }
}

