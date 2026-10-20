package com.example.qldetai.exception;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.i18n.ErrorMessages;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Converts common request errors to the application's consistent API envelope.
 * Business-specific exceptions can be added here as features are implemented.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
        MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception
            .getBindingResult()
            .getFieldErrors()
            .forEach(error -> fieldErrors.putIfAbsent(error.getField(), ErrorMessages.vietnamese(error.getDefaultMessage())));

        return ResponseEntity.badRequest().body(
            ApiResponse.failure("Request validation failed.", fieldErrors)
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
        ConstraintViolationException exception
    ) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception
            .getConstraintViolations()
            .forEach(violation ->
                violations.putIfAbsent(
                    violation.getPropertyPath().toString(),
                    ErrorMessages.vietnamese(violation.getMessage())
                )
            );

        return ResponseEntity.badRequest().body(
            ApiResponse.failure("Request validation failed.", violations)
        );
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, IllegalArgumentException.class })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(
            ApiResponse.failure("The request payload is invalid.")
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponse.failure(exception.getMessage())
        );
    }

    @ExceptionHandler({ ConflictException.class, DataIntegrityViolationException.class })
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception exception) {
        String message =
            exception instanceof ConflictException
                ? exception.getMessage()
                : "The requested change conflicts with existing data.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(message));
    }

    @ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiResponse.failure("Email or password is incorrect.")
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiResponse.failure("You do not have permission to perform this action.")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse.failure("An unexpected server error occurred.")
        );
    }
}
