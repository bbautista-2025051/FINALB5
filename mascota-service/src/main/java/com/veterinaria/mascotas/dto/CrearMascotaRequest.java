package com.veterinaria.mascotas.dto;

import com.veterinaria.mascotas.enums.Especie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CrearMascotaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        String nombre,

        @NotNull(message = "La especie es obligatoria")
        Especie especie,

        @Size(max = 100, message = "La raza no puede exceder 100 caracteres")
        String raza,

        @NotNull(message = "La edad es obligatoria")
        @PositiveOrZero(message = "La edad no puede ser negativa")
        Integer edad,

        @Positive(message = "El clienteId debe ser positivo")
        Long clienteId
) {
}
