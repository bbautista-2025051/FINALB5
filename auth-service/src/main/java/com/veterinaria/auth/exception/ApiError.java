package com.veterinaria.auth.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<DetalleError> details
) {

    public record DetalleError(String field, String message) {
    }

    public static ApiError de(int status, String error, String mensaje, String path) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje, path, null);
    }

    public static ApiError conDetalles(int status, String error, String mensaje, String path, List<DetalleError> detalles) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje, path, detalles);
    }
}
