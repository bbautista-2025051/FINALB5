package com.veterinaria.mascotas.security;

public record UsuarioAutenticado(
        Long id,
        String email,
        String rol
) {
}
