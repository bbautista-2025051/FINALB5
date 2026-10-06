package com.veterinaria.expedientes.controller;

import com.veterinaria.expedientes.dto.CrearExpedienteRequest;
import com.veterinaria.expedientes.dto.ExpedienteDTO;
import com.veterinaria.expedientes.security.UsuarioAutenticado;
import com.veterinaria.expedientes.service.ExpedienteService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expedientes")
@RequiredArgsConstructor
public class ExpedienteController {

    private final ExpedienteService expedienteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VET','ADMIN')")
    public ExpedienteDTO crear(
            @Valid @RequestBody CrearExpedienteRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor
    ) {
        return expedienteService.crear(request, actor);
    }

    @GetMapping("/mascotas/{mascotaId}")
    @PreAuthorize("isAuthenticated()")
    public Page<ExpedienteDTO> historial(
            @PathVariable Long mascotaId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor
    ) {
        return expedienteService.historial(mascotaId, pageable, actor);
    }
}
