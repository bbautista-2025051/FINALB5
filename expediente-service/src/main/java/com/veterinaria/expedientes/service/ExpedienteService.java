package com.veterinaria.expedientes.service;

import com.veterinaria.expedientes.client.CitaClient;
import com.veterinaria.expedientes.client.CitaInternaDTO;
import com.veterinaria.expedientes.client.MascotaClient;
import com.veterinaria.expedientes.client.MascotaInternaDTO;
import com.veterinaria.expedientes.dto.CrearExpedienteRequest;
import com.veterinaria.expedientes.dto.ExpedienteDTO;
import com.veterinaria.expedientes.entity.ExpedienteClinico;
import com.veterinaria.expedientes.exception.ConflictoException;
import com.veterinaria.expedientes.exception.ProhibidoException;
import com.veterinaria.expedientes.repository.ExpedienteClinicoRepository;
import com.veterinaria.expedientes.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpedienteService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ROL_VET = "VET";
    private static final String ROL_CLIENTE = "CLIENTE";

    private final ExpedienteClinicoRepository repository;
    private final CitaClient citaClient;
    private final MascotaClient mascotaClient;

    @Transactional
    public ExpedienteDTO crear(CrearExpedienteRequest request, UsuarioAutenticado actor) {
        CitaInternaDTO cita = citaClient.obtener(request.citaId());

        if (ROL_VET.equals(actor.rol())
                && !String.valueOf(cita.veterinarioId()).equals(String.valueOf(actor.id()))) {
            throw new ProhibidoException("Solo puedes registrar expedientes de tus propias citas");
        }
        if (!ESTADO_PENDIENTE.equals(cita.estado())) {
            throw new ConflictoException("La cita debe estar pendiente para registrar un expediente");
        }
        if (repository.existsByCitaId(request.citaId())) {
            throw new ConflictoException("La cita ya tiene un expediente clínico");
        }

        ExpedienteClinico expediente = new ExpedienteClinico();
        expediente.setCitaId(request.citaId());
        expediente.setMascotaId(cita.mascotaId());
        expediente.setDiagnostico(request.diagnostico().trim());
        expediente.setTratamiento(request.tratamiento().trim());
        expediente.setPesoKg(request.pesoKg());

        ExpedienteClinico guardado;
        try {
            guardado = repository.save(expediente);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("La cita ya tiene un expediente clínico");
        }

        citaClient.completar(request.citaId());

        log.info("Expediente creado id={} citaId={} mascotaId={}",
                guardado.getId(), guardado.getCitaId(), guardado.getMascotaId());
        return ExpedienteDTO.desde(guardado);
    }

    @Transactional(readOnly = true)
    public Page<ExpedienteDTO> historial(Long mascotaId, Pageable pageable, UsuarioAutenticado actor) {
        MascotaInternaDTO mascota = mascotaClient.obtener(mascotaId);

        if (ROL_CLIENTE.equals(actor.rol())
                && !String.valueOf(mascota.clienteId()).equals(String.valueOf(actor.id()))) {
            throw new ProhibidoException("No tienes acceso al historial de esta mascota");
        }

        Pageable limitado = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), TAMANO_MAXIMO_PAGINA),
                Sort.by(Sort.Direction.DESC, "fechaRegistro")
        );
        return repository.findByMascotaIdOrderByFechaRegistroDesc(mascotaId, limitado)
                .map(ExpedienteDTO::desde);
    }
}
