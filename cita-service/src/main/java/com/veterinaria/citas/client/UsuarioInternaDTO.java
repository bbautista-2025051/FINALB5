package com.veterinaria.citas.client;

public record UsuarioInternaDTO(
        Long id,
        String nombre,
        String telefono,
        String email,
        String rol
) {
}