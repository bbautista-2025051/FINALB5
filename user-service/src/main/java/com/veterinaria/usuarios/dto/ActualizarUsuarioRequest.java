package com.veterinaria.usuarios.dto;

import com.veterinaria.usuarios.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarUsuarioRequest(
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        String nombre,

        @Pattern(regexp = "^\\+?[0-9()\\-\\s]{7,20}$", message = "El teléfono no tiene un formato válido")
        String telefono,

        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres")
        String email,

        Rol rol,

        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password
) {
}
