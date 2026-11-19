package com.family.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Standardized Enterprise REST API Response Envelope.
 * Guarantees consistent response structures across all endpoints:
 * - statusCode: HTTP status code (200, 201, 400, 401, 403, 404, 500)
 * - status: "SUCCESS" or "FAILED"
 * - message: Human-readable message
 * - data: Payload object
 * - error: Error summary if failed, null on success
 * - timestamp: ISO-8601 formatted timestamp
 *
 * @param <T> Payload data type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardApiResponse<T> {

    private int statusCode;
    private String status; // "SUCCESS" or "FAILED"
    private String message;
    private T data;
    private String error;
    private LocalDateTime timestamp;

    public StandardApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public StandardApiResponse(int statusCode, String status, String message, T data, String error) {
        this.statusCode = statusCode;
        this.status = status;
        this.message = message;
        this.data = data;
        this.error = error;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> StandardApiResponse<T> success(int statusCode, String message, T data) {
        return new StandardApiResponse<>(statusCode, "SUCCESS", message, data, null);
    }

    public static <T> StandardApiResponse<T> success(String message, T data) {
        return success(200, message, data);
    }

    public static <T> StandardApiResponse<T> failed(int statusCode, String message, String error) {
        return new StandardApiResponse<>(statusCode, "FAILED", message, null, error);
    }

    public static <T> StandardApiResponse<T> failed(int statusCode, String message) {
        return failed(statusCode, message, message);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
