package com.veterinaria.mascotas.client;

public record UsuarioInternaDTO(
        Long id,
        String nombre,
        String telefono,
        String email,
        String rol
) {
}
