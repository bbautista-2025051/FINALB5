package com.veterinaria.usuarios.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    public static final String ROL_SERVICIO = "ROLE_SERVICE";

    private static final Logger log = LoggerFactory.getLogger(InternalKeyFilter.class);

    private final byte[] claveEsperada;

    public InternalKeyFilter(@Value("${veterinaria.security.internal-key}") String claveInterna) {
        if (claveInterna == null || claveInterna.isBlank()) {
            throw new IllegalStateException("INTERNAL_SERVICE_KEY no configurado");
        }
        this.claveEsperada = claveInterna.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String ruta = request.getRequestURI();
        if (ruta == null || !ruta.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String recibida = request.getHeader(HEADER);
        if (recibida != null && MessageDigest.isEqual(
                recibida.getBytes(StandardCharsets.UTF_8), claveEsperada)) {
            UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                    "servicio-interno",
                    null,
                    List.of(new SimpleGrantedAuthority(ROL_SERVICIO))
            );
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        } else {
            log.warn("Intento de acceso interno sin clave válida a {}", ruta);
        }

        filterChain.doFilter(request, response);
    }
}
