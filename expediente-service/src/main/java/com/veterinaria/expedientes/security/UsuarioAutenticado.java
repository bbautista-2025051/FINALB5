package com.veterinaria.expedientes.security;

public record UsuarioAutenticado(
        Long id,
        String email,
        String rol
) {
}
