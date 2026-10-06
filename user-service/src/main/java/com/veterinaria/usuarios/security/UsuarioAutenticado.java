package com.veterinaria.usuarios.security;

public record UsuarioAutenticado(
        Long id,
        String email,
        String rol
) {
}
