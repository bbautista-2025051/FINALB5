package com.veterinaria.auth.service;

import com.veterinaria.auth.client.UsuarioClient;
import com.veterinaria.auth.dto.LoginRequest;
import com.veterinaria.auth.dto.LoginResponse;
import com.veterinaria.auth.dto.RegistroRequest;
import com.veterinaria.auth.dto.UsuarioDTO;
import com.veterinaria.auth.dto.UsuarioInternoRequest;
import com.veterinaria.auth.enums.Rol;
import com.veterinaria.auth.security.JwtService;
import com.veterinaria.auth.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioClient usuarioClient;
    private final JwtService jwtService;

    public LoginResponse registrar(RegistroRequest request) {
        UsuarioInternoRequest interno = new UsuarioInternoRequest(
                request.nombre(),
                request.telefono(),
                request.email(),
                request.password(),
                Rol.CLIENTE
        );
        UsuarioDTO usuario = usuarioClient.crear(interno);
        return emitir(usuario);
    }

    public LoginResponse iniciarSesion(LoginRequest request) {
        UsuarioDTO usuario = usuarioClient.verificarCredenciales(request.email(), request.password());
        return emitir(usuario);
    }

    public UsuarioDTO obtenerActual(UsuarioAutenticado actor) {
        return usuarioClient.obtener(actor.id());
    }

    private LoginResponse emitir(UsuarioDTO usuario) {
        String token = jwtService.generarToken(usuario.id(), usuario.email(), usuario.rol().name());
        return LoginResponse.conToken(token, jwtService.getExpirationMs());
    }
}
