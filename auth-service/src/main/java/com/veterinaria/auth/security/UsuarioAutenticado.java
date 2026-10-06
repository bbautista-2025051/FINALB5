package com.veterinaria.auth.security;

public record UsuarioAutenticado(
        Long id,
        String email,
        String rol
) {
}
