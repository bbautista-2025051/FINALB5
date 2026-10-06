package com.veterinaria.citas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veterinaria.citas.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ManejadorSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ManejadorSeguridad(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        escribir(response, request, HttpStatus.UNAUTHORIZED, "Autenticación requerida: token JWT ausente o inválido");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException
    ) throws IOException {
        escribir(response, request, HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta operación");
    }

    private void escribir(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String mensaje
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError cuerpo = ApiError.de(status.value(), status.name(), mensaje, request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}