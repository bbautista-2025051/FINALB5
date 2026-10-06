package com.veterinaria.expedientes.client;

public interface CitaClient {

    CitaInternaDTO obtener(Long id);

    CitaInternaDTO completar(Long id);
}
