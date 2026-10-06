package com.veterinaria.mascotas.controller;

import com.veterinaria.mascotas.dto.MascotaDTO;
import com.veterinaria.mascotas.service.MascotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mascotas")
@RequiredArgsConstructor
public class MascotaInternoController {

    private final MascotaService mascotaService;

    @GetMapping("/{id}")
    public MascotaDTO obtener(@PathVariable Long id) {
        return mascotaService.obtener(id);
    }
}
