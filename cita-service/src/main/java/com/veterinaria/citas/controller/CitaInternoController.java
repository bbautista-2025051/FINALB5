package com.veterinaria.citas.controller;

import com.veterinaria.citas.dto.CitaDTO;
import com.veterinaria.citas.service.CitaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/citas")
@RequiredArgsConstructor
public class CitaInternoController {

    private final CitaService citaService;

    @GetMapping("/{id}")
    public CitaDTO obtener(@PathVariable Long id) {
        return citaService.obtenerInterno(id);
    }

    @PostMapping("/{id}/completar")
    public CitaDTO completar(@PathVariable Long id) {
        return citaService.completar(id);
    }
}