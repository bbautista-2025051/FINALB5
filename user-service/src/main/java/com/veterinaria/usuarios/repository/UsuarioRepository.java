package com.veterinaria.usuarios.repository;

import com.veterinaria.usuarios.entity.Usuario;
import com.veterinaria.usuarios.enums.Rol;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Usuario> findByRol(Rol rol, Pageable pageable);

    Page<Usuario> findByRolOrderByNombre(Rol rol, Pageable pageable);
}
