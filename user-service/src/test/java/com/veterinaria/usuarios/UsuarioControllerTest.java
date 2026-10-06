package com.veterinaria.usuarios;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veterinaria.usuarios.dto.CrearUsuarioRequest;
import com.veterinaria.usuarios.dto.VerificarCredencialesRequest;
import com.veterinaria.usuarios.enums.Rol;
import com.veterinaria.usuarios.security.JwtService;
import com.veterinaria.usuarios.service.UsuarioService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioService usuarioService;

    private static final String CLAVE_INTERNA = "clave-interna-de-prueba";

    private Long adminId;
    private Long clienteId;
    private String adminEmail;
    private String clienteEmail;

    @BeforeEach
    void prepararUsuarios() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin-" + sufijo + "@test.com";
        clienteEmail = "cliente-" + sufijo + "@test.com";
        adminId = usuarioService.crear(new CrearUsuarioRequest(
                "Admin Test", "3001110000", adminEmail, "Admin123!", Rol.ADMIN)).id();
        clienteId = usuarioService.crear(new CrearUsuarioRequest(
                "Cliente Test", "3002220000", clienteEmail, "Cliente123!", Rol.CLIENTE)).id();
    }

    private String tokenDe(Long id, String email, Rol rol) {
        return jwtService.generarToken(id, email, rol.name());
    }

    @Test
    void endpointProtegidoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void endpointProtegidoConTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer token-basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoConRolIncorrectoDevuelve403() throws Exception {
        String token = tokenDe(clienteId, clienteEmail, Rol.CLIENTE);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void adminListaUsuariosPaginados() throws Exception {
        String token = tokenDe(adminId, adminEmail, Rol.ADMIN);

        mockMvc.perform(get("/api/v1/usuarios?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void adminCreaUsuarioYElRolSeRespeta() throws Exception {
        String token = tokenDe(adminId, adminEmail, Rol.ADMIN);
        CrearUsuarioRequest request = new CrearUsuarioRequest(
                "Nuevo Vet", "3009998888", "vet-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com",
                "Vet12345!", Rol.VET);

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("VET"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void crearUsuarioConEmailDuplicadoDevuelve409() throws Exception {
        String token = tokenDe(adminId, adminEmail, Rol.ADMIN);
        String email = "duplicado-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        usuarioService.crear(new CrearUsuarioRequest("Repetido", "3001110000", email, "Admin123!", Rol.CLIENTE));

        CrearUsuarioRequest duplicado = new CrearUsuarioRequest("Repetido", "3001110000", email, "Admin123!", Rol.CLIENTE);

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void crearUsuarioConDatosInvalidosDevuelve400ConDetalles() throws Exception {
        String token = tokenDe(adminId, adminEmail, Rol.ADMIN);

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"email\":\"no-es-email\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void veterinariosEsVisibleParaCualquierRolAutenticado() throws Exception {
        String token = tokenDe(clienteId, clienteEmail, Rol.CLIENTE);

        mockMvc.perform(get("/api/v1/usuarios/veterinarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminEliminaUsuarioAjeno() throws Exception {
        String token = tokenDe(adminId, adminEmail, Rol.ADMIN);

        mockMvc.perform(delete("/api/v1/usuarios/" + clienteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminNoPuedeEliminarseASiMismo() throws Exception {
        String token = tokenDe(adminId, adminEmail, Rol.ADMIN);

        mockMvc.perform(delete("/api/v1/usuarios/" + adminId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void internoSinClaveDevuelve401() throws Exception {
        VerificarCredencialesRequest request = new VerificarCredencialesRequest(clienteEmail, "Cliente123!");

        mockMvc.perform(post("/internal/usuarios/verificar-credenciales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internoConClaveYPasswordCorrectaDevuelve200() throws Exception {
        VerificarCredencialesRequest request = new VerificarCredencialesRequest(clienteEmail, "Cliente123!");

        mockMvc.perform(post("/internal/usuarios/verificar-credenciales")
                        .header("X-Internal-Key", CLAVE_INTERNA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(clienteEmail))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void internoConPasswordIncorrectaDevuelve401() throws Exception {
        VerificarCredencialesRequest request = new VerificarCredencialesRequest(clienteEmail, "Incorrecta1!");

        MvcResult resultado = mockMvc.perform(post("/internal/usuarios/verificar-credenciales")
                        .header("X-Internal-Key", CLAVE_INTERNA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThatNoPasswordInBody(resultado);
    }

    private void assertThatNoPasswordInBody(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("Incorrecta1!");
    }
}
