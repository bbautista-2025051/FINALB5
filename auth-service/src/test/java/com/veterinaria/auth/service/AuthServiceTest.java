package com.veterinaria.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.veterinaria.auth.client.UsuarioClient;
import com.veterinaria.auth.dto.LoginRequest;
import com.veterinaria.auth.dto.LoginResponse;
import com.veterinaria.auth.dto.RegistroRequest;
import com.veterinaria.auth.dto.UsuarioDTO;
import com.veterinaria.auth.dto.UsuarioInternoRequest;
import com.veterinaria.auth.enums.Rol;
import com.veterinaria.auth.exception.ConflictoException;
import com.veterinaria.auth.exception.CredencialesInvalidasException;
import com.veterinaria.auth.exception.ServicioNoDisponibleException;
import com.veterinaria.auth.security.JwtService;
import com.veterinaria.auth.security.UsuarioAutenticado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioClient usuarioClient;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private static final UsuarioDTO USUARIO = new UsuarioDTO(7L, "Cliente Test", "3001112222",
            "cliente@test.com", Rol.CLIENTE);

    private RegistroRequest registro() {
        return new RegistroRequest("Cliente Test", "3001112222", "cliente@test.com", "Cliente123!", null);
    }

    @Test
    void registroSiemprCreaClienteAunqueElRolDelBodyDigaOtraCosa() {
        RegistroRequest conRolDeAdmin = new RegistroRequest("Cliente Test", "3001112222",
                "cliente@test.com", "Cliente123!", "ADMIN");
        when(usuarioClient.crear(any(UsuarioInternoRequest.class))).thenReturn(USUARIO);
        when(jwtService.generarToken(7L, "cliente@test.com", "CLIENTE")).thenReturn("token-registro");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        LoginResponse respuesta = authService.registrar(conRolDeAdmin);

        ArgumentCaptor<UsuarioInternoRequest> captor = ArgumentCaptor.forClass(UsuarioInternoRequest.class);
        verify(usuarioClient).crear(captor.capture());
        assertThat(captor.getValue().rol()).isEqualTo(Rol.CLIENTE);
        assertThat(respuesta.token()).isEqualTo("token-registro");
        assertThat(respuesta.type()).isEqualTo("Bearer");
        assertThat(respuesta.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void registroConEmailRepetidoPropagaConflicto() {
        when(usuarioClient.crear(any(UsuarioInternoRequest.class)))
                .thenThrow(new ConflictoException("El email ya está registrado"));

        assertThatThrownBy(() -> authService.registrar(registro()))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("El email ya está registrado");
    }

    @Test
    void registroConUserServiceCaidoPropagaServicioNoDisponible() {
        when(usuarioClient.crear(any(UsuarioInternoRequest.class)))
                .thenThrow(new ServicioNoDisponibleException("user-service no disponible"));

        assertThatThrownBy(() -> authService.registrar(registro()))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void loginEmiteTokenConClaimsDelUsuario() {
        when(usuarioClient.verificarCredenciales("cliente@test.com", "Cliente123!")).thenReturn(USUARIO);
        when(jwtService.generarToken(7L, "cliente@test.com", "CLIENTE")).thenReturn("token-login");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        LoginResponse respuesta = authService.iniciarSesion(
                new LoginRequest("cliente@test.com", "Cliente123!"));

        assertThat(respuesta.token()).isEqualTo("token-login");
        assertThat(respuesta.type()).isEqualTo("Bearer");
        assertThat(respuesta.expiresIn()).isEqualTo(3600L);
        verify(usuarioClient).verificarCredenciales("cliente@test.com", "Cliente123!");
    }

    @Test
    void loginConPasswordIncorrectaPropagaCredencialesInvalidas() {
        when(usuarioClient.verificarCredenciales("cliente@test.com", "mala"))
                .thenThrow(new CredencialesInvalidasException("Credenciales inválidas"));

        assertThatThrownBy(() -> authService.iniciarSesion(
                new LoginRequest("cliente@test.com", "mala")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void obtenerActualConsultaElUsuarioPorIdDelToken() {
        when(usuarioClient.obtener(7L)).thenReturn(USUARIO);

        UsuarioDTO resultado = authService.obtenerActual(
                new UsuarioAutenticado(7L, "cliente@test.com", "CLIENTE"));

        assertThat(resultado.email()).isEqualTo("cliente@test.com");
        verify(usuarioClient).obtener(7L);
    }
}
