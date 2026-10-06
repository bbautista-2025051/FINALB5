package com.veterinaria.expedientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veterinaria.expedientes.client.CitaClient;
import com.veterinaria.expedientes.client.CitaInternaDTO;
import com.veterinaria.expedientes.client.MascotaClient;
import com.veterinaria.expedientes.client.MascotaInternaDTO;
import com.veterinaria.expedientes.dto.CrearExpedienteRequest;
import com.veterinaria.expedientes.exception.ConflictoException;
import com.veterinaria.expedientes.exception.RecursoNoEncontradoException;
import com.veterinaria.expedientes.repository.ExpedienteClinicoRepository;
import com.veterinaria.expedientes.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
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
class ExpedienteControllerTest {

    private static final String AUTORIZACION = "Authorization";
    private static final AtomicLong SECUENCIA = new AtomicLong(4000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ExpedienteClinicoRepository repository;

    @MockitoBean
    private CitaClient citaClient;

    @MockitoBean
    private MascotaClient mascotaClient;

    @Test
    void historialSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + siguiente()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void historialConTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + siguiente())
                        .header(AUTORIZACION, "Bearer token-basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeCrearExpediente() throws Exception {
        String token = token(9001L, "cliente@vet.com", "CLIENTE");

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(siguiente())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void historialEsVisibleParaLosTresRoles() throws Exception {
        long mascotaId = siguiente();
        when(mascotaClient.obtener(mascotaId)).thenReturn(mascota(mascotaId, 9001L));

        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + mascotaId)
                        .header(AUTORIZACION, token(1L, "admin@vet.com", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + mascotaId)
                        .header(AUTORIZACION, token(2L, "vet@vet.com", "VET")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + mascotaId)
                        .header(AUTORIZACION, token(9001L, "cliente@vet.com", "CLIENTE")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCreaExpedienteYSePersiste() throws Exception {
        long citaId = siguiente();
        long mascotaId = siguiente();
        when(citaClient.obtener(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "PENDIENTE"));
        when(citaClient.completar(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "COMPLETADA"));

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token(1L, "admin@vet.com", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.citaId").value(citaId))
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.diagnostico").value("Gripe canina"))
                .andExpect(jsonPath("$.fechaRegistro").exists());

        assertThat(repository.existsByCitaId(citaId)).isTrue();
    }

    @Test
    void vetCreaExpedienteDeSuPropiaCita() throws Exception {
        long citaId = siguiente();
        long mascotaId = siguiente();
        when(citaClient.obtener(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "PENDIENTE"));
        when(citaClient.completar(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "COMPLETADA"));

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token(55L, "vet@vet.com", "VET"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(repository.existsByCitaId(citaId)).isTrue();
    }

    @Test
    void vetNoPuedeCrearExpedienteDeCitaDeOtroVeterinario() throws Exception {
        long citaId = siguiente();
        long mascotaId = siguiente();
        when(citaClient.obtener(citaId)).thenReturn(cita(citaId, mascotaId, 77L, "PENDIENTE"));

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token(55L, "vet@vet.com", "VET"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isForbidden());

        assertThat(repository.existsByCitaId(citaId)).isFalse();
    }

    @Test
    void segundaPublicacionDeLaMismaCitaDevuelve409() throws Exception {
        long citaId = siguiente();
        long mascotaId = siguiente();
        when(citaClient.obtener(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "PENDIENTE"));
        when(citaClient.completar(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "COMPLETADA"));
        String token = token(1L, "admin@vet.com", "ADMIN");

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void cuerpoInvalidoDevuelve400ConDetalles() throws Exception {
        String token = token(1L, "admin@vet.com", "ADMIN");
        String cuerpo = "{\"citaId\":" + siguiente()
                + ",\"diagnostico\":\"\",\"tratamiento\":\"Reposo\",\"pesoKg\":-2}";

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void historialDeMascotaAjenaParaClienteDevuelve403() throws Exception {
        long mascotaId = siguiente();
        when(mascotaClient.obtener(mascotaId)).thenReturn(mascota(mascotaId, 777L));
        String token = token(888L, "cliente@vet.com", "CLIENTE");

        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + mascotaId)
                        .header(AUTORIZACION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void historialPropioDevuelve200ConContenido() throws Exception {
        long mascotaId = siguiente();
        long citaId = siguiente();
        when(citaClient.obtener(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "PENDIENTE"));
        when(citaClient.completar(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "COMPLETADA"));

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token(1L, "admin@vet.com", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isCreated());

        when(mascotaClient.obtener(mascotaId)).thenReturn(mascota(mascotaId, 888L));

        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + mascotaId)
                        .header(AUTORIZACION, token(888L, "cliente@vet.com", "CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].citaId").value(citaId))
                .andExpect(jsonPath("$.content[0].mascotaId").value(mascotaId));
    }

    @Test
    void historialDeMascotaInexistenteDevuelve404() throws Exception {
        long mascotaId = siguiente();
        when(mascotaClient.obtener(mascotaId))
                .thenThrow(new RecursoNoEncontradoException("Mascota no encontrada con id " + mascotaId));
        String token = token(9001L, "cliente@vet.com", "CLIENTE");

        mockMvc.perform(get("/api/v1/expedientes/mascotas/" + mascotaId)
                        .header(AUTORIZACION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void cuandoCompletarFallaLaCitaNoSeCompletaYElExpedienteNoPersiste() throws Exception {
        long citaId = siguiente();
        long mascotaId = siguiente();
        when(citaClient.obtener(citaId)).thenReturn(cita(citaId, mascotaId, 55L, "PENDIENTE"));
        when(citaClient.completar(citaId)).thenThrow(new ConflictoException("La cita no está pendiente"));

        mockMvc.perform(post("/api/v1/expedientes")
                        .header(AUTORIZACION, token(1L, "admin@vet.com", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(citaId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        assertThat(repository.existsByCitaId(citaId)).isFalse();
    }

    private long siguiente() {
        return SECUENCIA.incrementAndGet();
    }

    private String token(Long id, String email, String rol) {
        return "Bearer " + jwtService.generarToken(id, email, rol);
    }

    private String cuerpo(long citaId) throws Exception {
        CrearExpedienteRequest request = new CrearExpedienteRequest(
                citaId, "Gripe canina", "Reposo y antibioticos", new BigDecimal("4.50"));
        return objectMapper.writeValueAsString(request);
    }

    private CitaInternaDTO cita(Long id, Long mascotaId, Long veterinarioId, String estado) {
        return new CitaInternaDTO(
                id, mascotaId, "Mimi", 900L, veterinarioId, "Dr. Vet",
                LocalDateTime.of(2026, 10, 5, 10, 0), "Revision general", estado);
    }

    private MascotaInternaDTO mascota(Long id, Long clienteId) {
        return new MascotaInternaDTO(id, "Mimi", "Perro", "Labrador", 3, clienteId, "Dueno");
    }
}
