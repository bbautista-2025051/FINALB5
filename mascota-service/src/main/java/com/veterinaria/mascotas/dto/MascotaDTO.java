package com.veterinaria.mascotas.dto;

import com.veterinaria.mascotas.entity.Mascota;
import com.veterinaria.mascotas.enums.Especie;

public record MascotaDTO(
        Long id,
        String nombre,
        Especie especie,
        String raza,
        Integer edad,
        Long clienteId,
        String clienteNombre
) {

    public static MascotaDTO desde(Mascota mascota) {
        return new MascotaDTO(
                mascota.getId(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getEdad(),
                mascota.getClienteId(),
                mascota.getClienteNombre()
        );
    }
}
