package com.veterinaria.citas.dto;

import com.veterinaria.citas.entity.CitaMedica;
import com.veterinaria.citas.enums.EstadoCita;
import java.time.LocalDateTime;

public record CitaDTO(
        Long id,
        Long mascotaId,
        String mascotaNombre,
        Long clienteId,
        Long veterinarioId,
        String veterinarioNombre,
        LocalDateTime fechaHora,
        String motivo,
        EstadoCita estado
) {

    public static CitaDTO desde(CitaMedica cita) {
        return new CitaDTO(
                cita.getId(),
                cita.getMascotaId(),
                cita.getMascotaNombre(),
                cita.getClienteId(),
                cita.getVeterinarioId(),
                cita.getVeterinarioNombre(),
                cita.getFechaHora(),
                cita.getMotivo(),
                cita.getEstado()
        );
    }
}