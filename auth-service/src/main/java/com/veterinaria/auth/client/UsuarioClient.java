package com.veterinaria.auth.client;

import com.veterinaria.auth.dto.UsuarioDTO;
import com.veterinaria.auth.dto.UsuarioInternoRequest;

public interface UsuarioClient {

    UsuarioDTO verificarCredenciales(String email, String password);

    UsuarioDTO crear(UsuarioInternoRequest request);

    UsuarioDTO obtener(Long id);
}
