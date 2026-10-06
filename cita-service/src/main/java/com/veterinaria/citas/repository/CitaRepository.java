package com.veterinaria.citas.repository;

import com.veterinaria.citas.entity.CitaMedica;
import com.veterinaria.citas.enums.EstadoCita;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaRepository extends JpaRepository<CitaMedica, Long>, JpaSpecificationExecutor<CitaMedica> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CitaMedica c WHERE c.veterinarioId = :veterinarioId AND c.estado <> :cancelada "
            + "AND c.fechaHora < :finVentana AND c.fechaHora > :inicioVentana")
    List<CitaMedica> buscarSolapadas(@Param("veterinarioId") Long veterinarioId,
            @Param("cancelada") EstadoCita cancelada,
            @Param("inicioVentana") LocalDateTime inicioVentana,
            @Param("finVentana") LocalDateTime finVentana);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CitaMedica c WHERE c.id = :id")
    Optional<CitaMedica> buscarConBloqueo(@Param("id") Long id);

    long countByClienteIdAndEstadoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
            Long clienteId, EstadoCita estado, LocalDateTime desde, LocalDateTime hasta);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CitaMedica c SET c.estado = :a WHERE c.id = :id AND c.estado = :desde")
    int transicionarEstado(@Param("id") Long id, @Param("desde") EstadoCita desde, @Param("a") EstadoCita a);
}