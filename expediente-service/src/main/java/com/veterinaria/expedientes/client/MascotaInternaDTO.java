package com.veterinaria.expedientes.client;

public record MascotaInternaDTO(
        Long id,
        String nombre,
        String especie,
        String raza,
        Integer edad,
        Long clienteId,
        String clienteNombre
) {
}
