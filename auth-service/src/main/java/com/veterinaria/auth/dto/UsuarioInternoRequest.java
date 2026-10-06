package com.veterinaria.auth.dto;

import com.veterinaria.auth.enums.Rol;

public record UsuarioInternoRequest(
        String nombre,
        String telefono,
        String email,
        String password,
        Rol rol
) {
}
