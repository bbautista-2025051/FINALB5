package com.veterinaria.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey clave;
    private final long expirationMs;

    public JwtService(
            @Value("${veterinaria.security.jwt.secret}") String secreto,
            @Value("${veterinaria.security.jwt.expiration-ms}") long expirationMs
    ) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generarToken(Long id, String email, String rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(expirationMs)))
                .signWith(clave)
                .compact();
    }

    public Optional<Claims> parsear(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
