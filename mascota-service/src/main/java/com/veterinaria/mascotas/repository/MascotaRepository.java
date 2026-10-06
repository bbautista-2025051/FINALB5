package com.veterinaria.mascotas.repository;

import com.veterinaria.mascotas.entity.Mascota;
import com.veterinaria.mascotas.enums.Especie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MascotaRepository extends JpaRepository<Mascota, Long>, JpaSpecificationExecutor<Mascota> {

    Page<Mascota> findByClienteId(Long clienteId, Pageable pageable);

    Page<Mascota> findByClienteIdAndEspecie(Long clienteId, Especie especie, Pageable pageable);

    Page<Mascota> findByEspecie(Especie especie, Pageable pageable);
}
