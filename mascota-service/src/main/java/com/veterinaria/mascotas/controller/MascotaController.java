package com.veterinaria.mascotas.controller;

import com.veterinaria.mascotas.dto.CrearMascotaRequest;
import com.veterinaria.mascotas.dto.MascotaDTO;
import com.veterinaria.mascotas.enums.Especie;
import com.veterinaria.mascotas.security.UsuarioAutenticado;
import com.veterinaria.mascotas.service.MascotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mascotas")
@RequiredArgsConstructor
public class MascotaController {

    private final MascotaService mascotaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
    public MascotaDTO crear(
            @Valid @RequestBody CrearMascotaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor
    ) {
        return mascotaService.crear(request, actor);
    }

    @GetMapping("/mis-mascotas")
    @PreAuthorize("hasRole('CLIENTE')")
    public Page<MascotaDTO> misMascotas(
            @AuthenticationPrincipal UsuarioAutenticado actor,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return mascotaService.misMascotas(actor.id(), pageable);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<MascotaDTO> listar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Especie especie,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return mascotaService.listar(clienteId, especie, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VET','ADMIN')")
    public MascotaDTO obtener(@PathVariable Long id) {
        return mascotaService.obtener(id);
    }
}
