package com.veterinaria.usuarios.dto;

import com.veterinaria.usuarios.entity.Usuario;
import com.veterinaria.usuarios.enums.Rol;

public record UsuarioDTO(
        Long id,
        String nombre,
        String telefono,
        String email,
        Rol rol
) {

    public static UsuarioDTO desde(Usuario usuario) {
        return new UsuarioDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getTelefono(),
                usuario.getEmail(),
                usuario.getRol()
        );
    }
}
