package io.github.edtechdevelopment.identity.presentation.error.handler;

import io.github.edtechdevelopment.identity.application.exception.AccountOperationNotAllowedException;
import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.exception.EmailVerificationRequiredException;
import io.github.edtechdevelopment.identity.application.exception.InvalidCredentialsException;
import io.github.edtechdevelopment.identity.application.exception.InvalidRefreshTokenException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.exception.InvalidVerificationTokenException;
import io.github.edtechdevelopment.identity.application.exception.RefreshAccessDeniedException;
import io.github.edtechdevelopment.identity.presentation.auth.cookie.RefreshTokenCookieFactory;
import io.github.edtechdevelopment.identity.presentation.auth.controller.AuthController;
import io.github.edtechdevelopment.identity.presentation.account.controller.CurrentUserController;
import io.github.edtechdevelopment.identity.presentation.error.model.ApiError;
import io.github.edtechdevelopment.identity.presentation.error.model.ErrorCode;
import io.github.edtechdevelopment.identity.presentation.error.model.FieldErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestControllerAdvice(basePackageClasses = {AuthController.class, CurrentUserController.class})
public final class IdentityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityExceptionHandler.class);

    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public IdentityExceptionHandler(RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.refreshTokenCookieFactory = Objects.requireNonNull(
                refreshTokenCookieFactory,
                "Refresh token cookie factory must not be null"
        );
    }

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

    @ExceptionHandler(InvalidVerificationTokenException.class)
    ResponseEntity<ApiError> handleInvalidVerificationToken(InvalidVerificationTokenException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_VERIFICATION_TOKEN,
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_CREDENTIALS,
                exception.getMessage()
        );
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    ResponseEntity<ApiError> handleEmailVerificationRequired(EmailVerificationRequiredException exception) {
        return errorResponse(
                HttpStatus.FORBIDDEN,
                ErrorCode.EMAIL_NOT_VERIFIED,
                exception.getMessage()
        );
    }

    @ExceptionHandler(AccountOperationNotAllowedException.class)
    ResponseEntity<ApiError> handleAccountOperationNotAllowed(AccountOperationNotAllowedException exception) {
        return errorResponse(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return refreshErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_REFRESH_TOKEN,
                exception.getMessage()
        );
    }

    @ExceptionHandler(RefreshAccessDeniedException.class)
    ResponseEntity<ApiError> handleRefreshAccessDenied(RefreshAccessDeniedException exception) {
        return refreshErrorResponse(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
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

    private ResponseEntity<ApiError> refreshErrorResponse(
            HttpStatus status,
            ErrorCode code,
            String message
    ) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .body(new ApiError(code, message, List.of(), newRequestId()));
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
