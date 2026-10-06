package com.veterinaria.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veterinaria.auth.client.UsuarioClient;
import com.veterinaria.auth.dto.LoginRequest;
import com.veterinaria.auth.dto.RegistroRequest;
import com.veterinaria.auth.dto.UsuarioDTO;
import com.veterinaria.auth.dto.UsuarioInternoRequest;
import com.veterinaria.auth.enums.Rol;
import com.veterinaria.auth.exception.ConflictoException;
import com.veterinaria.auth.exception.CredencialesInvalidasException;
import com.veterinaria.auth.exception.RecursoNoEncontradoException;
import com.veterinaria.auth.exception.ServicioNoDisponibleException;
import com.veterinaria.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioClient usuarioClient;

    private static final Long CLIENTE_ID = 42L;
    private static final String CLIENTE_EMAIL = "cliente@test.com";

    private UsuarioDTO usuarioCliente() {
        return new UsuarioDTO(CLIENTE_ID, "Cliente Test", "3001112222", CLIENTE_EMAIL, Rol.CLIENTE);
    }

    private String tokenValido() {
        return jwtService.generarToken(CLIENTE_ID, CLIENTE_EMAIL, "CLIENTE");
    }

    private RegistroRequest registro(String email) {
        return new RegistroRequest("Cliente Test", "3001112222", email, "Cliente123!", null);
    }

    @Test
    void registroCreaClienteYDevuelveToken() throws Exception {
        whenCrearDevuelve(usuarioCliente());

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro(CLIENTE_EMAIL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        ArgumentCaptor<UsuarioInternoRequest> captor = ArgumentCaptor.forClass(UsuarioInternoRequest.class);
        org.mockito.Mockito.verify(usuarioClient).crear(captor.capture());
        assertThat(captor.getValue().rol()).isEqualTo(Rol.CLIENTE);
        assertThat(captor.getValue().email()).isEqualTo(CLIENTE_EMAIL);
    }

    @Test
    void registroIgnoraElRolEnviadoEnElBody() throws Exception {
        whenCrearDevuelve(usuarioCliente());
        String cuerpo = "{\"nombre\":\"Cliente Test\",\"telefono\":\"3001112222\","
                + "\"email\":\"" + CLIENTE_EMAIL + "\",\"password\":\"Cliente123!\",\"rol\":\"ADMIN\"}";

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());

        ArgumentCaptor<UsuarioInternoRequest> captor = ArgumentCaptor.forClass(UsuarioInternoRequest.class);
        org.mockito.Mockito.verify(usuarioClient).crear(captor.capture());
        assertThat(captor.getValue().rol()).isEqualTo(Rol.CLIENTE);
    }

    @Test
    void registroConEmailDuplicadoDevuelve409() throws Exception {
        org.mockito.Mockito.when(usuarioClient.crear(org.mockito.ArgumentMatchers.any(UsuarioInternoRequest.class)))
                .thenThrow(new ConflictoException("El email ya está registrado"));

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro("duplicado@test.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void registroConDatosInvalidosDevuelve400ConDetalles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"email\":\"no-es-email\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void registroConUserServiceCaidoDevuelve503() throws Exception {
        org.mockito.Mockito.when(usuarioClient.crear(org.mockito.ArgumentMatchers.any(UsuarioInternoRequest.class)))
                .thenThrow(new ServicioNoDisponibleException("user-service no disponible"));

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro(CLIENTE_EMAIL))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Servicio temporalmente no disponible"));
    }

    @Test
    void loginCorrectoDevuelve200ConToken() throws Exception {
        org.mockito.Mockito.when(usuarioClient.verificarCredenciales(CLIENTE_EMAIL, "Cliente123!"))
                .thenReturn(usuarioCliente());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(CLIENTE_EMAIL, "Cliente123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    void loginConPasswordIncorrectaDevuelve401() throws Exception {
        org.mockito.Mockito.when(usuarioClient.verificarCredenciales(CLIENTE_EMAIL, "mala"))
                .thenThrow(new CredencialesInvalidasException("Credenciales inválidas"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(CLIENTE_EMAIL, "mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void loginConDatosInvalidosDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void meSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void meConTokenBasuraDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer token-basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevuelveElUsuarioDelToken() throws Exception {
        org.mockito.Mockito.when(usuarioClient.obtener(CLIENTE_ID)).thenReturn(usuarioCliente());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tokenValido()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CLIENTE_ID))
                .andExpect(jsonPath("$.email").value(CLIENTE_EMAIL))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void meConUsuarioEliminadoDevuelve404() throws Exception {
        org.mockito.Mockito.when(usuarioClient.obtener(CLIENTE_ID))
                .thenThrow(new RecursoNoEncontradoException("El usuario ya no existe"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tokenValido()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private void whenCrearDevuelve(UsuarioDTO usuario) {
        org.mockito.Mockito.when(usuarioClient.crear(org.mockito.ArgumentMatchers.any(UsuarioInternoRequest.class)))
                .thenReturn(usuario);
    }
}
