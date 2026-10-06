package com.veterinaria.mascotas.service;

import com.veterinaria.mascotas.client.UsuarioClient;
import com.veterinaria.mascotas.client.UsuarioInternaDTO;
import com.veterinaria.mascotas.dto.CrearMascotaRequest;
import com.veterinaria.mascotas.dto.MascotaDTO;
import com.veterinaria.mascotas.entity.Mascota;
import com.veterinaria.mascotas.enums.Especie;
import com.veterinaria.mascotas.exception.RecursoNoEncontradoException;
import com.veterinaria.mascotas.exception.ReglaNegocioException;
import com.veterinaria.mascotas.exception.SolicitudInvalidaException;
import com.veterinaria.mascotas.repository.MascotaRepository;
import com.veterinaria.mascotas.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MascotaService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final String ROL_CLIENTE = "CLIENTE";

    private final MascotaRepository repository;
    private final UsuarioClient usuarioClient;

    @Transactional
    public MascotaDTO crear(CrearMascotaRequest request, UsuarioAutenticado actor) {
        Long clienteIdEfectivo = clienteIdEfectivo(request, actor);

        UsuarioInternaDTO cliente = usuarioClient.obtener(clienteIdEfectivo);
        if (!ROL_CLIENTE.equals(cliente.rol())) {
            throw new ReglaNegocioException("El usuario indicado no es un cliente");
        }

        Mascota mascota = new Mascota();
        mascota.setNombre(request.nombre().trim());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza() == null ? null : request.raza().trim());
        mascota.setEdad(request.edad());
        mascota.setClienteId(clienteIdEfectivo);
        mascota.setClienteNombre(cliente.nombre());

        Mascota guardada = repository.save(mascota);
        log.info("Mascota creada id={} clienteId={}", guardada.getId(), guardada.getClienteId());
        return MascotaDTO.desde(guardada);
    }

    @Transactional(readOnly = true)
    public Page<MascotaDTO> misMascotas(Long clienteId, Pageable pageable) {
        return repository.findByClienteId(clienteId, limitar(pageable))
                .map(MascotaDTO::desde);
    }

    @Transactional(readOnly = true)
    public Page<MascotaDTO> listar(Long clienteIdFiltro, Especie especieFiltro, Pageable pageable) {
        Specification<Mascota> filtros = (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (clienteIdFiltro != null) {
                predicados.add(cb.equal(root.get("clienteId"), clienteIdFiltro));
            }
            if (especieFiltro != null) {
                predicados.add(cb.equal(root.get("especie"), especieFiltro));
            }
            if (predicados.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
        return repository.findAll(filtros, limitar(pageable))
                .map(MascotaDTO::desde);
    }

    @Transactional(readOnly = true)
    public MascotaDTO obtener(Long id) {
        Mascota mascota = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada con id " + id));
        return MascotaDTO.desde(mascota);
    }

    private Long clienteIdEfectivo(CrearMascotaRequest request, UsuarioAutenticado actor) {
        if (ROL_CLIENTE.equals(actor.rol())) {
            return actor.id();
        }
        if (request.clienteId() == null) {
            throw new SolicitudInvalidaException("clienteId es obligatorio para ADMIN");
        }
        return request.clienteId();
    }

    private Pageable limitar(Pageable pageable) {
        if (pageable.getPageSize() <= TAMANO_MAXIMO_PAGINA) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), TAMANO_MAXIMO_PAGINA, pageable.getSort());
    }
}
