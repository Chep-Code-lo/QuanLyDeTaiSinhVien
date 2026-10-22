package com.example.qldetai.exception;

import com.example.qldetai.common.api.ApiResponse;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts common request errors to the application's consistent API envelope.
 * Business-specific exceptions can be added here as features are implemented.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure("Tệp quá lớn. Tối đa 20 MB."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(
                        error ->
                                fieldErrors.putIfAbsent(
                                        error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.",
                                fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception
                .getConstraintViolations()
                .forEach(
                        violation ->
                                violations.putIfAbsent(
                                        violation.getPropertyPath().toString(),
                                        violation.getMessage()));

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.",
                                violations));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class,
        ServletRequestBindingException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại."));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(
            TooManyRequestsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception exception) {
        String message =
                exception instanceof ConflictException
                        ? exception.getMessage()
                        : "Thay đổi bị trùng hoặc xung đột với dữ liệu hiện có.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(message));
    }

    @ExceptionHandler({
        ConcurrencyFailureException.class,
        OptimisticLockException.class,
        PessimisticLockException.class,
        LockTimeoutException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConcurrentChange(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.failure(
                                "Dữ liệu đã được thay đổi bởi yêu cầu khác. Vui lòng tải lại và thử lại."));
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("Email hoặc mật khẩu không chính xác."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("Bạn không có quyền thực hiện thao tác này."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error(
                "Unhandled request failure type={} requestId={}",
                exception.getClass().getName(),
                org.slf4j.MDC.get("requestId"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("Hệ thống gặp lỗi. Vui lòng thử lại sau."));
    }
}
