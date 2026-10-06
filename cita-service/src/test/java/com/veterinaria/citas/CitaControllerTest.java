package com.veterinaria.citas;

import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veterinaria.citas.client.MascotaClient;
import com.veterinaria.citas.client.MascotaInternaDTO;
import com.veterinaria.citas.client.UsuarioClient;
import com.veterinaria.citas.client.UsuarioInternaDTO;
import com.veterinaria.citas.dto.CrearCitaRequest;
import com.veterinaria.citas.repository.BloqueoRecursoRepository;
import com.veterinaria.citas.repository.CitaRepository;
import com.veterinaria.citas.security.JwtService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CitaControllerTest {

    private static final String CLAVE_INTERNA = "clave-interna-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CitaRepository repository;

    @Autowired
    private BloqueoRecursoRepository bloqueoRepository;

    @MockitoBean
    private MascotaClient mascotaClient;

    @MockitoBean
    private UsuarioClient usuarioClient;

    private String tokenCliente;
    private String tokenCliente2;
    private String tokenVet;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        bloqueoRepository.deleteAll();

        tokenCliente = jwtService.generarToken(10L, "cliente1@test.com", "CLIENTE");
        tokenCliente2 = jwtService.generarToken(20L, "cliente2@test.com", "CLIENTE");
        tokenVet = jwtService.generarToken(7L, "vera@vet.com", "VET");
        tokenAdmin = jwtService.generarToken(1L, "admin@test.com", "ADMIN");

        lenient().when(mascotaClient.obtener(101L))
                .thenReturn(new MascotaInternaDTO(101L, "Firu", "Perro", "Labrador", 3, 10L, "Cliente Uno"));
        lenient().when(mascotaClient.obtener(102L))
                .thenReturn(new MascotaInternaDTO(102L, "Rex", "Perro", "Mestizo", 5, 20L, "Cliente Dos"));
        lenient().when(usuarioClient.obtener(7L))
                .thenReturn(new UsuarioInternaDTO(7L, "Dra. Vera", "3001111111", "vera@vet.com", "VET"));
        lenient().when(usuarioClient.obtener(9L))
                .thenReturn(new UsuarioInternaDTO(9L, "Dr. Otro", "3002222222", "otro@vet.com", "VET"));
    }

    @Test
    void agendaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/citas/agenda"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void agendaConRolClienteDevuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/citas/agenda")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void crearCitaYConflictosDeHorario() throws Exception {
        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T10:00:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.veterinarioId").value(7));

        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T10:00:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El veterinario ya tiene una cita en ese horario"));

        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T10:15:00"))
                .andExpect(status().isConflict());

        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T10:30:00"))
                .andExpect(status().isCreated());
    }

    @Test
    void terceraCitaPendienteElMismoDiaDevuelve409() throws Exception {
        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T10:00:00")).andExpect(status().isCreated());
        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T12:00:00")).andExpect(status().isCreated());

        postCita(tokenCliente, cita(101L, 7L, "2030-05-20T14:00:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El cliente ya tiene 2 citas pendientes para ese día"));
    }

    @Test
    void crearCitaEnElPasadoDevuelve422() throws Exception {
        postCita(tokenCliente, cita(101L, 7L, "2020-01-01T10:00:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No se pueden crear citas en el pasado"));
    }

    @Test
    void clienteNoPuedeCrearCitaParaMascotaAjena() throws Exception {
        postCita(tokenCliente, cita(102L, 7L, "2030-05-20T10:00:00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo puedes crear citas para tus propias mascotas"));
    }

    @Test
    void cancelarCitaConAnticipacionYReintentar() throws Exception {
        long id = idDe(postCita(tokenCliente, cita(101L, 7L, "2030-05-20T10:00:00"))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(patch("/api/v1/citas/{id}/cancelar", id)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        mockMvc.perform(patch("/api/v1/citas/{id}/cancelar", id)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelarCitaConMenosDeDosHorasDevuelve422() throws Exception {
        CrearCitaRequest reciente = new CrearCitaRequest(
                101L, 7L, LocalDateTime.now().plusHours(1), "Consulta urgente");
        long id = idDe(postCita(tokenCliente, reciente).andExpect(status().isCreated()).andReturn());

        mockMvc.perform(patch("/api/v1/citas/{id}/cancelar", id)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Solo se pueden cancelar citas con más de 2 horas de anticipación"));
    }

    @Test
    void clienteNoPuedeCancelarCitaDeOtroCliente() throws Exception {
        long id = idDe(postCita(tokenCliente2, cita(102L, 7L, "2030-05-20T10:00:00"))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(patch("/api/v1/citas/{id}/cancelar", id)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo puedes cancelar tus propias citas"));
    }

    @Test
    void vetNoPuedeVerAgendaDeOtroVetYVeLaPropia() throws Exception {
        postCita(tokenAdmin, cita(101L, 7L, "2030-05-20T09:00:00")).andExpect(status().isCreated());
        postCita(tokenAdmin, cita(101L, 9L, "2030-05-20T11:00:00")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/citas/agenda?veterinarioId=1")
                        .header("Authorization", "Bearer " + tokenVet))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/citas/agenda")
                        .header("Authorization", "Bearer " + tokenVet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].veterinarioId").value(7));
    }

    @Test
    void obtenerCitaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/citas/9999")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isNotFound());
    }

    @Test
    void internoCompletarSinClaveDevuelve401YConClave200() throws Exception {
        long id = idDe(postCita(tokenAdmin, cita(101L, 7L, "2030-05-20T10:00:00"))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/internal/citas/{id}/completar", id))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/citas/{id}/completar", id)
                        .header("X-Internal-Key", CLAVE_INTERNA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    private CrearCitaRequest cita(long mascotaId, long veterinarioId, String fechaHora) {
        return new CrearCitaRequest(mascotaId, veterinarioId, LocalDateTime.parse(fechaHora), "Consulta general");
    }

    private ResultActions postCita(String token, CrearCitaRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/citas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private long idDe(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }
}