package com.veterinaria.citas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CrearCitaRequest(
        @NotNull(message = "La mascota es obligatoria")
        @Positive(message = "El id de la mascota debe ser positivo")
        Long mascotaId,

        @NotNull(message = "El veterinario es obligatorio")
        @Positive(message = "El id del veterinario debe ser positivo")
        Long veterinarioId,

        @NotNull(message = "La fecha y hora es obligatoria")
        LocalDateTime fechaHora,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
        String motivo
) {
}