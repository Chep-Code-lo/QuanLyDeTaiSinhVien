package com.example.qldetai.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.qldetai.common.i18n.ErrorMessages;
import java.time.Instant;

/**
 * Standard envelope returned by the REST API.
 *
 * @param success whether the request completed successfully
 * @param message short, user-facing result description
 * @param data response payload; omitted when not applicable
 * @param timestamp time at which the response was created (UTC)
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {
    public static <T> ApiResponse<T> success(T data) {
        return success("Request completed successfully.", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static ApiResponse<Void> failure(String message) {
        return new ApiResponse<>(false, ErrorMessages.vietnamese(message), null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(String message, T data) {
        return new ApiResponse<>(false, ErrorMessages.vietnamese(message), data, Instant.now());
    }
}
