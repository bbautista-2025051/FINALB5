package com.veterinaria.expedientes.dto;

import com.veterinaria.expedientes.entity.ExpedienteClinico;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpedienteDTO(
        Long id,
        Long citaId,
        Long mascotaId,
        String diagnostico,
        String tratamiento,
        BigDecimal pesoKg,
        LocalDateTime fechaRegistro
) {

    public static ExpedienteDTO desde(ExpedienteClinico expediente) {
        return new ExpedienteDTO(
                expediente.getId(),
                expediente.getCitaId(),
                expediente.getMascotaId(),
                expediente.getDiagnostico(),
                expediente.getTratamiento(),
                expediente.getPesoKg(),
                expediente.getFechaRegistro()
        );
    }
}
