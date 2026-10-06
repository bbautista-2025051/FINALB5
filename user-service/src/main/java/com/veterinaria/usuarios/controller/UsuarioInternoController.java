package com.veterinaria.usuarios.controller;

import com.veterinaria.usuarios.dto.CrearUsuarioRequest;
import com.veterinaria.usuarios.dto.UsuarioDTO;
import com.veterinaria.usuarios.dto.VerificarCredencialesRequest;
import com.veterinaria.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/usuarios")
@RequiredArgsConstructor
public class UsuarioInternoController {

    private final UsuarioService usuarioService;

    @PostMapping("/verificar-credenciales")
    public UsuarioDTO verificarCredenciales(@Valid @RequestBody VerificarCredencialesRequest request) {
        return usuarioService.verificarCredenciales(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDTO crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return usuarioService.crear(request);
    }

    @GetMapping("/{id}")
    public UsuarioDTO obtener(@PathVariable Long id) {
        return usuarioService.obtener(id);
    }
}
