package com.veterinaria.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiError> credencialesInvalidas(CredencialesInvalidasException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ApiError> solicitudInvalida(SolicitudInvalidaException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ApiError> servicioNoDisponible(ServicioNoDisponibleException ex, HttpServletRequest req) {
        log.warn("Servicio dependiente no disponible: {}", ex.getMessage());
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "Servicio temporalmente no disponible", req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> malasCredenciales(BadCredentialsException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta operación", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.DetalleError> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(this::aDetalle)
                .toList();
        return ResponseEntity.badRequest().body(ApiError.conDetalles(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                "Validación fallida",
                req.getRequestURI(),
                detalles
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido o ilegible", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "Parámetro '" + ex.getName() + "' con valor inválido", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> rutaNoEncontrada(NoResourceFoundException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodoNoSoportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED,
                "Método " + ex.getMethod() + " no soportado para esta ruta", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> tipoContenidoNoSoportado(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de contenido no soportado", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> parametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "Falta el parámetro '" + ex.getParameterName() + "'", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req);
    }

    private ApiError.DetalleError aDetalle(FieldError error) {
        return new ApiError.DetalleError(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiError.de(status.value(), status.name(), mensaje, req.getRequestURI()));
    }
}
