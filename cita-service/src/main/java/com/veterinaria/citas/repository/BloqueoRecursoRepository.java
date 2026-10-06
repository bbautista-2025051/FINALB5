package com.veterinaria.citas.repository;

import com.veterinaria.citas.entity.BloqueoRecurso;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueoRecursoRepository extends JpaRepository<BloqueoRecurso, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BloqueoRecurso b WHERE b.clave = :clave")
    Optional<BloqueoRecurso> buscarConBloqueo(@Param("clave") String clave);
}