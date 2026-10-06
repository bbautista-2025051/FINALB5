package com.veterinaria.expedientes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.veterinaria.expedientes.client.CitaClient;
import com.veterinaria.expedientes.client.CitaInternaDTO;
import com.veterinaria.expedientes.client.MascotaClient;
import com.veterinaria.expedientes.client.MascotaInternaDTO;
import com.veterinaria.expedientes.dto.CrearExpedienteRequest;
import com.veterinaria.expedientes.dto.ExpedienteDTO;
import com.veterinaria.expedientes.entity.ExpedienteClinico;
import com.veterinaria.expedientes.exception.ConflictoException;
import com.veterinaria.expedientes.exception.ProhibidoException;
import com.veterinaria.expedientes.exception.RecursoNoEncontradoException;
import com.veterinaria.expedientes.exception.ServicioNoDisponibleException;
import com.veterinaria.expedientes.repository.ExpedienteClinicoRepository;
import com.veterinaria.expedientes.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExpedienteServiceTest {

    @Mock
    private ExpedienteClinicoRepository repository;

    @Mock
    private CitaClient citaClient;

    @Mock
    private MascotaClient mascotaClient;

    @InjectMocks
    private ExpedienteService expedienteService;

    private static final UsuarioAutenticado VET_PROPIO = new UsuarioAutenticado(5L, "vet@vet.com", "VET");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, "admin@vet.com", "ADMIN");

    @Test
    void crearExpedienteValidoGuardaYCompletaLaCita() {
        when(citaClient.obtener(1L)).thenReturn(cita(1L, 10L, 5L, "PENDIENTE"));
        when(repository.existsByCitaId(1L)).thenReturn(false);
        when(repository.save(any(ExpedienteClinico.class))).thenAnswer(invocation -> {
            ExpedienteClinico expediente = invocation.getArgument(0);
            expediente.setId(77L);
            return expediente;
        });
        when(citaClient.completar(1L)).thenReturn(cita(1L, 10L, 5L, "COMPLETADA"));

        ExpedienteDTO resultado = expedienteService.crear(solicitud(1L), VET_PROPIO);

        assertThat(resultado.id()).isEqualTo(77L);
        assertThat(resultado.citaId()).isEqualTo(1L);
        assertThat(resultado.mascotaId()).isEqualTo(10L);
        assertThat(resultado.pesoKg()).isEqualByComparingTo("4.50");
        assertThat(resultado.diagnostico()).isEqualTo("Gripe canina");

        ArgumentCaptor<ExpedienteClinico> captor = ArgumentCaptor.forClass(ExpedienteClinico.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCitaId()).isEqualTo(1L);
        assertThat(captor.getValue().getMascotaId()).isEqualTo(10L);
        verify(citaClient).completar(1L);
    }

    @Test
    void crearConCitaInexistentePropagaEl404DelCliente() {
        when(citaClient.obtener(9L)).thenThrow(new RecursoNoEncontradoException("Cita no encontrada con id 9"));

        assertThatThrownBy(() -> expedienteService.crear(solicitud(9L), ADMIN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cita no encontrada con id 9");

        verify(repository, never()).save(any());
    }

    @Test
    void vetNoPuedeCrearExpedienteDeCitaAjena() {
        when(citaClient.obtener(2L)).thenReturn(cita(2L, 10L, 77L, "PENDIENTE"));

        assertThatThrownBy(() -> expedienteService.crear(solicitud(2L), VET_PROPIO))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("tus propias citas");

        verify(repository, never()).save(any());
        verify(citaClient, never()).completar(any());
    }

    @Test
    void crearEnCitaCompletadaDevuelveConflicto() {
        when(citaClient.obtener(3L)).thenReturn(cita(3L, 10L, 5L, "COMPLETADA"));

        assertThatThrownBy(() -> expedienteService.crear(solicitud(3L), ADMIN))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("pendiente para registrar");

        verify(repository, never()).save(any());
    }

    @Test
    void crearCuandoLaCitaYaTieneExpedienteDevuelveConflicto() {
        when(citaClient.obtener(4L)).thenReturn(cita(4L, 10L, 5L, "PENDIENTE"));
        when(repository.existsByCitaId(4L)).thenReturn(true);

        assertThatThrownBy(() -> expedienteService.crear(solicitud(4L), ADMIN))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya tiene un expediente");

        verify(repository, never()).save(any());
        verify(citaClient, never()).completar(any());
    }

    @Test
    void cuandoCompletarFallaElServicioPropagaYElSaveSiLlegoALlamarse() {
        when(citaClient.obtener(6L)).thenReturn(cita(6L, 10L, 5L, "PENDIENTE"));
        when(repository.existsByCitaId(6L)).thenReturn(false);
        when(repository.save(any(ExpedienteClinico.class))).thenAnswer(invocation -> {
            ExpedienteClinico expediente = invocation.getArgument(0);
            expediente.setId(88L);
            return expediente;
        });
        when(citaClient.completar(6L)).thenThrow(new ServicioNoDisponibleException("cita-service caido"));

        assertThatThrownBy(() -> expedienteService.crear(solicitud(6L), ADMIN))
                .isInstanceOf(ServicioNoDisponibleException.class);

        verify(repository).save(any(ExpedienteClinico.class));
        verify(citaClient).completar(6L);
    }

    @Test
    void historialDeMascotaAjenaParaClienteDevuelveProhibido() {
        when(mascotaClient.obtener(5L)).thenReturn(mascota(5L, 77L));

        assertThatThrownBy(() -> expedienteService.historial(
                5L, PageRequest.of(0, 20), new UsuarioAutenticado(99L, "cliente@vet.com", "CLIENTE")))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("No tienes acceso al historial");

        verify(repository, never()).findByMascotaIdOrderByFechaRegistroDesc(any(), any());
    }

    @Test
    void historialDeMascotaPropiaParaClienteDevuelveContenido() {
        when(mascotaClient.obtener(5L)).thenReturn(mascota(5L, 99L));
        when(repository.findByMascotaIdOrderByFechaRegistroDesc(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entidad(1L, 5L, 5L))));

        Page<ExpedienteDTO> resultado = expedienteService.historial(
                5L, PageRequest.of(0, 20), new UsuarioAutenticado(99L, "cliente@vet.com", "CLIENTE"));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).mascotaId()).isEqualTo(5L);
        assertThat(resultado.getContent().get(0).citaId()).isEqualTo(5L);
    }

    @Test
    void historialDeMascotaAjenaParaAdminDevuelveContenido() {
        when(mascotaClient.obtener(6L)).thenReturn(mascota(6L, 77L));
        when(repository.findByMascotaIdOrderByFechaRegistroDesc(eq(6L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entidad(2L, 6L, 6L))));

        Page<ExpedienteDTO> resultado = expedienteService.historial(
                6L, PageRequest.of(0, 20), new UsuarioAutenticado(123L, "admin@vet.com", "ADMIN"));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).id()).isEqualTo(2L);
    }

    private CrearExpedienteRequest solicitud(Long citaId) {
        return new CrearExpedienteRequest(citaId, "Gripe canina", "Reposo y antibioticos", new BigDecimal("4.50"));
    }

    private CitaInternaDTO cita(Long id, Long mascotaId, Long veterinarioId, String estado) {
        return new CitaInternaDTO(
                id, mascotaId, "Mimi", 900L, veterinarioId, "Dr. Vet",
                LocalDateTime.of(2026, 10, 5, 10, 0), "Revision general", estado);
    }

    private MascotaInternaDTO mascota(Long id, Long clienteId) {
        return new MascotaInternaDTO(id, "Mimi", "Perro", "Labrador", 3, clienteId, "Dueno");
    }

    private ExpedienteClinico entidad(Long id, Long citaId, Long mascotaId) {
        ExpedienteClinico expediente = new ExpedienteClinico();
        expediente.setId(id);
        expediente.setCitaId(citaId);
        expediente.setMascotaId(mascotaId);
        expediente.setDiagnostico("Gripe canina");
        expediente.setTratamiento("Reposo");
        expediente.setPesoKg(new BigDecimal("4.50"));
        expediente.setFechaRegistro(LocalDateTime.of(2026, 10, 5, 12, 0));
        return expediente;
    }
}
