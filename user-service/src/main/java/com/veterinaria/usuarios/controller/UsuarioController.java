package com.veterinaria.usuarios.controller;

import com.veterinaria.usuarios.dto.ActualizarUsuarioRequest;
import com.veterinaria.usuarios.dto.CrearUsuarioRequest;
import com.veterinaria.usuarios.dto.UsuarioDTO;
import com.veterinaria.usuarios.dto.VeterinarioDTO;
import com.veterinaria.usuarios.enums.Rol;
import com.veterinaria.usuarios.security.UsuarioAutenticado;
import com.veterinaria.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioDTO crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return usuarioService.crear(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UsuarioDTO> listar(
            @RequestParam(required = false) Rol rol,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return usuarioService.listar(rol, pageable);
    }

    @GetMapping("/veterinarios")
    public Page<VeterinarioDTO> veterinarios(@PageableDefault(size = 50) Pageable pageable) {
        return usuarioService.veterinarios(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioDTO obtener(@PathVariable Long id) {
        return usuarioService.obtener(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioDTO actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request
    ) {
        return usuarioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        usuarioService.eliminar(id, usuario.id());
        return ResponseEntity.noContent().build();
    }
}
