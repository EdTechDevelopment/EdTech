package io.github.edtechdevelopment.identity.presentation.error.handler;

import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.presentation.auth.controller.AuthController;
import io.github.edtechdevelopment.identity.presentation.error.model.ApiError;
import io.github.edtechdevelopment.identity.presentation.error.model.ErrorCode;
import io.github.edtechdevelopment.identity.presentation.error.model.FieldErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public final class IdentityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(IdentityExceptionHandler::toFieldErrorResponse)
                .sorted(Comparator.comparing(FieldErrorResponse::field))
                .toList();

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request body is malformed or contains an unsupported value"
        );
    }

    @ExceptionHandler(InvalidUseCaseInputException.class)
    ResponseEntity<ApiError> handleInvalidUseCaseInput(InvalidUseCaseInputException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                exception.getMessage()
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ApiError> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        return errorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.EMAIL_ALREADY_EXISTS,
                exception.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        String requestId = newRequestId();
        LOGGER.error("Unexpected Identity API error, requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected internal error occurred",
                List.of(),
                requestId
        ));
    }

    private static ResponseEntity<ApiError> errorResponse(
            HttpStatus status,
            ErrorCode code,
            String message
    ) {
        return errorResponse(status, code, message, List.of());
    }

    private static ResponseEntity<ApiError> errorResponse(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<FieldErrorResponse> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new ApiError(code, message, fieldErrors, newRequestId()));
    }

    private static FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new FieldErrorResponse(
                fieldError.getField(),
                fieldError.getCode(),
                fieldError.getDefaultMessage()
        );
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
