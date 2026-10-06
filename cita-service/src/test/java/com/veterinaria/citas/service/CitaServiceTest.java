package com.veterinaria.citas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.veterinaria.citas.client.MascotaClient;
import com.veterinaria.citas.client.MascotaInternaDTO;
import com.veterinaria.citas.client.UsuarioClient;
import com.veterinaria.citas.client.UsuarioInternaDTO;
import com.veterinaria.citas.dto.CitaDTO;
import com.veterinaria.citas.dto.CrearCitaRequest;
import com.veterinaria.citas.entity.CitaMedica;
import com.veterinaria.citas.enums.EstadoCita;
import com.veterinaria.citas.exception.ConflictoException;
import com.veterinaria.citas.exception.ProhibidoException;
import com.veterinaria.citas.exception.ReglaNegocioException;
import com.veterinaria.citas.repository.BloqueoRecursoRepository;
import com.veterinaria.citas.repository.CitaRepository;
import com.veterinaria.citas.security.UsuarioAutenticado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    @Mock
    private CitaRepository repository;

    @Mock
    private BloqueoRecursoRepository bloqueoRepository;

    @Mock
    private MascotaClient mascotaClient;

    @Mock
    private UsuarioClient usuarioClient;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CitaService citaService;

    private final LocalDateTime fechaFutura = LocalDateTime.of(2030, 6, 15, 10, 0);

    @Test
    void crearCitaEnElPasadoRechaza() {
        UsuarioAutenticado actor = actor(10L, "CLIENTE");

        assertThatThrownBy(() -> citaService.crear(
                new CrearCitaRequest(1L, 7L, LocalDateTime.of(2020, 1, 1, 10, 0), "Consulta"), actor))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("No se pueden crear citas en el pasado");

        verify(repository, never()).save(any());
    }

    @Test
    void crearConSolapeDevuelveConflicto() {
        UsuarioAutenticado actor = actor(10L, "CLIENTE");
        when(mascotaClient.obtener(1L)).thenReturn(mascota(10L));
        when(usuarioClient.obtener(7L)).thenReturn(vet("VET"));
        when(repository.buscarSolapadas(eq(7L), eq(EstadoCita.CANCELADA), any(), any()))
                .thenReturn(List.of(cita(1L, 7L, 10L, fechaFutura, EstadoCita.PENDIENTE)));

        assertThatThrownBy(() -> citaService.crear(
                new CrearCitaRequest(1L, 7L, fechaFutura, "Consulta"), actor))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya tiene una cita en ese horario");

        verify(repository, never()).save(any());
    }

    @Test
    void crearAlSuperarLimiteDiarioDevuelveConflicto() {
        UsuarioAutenticado actor = actor(10L, "CLIENTE");
        when(mascotaClient.obtener(1L)).thenReturn(mascota(10L));
        when(usuarioClient.obtener(7L)).thenReturn(vet("VET"));
        when(repository.countByClienteIdAndEstadoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                eq(10L), eq(EstadoCita.PENDIENTE), any(), any())).thenReturn(2L);

        assertThatThrownBy(() -> citaService.crear(
                new CrearCitaRequest(1L, 7L, fechaFutura, "Consulta"), actor))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("2 citas pendientes");

        verify(repository, never()).save(any());
    }

    @Test
    void crearParaUsuarioQueNoEsVeterinarioRechaza() {
        UsuarioAutenticado actor = actor(10L, "CLIENTE");
        when(mascotaClient.obtener(1L)).thenReturn(mascota(10L));
        when(usuarioClient.obtener(7L)).thenReturn(vet("CLIENTE"));

        assertThatThrownBy(() -> citaService.crear(
                new CrearCitaRequest(1L, 7L, fechaFutura, "Consulta"), actor))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no es un veterinario");

        verify(repository, never()).save(any());
    }

    @Test
    void crearCitaValidaGuardaComoPendiente() {
        UsuarioAutenticado actor = actor(10L, "CLIENTE");
        when(mascotaClient.obtener(1L)).thenReturn(mascota(10L));
        when(usuarioClient.obtener(7L)).thenReturn(vet("VET"));
        when(repository.save(any(CitaMedica.class))).thenAnswer(inv -> {
            CitaMedica cita = inv.getArgument(0);
            cita.setId(99L);
            return cita;
        });

        CitaDTO resultado = citaService.crear(
                new CrearCitaRequest(1L, 7L, fechaFutura, "Consulta"), actor);

        assertThat(resultado.id()).isEqualTo(99L);
        assertThat(resultado.estado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(resultado.mascotaNombre()).isEqualTo("Firu");
        assertThat(resultado.veterinarioNombre()).isEqualTo("Dra. Vera");

        ArgumentCaptor<CitaMedica> captor = ArgumentCaptor.forClass(CitaMedica.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(captor.getValue().getClienteId()).isEqualTo(10L);
    }

    @Test
    void clienteNoPuedeCrearParaMascotaAjena() {
        UsuarioAutenticado actor = actor(10L, "CLIENTE");
        when(mascotaClient.obtener(1L)).thenReturn(mascota(20L));

        assertThatThrownBy(() -> citaService.crear(
                new CrearCitaRequest(1L, 7L, fechaFutura, "Consulta"), actor))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("tus propias mascotas");

        verify(repository, never()).save(any());
    }

    @Test
    void cancelarConMenosDeDosHorasDevuelveReglaNegocio() {
        CitaMedica cita = cita(5L, 7L, 10L, LocalDateTime.now().plusHours(1), EstadoCita.PENDIENTE);
        when(repository.findById(5L)).thenReturn(Optional.of(cita));
        when(repository.buscarConBloqueo(5L)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cancelar(5L, actor(10L, "CLIENTE")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("2 horas de anticipación");

        verify(repository, never()).transicionarEstado(any(), any(), any());
    }

    @Test
    void cancelarConTresHorasDeAnticipacionCancela() {
        CitaMedica cita = cita(5L, 7L, 10L, LocalDateTime.now().plusHours(3), EstadoCita.PENDIENTE);
        when(repository.findById(5L)).thenReturn(Optional.of(cita));
        when(repository.buscarConBloqueo(5L)).thenReturn(Optional.of(cita));
        when(repository.transicionarEstado(eq(5L), eq(EstadoCita.PENDIENTE), eq(EstadoCita.CANCELADA)))
                .thenReturn(1);

        CitaDTO resultado = citaService.cancelar(5L, actor(10L, "CLIENTE"));

        assertThat(resultado.estado()).isEqualTo(EstadoCita.CANCELADA);
        verify(repository).transicionarEstado(5L, EstadoCita.PENDIENTE, EstadoCita.CANCELADA);
    }

    @Test
    void cancelarCitaYaCompletadaDevuelveConflicto() {
        CitaMedica cita = cita(5L, 7L, 10L, LocalDateTime.now().plusHours(3), EstadoCita.COMPLETADA);
        when(repository.findById(5L)).thenReturn(Optional.of(cita));
        when(repository.buscarConBloqueo(5L)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cancelar(5L, actor(10L, "CLIENTE")))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Solo se pueden cancelar citas pendientes");

        verify(repository, never()).transicionarEstado(any(), any(), any());
    }

    @Test
    void clienteNoPuedeCancelarCitaAjena() {
        CitaMedica cita = cita(5L, 7L, 20L, LocalDateTime.now().plusHours(3), EstadoCita.PENDIENTE);
        when(repository.findById(5L)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cancelar(5L, actor(10L, "CLIENTE")))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("tus propias citas");

        verify(repository, never()).buscarConBloqueo(any());
        verify(repository, never()).transicionarEstado(any(), any(), any());
    }

    @Test
    void vetNoPuedeConsultarAgendaDeOtroVeterinario() {
        UsuarioAutenticado actor = actor(7L, "VET");

        assertThatThrownBy(() -> citaService.agenda(9L, null, PageRequest.of(0, 20), actor))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("tu propia agenda");

        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void agendaSinFiltroUsaElIdDelVeterinarioAutenticado() {
        UsuarioAutenticado actor = actor(7L, "VET");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        citaService.agenda(null, null, PageRequest.of(0, 20), actor);

        ArgumentCaptor<Specification<CitaMedica>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));

        Root<CitaMedica> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> pathVet = mock(Path.class);
        Predicate predicado = mock(Predicate.class);
        doReturn(pathVet).when(root).get("veterinarioId");
        when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(predicado);
        when(cb.and(any(Predicate[].class))).thenReturn(predicado);

        specCaptor.getValue().toPredicate(root, query, cb);

        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(cb).equal(any(Expression.class), valor.capture());
        assertThat(valor.getValue()).isEqualTo(7L);
        verify(root).get("veterinarioId");
    }

    private UsuarioAutenticado actor(Long id, String rol) {
        return new UsuarioAutenticado(id, "actor@test.com", rol);
    }

    private MascotaInternaDTO mascota(Long clienteId) {
        return new MascotaInternaDTO(1L, "Firu", "Perro", "Labrador", 3, clienteId, "Cliente Test");
    }

    private UsuarioInternaDTO vet(String rol) {
        return new UsuarioInternaDTO(7L, "Dra. Vera", "3001111111", "vera@vet.com", rol);
    }

    private CitaMedica cita(Long id, Long veterinarioId, Long clienteId, LocalDateTime fechaHora, EstadoCita estado) {
        CitaMedica cita = new CitaMedica();
        cita.setId(id);
        cita.setMascotaId(1L);
        cita.setMascotaNombre("Firu");
        cita.setClienteId(clienteId);
        cita.setVeterinarioId(veterinarioId);
        cita.setVeterinarioNombre("Dra. Vera");
        cita.setFechaHora(fechaHora);
        cita.setMotivo("Consulta");
        cita.setEstado(estado);
        return cita;
    }
}