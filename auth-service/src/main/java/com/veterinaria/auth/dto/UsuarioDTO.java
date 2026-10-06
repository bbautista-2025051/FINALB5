package com.veterinaria.auth.dto;

import com.veterinaria.auth.enums.Rol;

public record UsuarioDTO(
        Long id,
        String nombre,
        String telefono,
        String email,
        Rol rol
) {
}
