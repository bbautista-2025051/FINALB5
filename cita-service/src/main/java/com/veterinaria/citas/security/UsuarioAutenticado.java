package com.veterinaria.citas.security;

public record UsuarioAutenticado(
        Long id,
        String email,
        String rol
) {
}