package com.veterinaria.citas.controller;

import com.veterinaria.citas.dto.CitaDTO;
import com.veterinaria.citas.dto.CrearCitaRequest;
import com.veterinaria.citas.security.UsuarioAutenticado;
import com.veterinaria.citas.service.CitaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
    public CitaDTO crear(
            @Valid @RequestBody CrearCitaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        return citaService.crear(request, usuario);
    }

    @GetMapping("/agenda")
    @PreAuthorize("hasAnyRole('VET','ADMIN')")
    public Page<CitaDTO> agenda(
            @RequestParam(required = false) Long veterinarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        return citaService.agenda(veterinarioId, fecha, pageable, usuario);
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
    public CitaDTO cancelar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        return citaService.cancelar(id, usuario);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CitaDTO obtener(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        return citaService.obtener(id, usuario);
    }
}