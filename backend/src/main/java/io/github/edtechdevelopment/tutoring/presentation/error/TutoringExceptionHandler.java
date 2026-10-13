package io.github.edtechdevelopment.tutoring.presentation.error;

import io.github.edtechdevelopment.tutoring.api.exception.IdempotencyConflictException;
import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvalidInvitationCursorException;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationCreationConflictException;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationResponseConflictException;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationUnavailableException;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileSearchException;
import io.github.edtechdevelopment.tutoring.application.relationship.exception.InvalidRelationshipCursorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestControllerAdvice(basePackages = "io.github.edtechdevelopment.tutoring.presentation")
public class TutoringExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TutoringExceptionHandler.class);

    private ResponseEntity<TutoringApiError> error(int status, String code, String message, List<TutoringFieldError> fields) {
        return ResponseEntity.status(status).body(new TutoringApiError(code, message, fields, UUID.randomUUID().toString()));
    }
    private List<TutoringFieldError> fields(Set<String> names) {
        return names.stream().sorted().map(name -> new TutoringFieldError(name, "Invalid value")).toList();
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<TutoringApiError> validation(MethodArgumentNotValidException exception) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new TutoringFieldError(field.getField(), field.getDefaultMessage()))
                .sorted(Comparator.comparing(TutoringFieldError::field)).toList();
        return error(400, "VALIDATION_ERROR", "Request validation failed", fields);
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<TutoringApiError> invalidInput(Exception exception) {
        return error(400, "VALIDATION_ERROR", "Request data is invalid", List.of());
    }
    @ExceptionHandler(InvalidProfileDataException.class)
    ResponseEntity<TutoringApiError> invalidProfile(InvalidProfileDataException exception) {
        return error(400, "VALIDATION_ERROR", "Profile data is invalid", fields(exception.invalidFields()));
    }
    @ExceptionHandler(InvalidPublicProfileSearchException.class)
    ResponseEntity<TutoringApiError> invalidSearch(InvalidPublicProfileSearchException exception) {
        return error(400, "VALIDATION_ERROR", "Search filters are invalid", fields(exception.invalidFields()));
    }
    @ExceptionHandler(UnknownSubjectsException.class)
    ResponseEntity<TutoringApiError> unknownSubjects() {
        return error(400, "UNKNOWN_SUBJECTS", "Selected subjects do not exist", List.of(new TutoringFieldError("subjectCodes", "Unknown subject")));
    }
    @ExceptionHandler({InvalidPublicProfileCursorException.class, InvalidInvitationCursorException.class, InvalidRelationshipCursorException.class})
    ResponseEntity<TutoringApiError> invalidCursor() {
        return error(400, "INVALID_CURSOR", "Cursor is invalid", List.of());
    }

    @ExceptionHandler({ProfileNotFoundException.class, InvitationUnavailableException.class})
    ResponseEntity<TutoringApiError> notFound() {
        return error(404, "NOT_FOUND", "Requested resource is unavailable", List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<TutoringApiError> responseStatus(ResponseStatusException exception) {
        return error(exception.getStatusCode().value(), "NOT_FOUND", "Requested resource is unavailable", List.of());
    }
    @ExceptionHandler(InvitationCreationConflictException.class)
    ResponseEntity<TutoringApiError> invitationCreation(InvitationCreationConflictException exception) {
        return error(409, exception.reason().name(), "Invitation cannot be created", List.of());
    }
    @ExceptionHandler(InvitationResponseConflictException.class)
    ResponseEntity<TutoringApiError> invitationResponse(InvitationResponseConflictException exception) {
        return error(409, exception.reason().name(), "Invitation cannot be answered", List.of());
    }
    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<TutoringApiError> replayConflict() {
        return error(409, "IDEMPOTENCY_CONFLICT", "Operation key was already used for another request", List.of());
    }

    @ExceptionHandler(ProfileAlreadyExistsException.class)
    ResponseEntity<TutoringApiError> profileConflict() {
        return error(409, "PROFILE_ALREADY_EXISTS", "Profile already exists", List.of());
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<TutoringApiError> databaseFailure() {
        return error(503, "INTERNAL_ERROR", "Storage is temporarily unavailable", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<TutoringApiError> unexpected(Exception exception) {
        String id = UUID.randomUUID().toString();
        LOG.error("Tutoring request failed, requestId={}, exceptionType={}", id, exception.getClass().getName());
        return ResponseEntity.status(500).body(new TutoringApiError("INTERNAL_ERROR", "An internal error occurred", List.of(), id));
    }
}
