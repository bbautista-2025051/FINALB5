package com.veterinaria.expedientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CrearExpedienteRequest(
        @NotNull(message = "El id de la cita es obligatorio")
        @Positive(message = "El id de la cita debe ser positivo")
        Long citaId,

        @NotBlank(message = "El diagnostico es obligatorio")
        @Size(max = 1000, message = "El diagnostico no puede exceder 1000 caracteres")
        String diagnostico,

        @NotBlank(message = "El tratamiento es obligatorio")
        @Size(max = 1000, message = "El tratamiento no puede exceder 1000 caracteres")
        String tratamiento,

        @NotNull(message = "El peso es obligatorio")
        @Positive(message = "El peso debe ser mayor que cero")
        BigDecimal pesoKg
) {
}
