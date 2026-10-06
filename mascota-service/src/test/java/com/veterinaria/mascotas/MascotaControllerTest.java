package com.veterinaria.mascotas;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veterinaria.mascotas.client.UsuarioClient;
import com.veterinaria.mascotas.client.UsuarioInternaDTO;
import com.veterinaria.mascotas.dto.CrearMascotaRequest;
import com.veterinaria.mascotas.dto.MascotaDTO;
import com.veterinaria.mascotas.enums.Especie;
import com.veterinaria.mascotas.security.JwtService;
import com.veterinaria.mascotas.security.UsuarioAutenticado;
import com.veterinaria.mascotas.service.MascotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class MascotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MascotaService mascotaService;

    @MockitoBean
    private UsuarioClient usuarioClient;

    private static final String CLAVE_INTERNA = "clave-interna-de-prueba";

    @BeforeEach
    void prepararCliente() {
        when(usuarioClient.obtener(anyLong())).thenAnswer(invocacion -> new UsuarioInternaDTO(
                invocacion.getArgument(0),
                "Cliente Demo",
                "3001112222",
                "demo@test.com",
                "CLIENTE"
        ));
    }

    private String tokenDe(Long id, String rol) {
        return jwtService.generarToken(id, "demo@test.com", rol);
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/mascotas/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void clienteNoPuedeObtenerMascotaPorIdDevuelve403() throws Exception {
        String token = tokenDe(808L, "CLIENTE");

        mockMvc.perform(get("/api/v1/mascotas/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void clienteVeSoloSusMascotas() throws Exception {
        MascotaDTO propia = mascotaService.crear(
                new CrearMascotaRequest("Mishi", Especie.GATO, "Común", 2, null),
                new UsuarioAutenticado(701L, "mishi@test.com", "CLIENTE"));
        MascotaDTO ajena = mascotaService.crear(
                new CrearMascotaRequest("Otto", Especie.PERRO, null, 5, null),
                new UsuarioAutenticado(702L, "otto@test.com", "CLIENTE"));

        mockMvc.perform(get("/api/v1/mascotas/mis-mascotas")
                        .header("Authorization", "Bearer " + tokenDe(701L, "CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(propia.id()))
                .andExpect(jsonPath("$.content[0].clienteId").value(701));
    }

    @Test
    void adminCreaMascotaParaElClienteIndicado() throws Exception {
        String token = tokenDe(1L, "ADMIN");
        CrearMascotaRequest request = new CrearMascotaRequest("Toby", Especie.PERRO, "Beagle", 4, 777L);

        mockMvc.perform(post("/api/v1/mascotas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(777))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente Demo"))
                .andExpect(jsonPath("$.nombre").value("Toby"));
    }

    @Test
    void clienteCreaMascotaUsandoSuPropioIdAunqueElBodyIndiqueOtro() throws Exception {
        String token = tokenDe(808L, "CLIENTE");
        CrearMascotaRequest request = new CrearMascotaRequest("Luna", Especie.GATO, null, 1, 999L);

        mockMvc.perform(post("/api/v1/mascotas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(808));
    }

    @Test
    void vetObtieneMascotaPorId() throws Exception {
        MascotaDTO creada = mascotaService.crear(
                new CrearMascotaRequest("Rex", Especie.PERRO, "Pastor", 6, 700L),
                new UsuarioAutenticado(1L, "admin@test.com", "ADMIN"));
        String token = tokenDe(5L, "VET");

        mockMvc.perform(get("/api/v1/mascotas/" + creada.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creada.id()))
                .andExpect(jsonPath("$.nombre").value("Rex"));
    }

    @Test
    void internoSinClaveDevuelve401() throws Exception {
        MascotaDTO creada = mascotaService.crear(
                new CrearMascotaRequest("Maya", Especie.AVE, null, 3, 703L),
                new UsuarioAutenticado(1L, "admin@test.com", "ADMIN"));

        mockMvc.perform(get("/internal/mascotas/" + creada.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void internoConClaveDevuelve200() throws Exception {
        MascotaDTO creada = mascotaService.crear(
                new CrearMascotaRequest("Maya", Especie.AVE, null, 3, 704L),
                new UsuarioAutenticado(1L, "admin@test.com", "ADMIN"));

        mockMvc.perform(get("/internal/mascotas/" + creada.id())
                        .header("X-Internal-Key", CLAVE_INTERNA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creada.id()))
                .andExpect(jsonPath("$.nombre").value("Maya"))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente Demo"));
    }

    @Test
    void cuerpoInvalidoDevuelve400ConDetalles() throws Exception {
        String token = tokenDe(808L, "CLIENTE");

        mockMvc.perform(post("/api/v1/mascotas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray());
    }
}
