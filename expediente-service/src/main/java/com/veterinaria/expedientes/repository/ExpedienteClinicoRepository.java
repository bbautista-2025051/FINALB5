package com.veterinaria.expedientes.repository;

import com.veterinaria.expedientes.entity.ExpedienteClinico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpedienteClinicoRepository extends JpaRepository<ExpedienteClinico, Long> {

    boolean existsByCitaId(Long citaId);

    Page<ExpedienteClinico> findByMascotaIdOrderByFechaRegistroDesc(Long mascotaId, Pageable pageable);
}
