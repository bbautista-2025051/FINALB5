package com.veterinaria.citas.service;

import com.veterinaria.citas.client.MascotaClient;
import com.veterinaria.citas.client.MascotaInternaDTO;
import com.veterinaria.citas.client.UsuarioClient;
import com.veterinaria.citas.client.UsuarioInternaDTO;
import com.veterinaria.citas.dto.CitaDTO;
import com.veterinaria.citas.dto.CrearCitaRequest;
import com.veterinaria.citas.entity.BloqueoRecurso;
import com.veterinaria.citas.entity.CitaMedica;
import com.veterinaria.citas.enums.EstadoCita;
import com.veterinaria.citas.exception.ConflictoException;
import com.veterinaria.citas.exception.ProhibidoException;
import com.veterinaria.citas.exception.RecursoNoEncontradoException;
import com.veterinaria.citas.exception.ReglaNegocioException;
import com.veterinaria.citas.repository.BloqueoRecursoRepository;
import com.veterinaria.citas.repository.CitaRepository;
import com.veterinaria.citas.security.UsuarioAutenticado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CitaService {

    public static final int DURACION_MINUTOS = 30;
    public static final int MAXIMO_CITAS_PENDIENTES_POR_DIA = 2;
    public static final long HORAS_MINIMAS_CANCELACION = 2;

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final CitaRepository repository;
    private final BloqueoRecursoRepository bloqueoRepository;
    private final MascotaClient mascotaClient;
    private final UsuarioClient usuarioClient;
    private final EntityManager entityManager;

    @Transactional
    public CitaDTO crear(CrearCitaRequest request, UsuarioAutenticado actor) {
        if (request.fechaHora().isBefore(LocalDateTime.now())) {
            throw new ReglaNegocioException("No se pueden crear citas en el pasado");
        }

        MascotaInternaDTO mascota = mascotaClient.obtener(request.mascotaId());

        if ("CLIENTE".equals(actor.rol()) && !mascota.clienteId().equals(actor.id())) {
            throw new ProhibidoException("Solo puedes crear citas para tus propias mascotas");
        }

        UsuarioInternaDTO vet = usuarioClient.obtener(request.veterinarioId());
        if (!"VET".equals(vet.rol())) {
            throw new ReglaNegocioException("El usuario indicado no es un veterinario");
        }

        bloquear("VET:" + vet.id());
        bloquear("CLIENTE:" + mascota.clienteId());

        List<CitaMedica> solapes = repository.buscarSolapadas(
                vet.id(),
                EstadoCita.CANCELADA,
                request.fechaHora().minusMinutes(DURACION_MINUTOS),
                request.fechaHora().plusMinutes(DURACION_MINUTOS));
        if (!solapes.isEmpty()) {
            throw new ConflictoException("El veterinario ya tiene una cita en ese horario");
        }

        LocalDate dia = request.fechaHora().toLocalDate();
        long pendientes = repository.countByClienteIdAndEstadoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                mascota.clienteId(),
                EstadoCita.PENDIENTE,
                dia.atStartOfDay(),
                dia.plusDays(1).atStartOfDay());
        if (pendientes >= MAXIMO_CITAS_PENDIENTES_POR_DIA) {
            throw new ConflictoException("El cliente ya tiene 2 citas pendientes para ese día");
        }

        CitaMedica cita = new CitaMedica();
        cita.setMascotaId(mascota.id());
        cita.setMascotaNombre(mascota.nombre());
        cita.setClienteId(mascota.clienteId());
        cita.setVeterinarioId(vet.id());
        cita.setVeterinarioNombre(vet.nombre());
        cita.setFechaHora(request.fechaHora());
        cita.setMotivo(request.motivo().trim());
        cita.setEstado(EstadoCita.PENDIENTE);

        CitaMedica guardada = repository.save(cita);
        log.info("Cita creada id={} mascotaId={} veterinarioId={} fechaHora={}",
                guardada.getId(), guardada.getMascotaId(), guardada.getVeterinarioId(), guardada.getFechaHora());
        return CitaDTO.desde(guardada);
    }

    @Transactional
    public CitaDTO cancelar(Long id, UsuarioAutenticado actor) {
        CitaMedica cita = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada con id " + id));

        if ("CLIENTE".equals(actor.rol()) && !cita.getClienteId().equals(actor.id())) {
            throw new ProhibidoException("Solo puedes cancelar tus propias citas");
        }

        bloquear("VET:" + cita.getVeterinarioId());
        bloquear("CLIENTE:" + cita.getClienteId());

        CitaMedica actual = repository.buscarConBloqueo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada con id " + id));

        if (actual.getEstado() != EstadoCita.PENDIENTE) {
            throw new ConflictoException("Solo se pueden cancelar citas pendientes");
        }

        if (!LocalDateTime.now().isBefore(actual.getFechaHora().minusHours(HORAS_MINIMAS_CANCELACION))) {
            throw new ReglaNegocioException("Solo se pueden cancelar citas con más de 2 horas de anticipación");
        }

        int filas = repository.transicionarEstado(id, EstadoCita.PENDIENTE, EstadoCita.CANCELADA);
        if (filas == 0) {
            throw new ConflictoException("La cita ya no está pendiente");
        }

        actual.setEstado(EstadoCita.CANCELADA);
        log.info("Cita cancelada id={} veterinarioId={} clienteId={} fechaHora={}",
                actual.getId(), actual.getVeterinarioId(), actual.getClienteId(), actual.getFechaHora());
        return CitaDTO.desde(actual);
    }

    @Transactional(readOnly = true)
    public Page<CitaDTO> agenda(Long veterinarioIdParam, LocalDate fecha, Pageable pageable, UsuarioAutenticado actor) {
        Long efectivo = veterinarioIdParam;
        if ("VET".equals(actor.rol())) {
            if (veterinarioIdParam != null && !veterinarioIdParam.equals(actor.id())) {
                throw new ProhibidoException("Solo puedes consultar tu propia agenda");
            }
            efectivo = actor.id();
        }

        Long veterinarioFiltro = efectivo;
        Specification<CitaMedica> spec = (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (veterinarioFiltro != null) {
                predicados.add(cb.equal(root.get("veterinarioId"), veterinarioFiltro));
            }
            if (fecha != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), fecha.atStartOfDay()));
                predicados.add(cb.lessThan(root.get("fechaHora"), fecha.plusDays(1).atStartOfDay()));
            }
            if (predicados.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };

        Pageable limitado = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), TAMANO_MAXIMO_PAGINA),
                Sort.by("fechaHora"));
        return repository.findAll(spec, limitado).map(CitaDTO::desde);
    }

    @Transactional(readOnly = true)
    public CitaDTO obtener(Long id, UsuarioAutenticado actor) {
        CitaMedica cita = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada con id " + id));
        verificarAcceso(cita, actor);
        return CitaDTO.desde(cita);
    }

    @Transactional(readOnly = true)
    public CitaDTO obtenerInterno(Long id) {
        return repository.findById(id)
                .map(CitaDTO::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada con id " + id));
    }

    @Transactional
    public CitaDTO completar(Long id) {
        int filas = repository.transicionarEstado(id, EstadoCita.PENDIENTE, EstadoCita.COMPLETADA);
        if (filas == 0) {
            if (!repository.existsById(id)) {
                throw new RecursoNoEncontradoException("Cita no encontrada con id " + id);
            }
            throw new ConflictoException("La cita no está pendiente");
        }
        CitaMedica cita = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada con id " + id));
        log.info("Cita completada id={} veterinarioId={} fechaHora={}",
                cita.getId(), cita.getVeterinarioId(), cita.getFechaHora());
        return CitaDTO.desde(cita);
    }

    private void verificarAcceso(CitaMedica cita, UsuarioAutenticado actor) {
        switch (actor.rol()) {
            case "ADMIN" -> {
            }
            case "VET" -> {
                if (!cita.getVeterinarioId().equals(actor.id())) {
                    throw new ProhibidoException("Solo puedes consultar citas de tu propia agenda");
                }
            }
            case "CLIENTE" -> {
                if (!cita.getClienteId().equals(actor.id())) {
                    throw new ProhibidoException("Solo puedes consultar tus propias citas");
                }
            }
            default -> throw new ProhibidoException("No tienes permisos para consultar esta cita");
        }
    }

    private void bloquear(String clave) {
        for (int intento = 0; intento < 3; intento++) {
            if (bloqueoRepository.buscarConBloqueo(clave).isPresent()) return;
            try {
                bloqueoRepository.saveAndFlush(new BloqueoRecurso(clave));
                return; // el INSERT ya mantiene el lock X de la fila
            } catch (DataIntegrityViolationException ex) {
                // otra transacción creó la clave: el siguiente buscarConBloqueo esperará su commit.
                // Se limpia el contexto de persistencia para descartar el INSERT fallido; de lo
                // contrario Hibernate lo reintenta en el siguiente flush con una violación de clave.
                entityManager.clear();
            }
        }
        throw new ConflictoException("No se pudo bloquear el recurso, reintente la operación");
    }
}