package com.veterinaria.expedientes.client;

import java.time.LocalDateTime;

public record CitaInternaDTO(
        Long id,
        Long mascotaId,
        String mascotaNombre,
        Long clienteId,
        Long veterinarioId,
        String veterinarioNombre,
        LocalDateTime fechaHora,
        String motivo,
        String estado
) {
}
