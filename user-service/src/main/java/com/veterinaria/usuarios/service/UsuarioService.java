package com.veterinaria.usuarios.service;

import com.veterinaria.usuarios.dto.ActualizarUsuarioRequest;
import com.veterinaria.usuarios.dto.CrearUsuarioRequest;
import com.veterinaria.usuarios.dto.UsuarioDTO;
import com.veterinaria.usuarios.dto.VerificarCredencialesRequest;
import com.veterinaria.usuarios.dto.VeterinarioDTO;
import com.veterinaria.usuarios.entity.Usuario;
import com.veterinaria.usuarios.enums.Rol;
import com.veterinaria.usuarios.exception.CredencialesInvalidasException;
import com.veterinaria.usuarios.exception.ConflictoException;
import com.veterinaria.usuarios.exception.RecursoNoEncontradoException;
import com.veterinaria.usuarios.repository.UsuarioRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioDTO crear(CrearUsuarioRequest request) {
        String email = normalizarEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw new ConflictoException("El email ya está registrado");
        }
        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setTelefono(request.telefono().trim());
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(request.rol());

        Usuario guardado;
        try {
            guardado = repository.save(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("El email ya está registrado");
        }
        log.info("Usuario creado id={} rol={}", guardado.getId(), guardado.getRol());
        return UsuarioDTO.desde(guardado);
    }

    @Transactional(readOnly = true)
    public UsuarioDTO obtener(Long id) {
        return UsuarioDTO.desde(buscar(id));
    }

    @Transactional(readOnly = true)
    public Page<UsuarioDTO> listar(Rol rol, Pageable pageable) {
        Pageable limitado = limitar(pageable);
        Page<Usuario> resultado = rol == null
                ? repository.findAll(limitado)
                : repository.findByRol(rol, limitado);
        return resultado.map(UsuarioDTO::desde);
    }

    @Transactional(readOnly = true)
    public Page<VeterinarioDTO> veterinarios(Pageable pageable) {
        Pageable limitado = limitar(pageable);
        return repository.findByRolOrderByNombre(Rol.VET, limitado)
                .map(v -> new VeterinarioDTO(v.getId(), v.getNombre()));
    }

    @Transactional
    public UsuarioDTO actualizar(Long id, ActualizarUsuarioRequest request) {
        Usuario usuario = buscar(id);

        if (request.email() != null && !request.email().isBlank()) {
            String email = normalizarEmail(request.email());
            if (!email.equals(usuario.getEmail()) && repository.existsByEmail(email)) {
                throw new ConflictoException("El email ya está registrado");
            }
            usuario.setEmail(email);
        }
        if (request.nombre() != null && !request.nombre().isBlank()) {
            usuario.setNombre(request.nombre().trim());
        }
        if (request.telefono() != null && !request.telefono().isBlank()) {
            usuario.setTelefono(request.telefono().trim());
        }
        if (request.rol() != null) {
            usuario.setRol(request.rol());
        }
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }

        try {
            Usuario actualizado = repository.save(usuario);
            log.info("Usuario actualizado id={}", actualizado.getId());
            return UsuarioDTO.desde(actualizado);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("El email ya está registrado");
        }
    }

    @Transactional
    public void eliminar(Long id, Long usuarioAutenticadoId) {
        if (id.equals(usuarioAutenticadoId)) {
            throw new ConflictoException("No puedes eliminar tu propia cuenta");
        }
        Usuario usuario = buscar(id);
        repository.delete(usuario);
        log.info("Usuario eliminado id={}", id);
    }

    @Transactional(readOnly = true)
    public UsuarioDTO verificarCredenciales(VerificarCredencialesRequest request) {
        String email = normalizarEmail(request.email());
        Usuario usuario = repository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Autenticación fallida: email no registrado");
                    return new CredencialesInvalidasException("Credenciales inválidas");
                });

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            log.warn("Autenticación fallida: contraseña incorrecta para usuario id={}", usuario.getId());
            throw new CredencialesInvalidasException("Credenciales inválidas");
        }
        return UsuarioDTO.desde(usuario);
    }

    private Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con id " + id));
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Pageable limitar(Pageable pageable) {
        if (pageable.getPageSize() <= TAMANO_MAXIMO_PAGINA) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), TAMANO_MAXIMO_PAGINA, pageable.getSort());
    }
}
