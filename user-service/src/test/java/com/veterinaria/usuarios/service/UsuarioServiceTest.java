package com.veterinaria.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.veterinaria.usuarios.dto.ActualizarUsuarioRequest;
import com.veterinaria.usuarios.dto.CrearUsuarioRequest;
import com.veterinaria.usuarios.dto.UsuarioDTO;
import com.veterinaria.usuarios.dto.VerificarCredencialesRequest;
import com.veterinaria.usuarios.entity.Usuario;
import com.veterinaria.usuarios.enums.Rol;
import com.veterinaria.usuarios.exception.CredencialesInvalidasException;
import com.veterinaria.usuarios.exception.ConflictoException;
import com.veterinaria.usuarios.exception.RecursoNoEncontradoException;
import com.veterinaria.usuarios.repository.UsuarioRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private final AtomicLong secuencia = new AtomicLong(1);

    @BeforeEach
    void limpiar() {
        secuencia.set(1);
    }

    @Test
    void crearUsuarioValidoEncriptaPasswordYAsignaRol() {
        when(repository.existsByEmail("ana@vet.com")).thenReturn(false);
        when(passwordEncoder.encode("Secreta123")).thenReturn("$2a$10$hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario u = invocation.getArgument(0);
            u.setId(secuencia.getAndIncrement());
            return u;
        });

        UsuarioDTO resultado = usuarioService.crear(
                new CrearUsuarioRequest("Ana Pérez", "3001234567", "ana@vet.com", "Secreta123", Rol.CLIENTE));

        assertThat(resultado.id()).isNotNull();
        assertThat(resultado.rol()).isEqualTo(Rol.CLIENTE);
        assertThat(resultado.email()).isEqualTo("ana@vet.com");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$hash");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("Secreta123");
    }

    @Test
    void crearUsuarioConEmailDuplicadoRechaza() {
        when(repository.existsByEmail("ana@vet.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crear(
                new CrearUsuarioRequest("Ana", "3001234567", "ana@vet.com", "Secreta123", Rol.CLIENTE)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya está registrado");

        verify(repository, never()).save(any());
    }

    @Test
    void verificarCredencialesCorrectasRetornaUsuario() {
        Usuario usuario = usuarioConPassword();
        when(repository.findByEmail("cliente@vet.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Cliente123!", "$2a$10$hash")).thenReturn(true);

        UsuarioDTO resultado = usuarioService.verificarCredenciales(
                new VerificarCredencialesRequest("cliente@vet.com", "Cliente123!"));

        assertThat(resultado.id()).isEqualTo(10L);
        assertThat(resultado.rol()).isEqualTo(Rol.CLIENTE);
    }

    @Test
    void verificarPasswordIncorrectaLanzaCredencialesInvalidas() {
        Usuario usuario = usuarioConPassword();
        when(repository.findByEmail("cliente@vet.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.verificarCredenciales(
                new VerificarCredencialesRequest("cliente@vet.com", "otra")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void verificarEmailInexistenteLanzaCredencialesInvalidas() {
        when(repository.findByEmail("nadie@vet.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.verificarCredenciales(
                new VerificarCredencialesRequest("nadie@vet.com", "Secreta123")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void eliminarPropiaCuentaEsRechazado() {
        assertThatThrownBy(() -> usuarioService.eliminar(5L, 5L))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("propia cuenta");

        verify(repository, never()).delete(any());
    }

    @Test
    void eliminarCuentaAjenaLaBorra() {
        Usuario usuario = usuarioConPassword();
        when(repository.findById(10L)).thenReturn(Optional.of(usuario));

        usuarioService.eliminar(10L, 99L);

        verify(repository).delete(usuario);
    }

    @Test
    void obtenerUsuarioInexistenteLanza404() {
        when(repository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtener(77L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarLimitaElTamanoDePagina() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        usuarioService.listar(null, PageRequest.of(0, 5000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void actualizarCambiaSoloLosCamposPresentes() {
        Usuario usuario = usuarioConPassword();
        when(repository.findById(10L)).thenReturn(Optional.of(usuario));
        when(repository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioDTO resultado = usuarioService.actualizar(
                10L,
                new ActualizarUsuarioRequest(null, null, null, Rol.VET, null));

        assertThat(resultado.rol()).isEqualTo(Rol.VET);
        assertThat(resultado.nombre()).isEqualTo(usuario.getNombre());
        verify(passwordEncoder, never()).encode(any());
    }

    private Usuario usuarioConPassword() {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setNombre("Cliente Demo");
        usuario.setTelefono("3005556666");
        usuario.setEmail("cliente@vet.com");
        usuario.setPassword("$2a$10$hash");
        usuario.setRol(Rol.CLIENTE);
        return usuario;
    }
}
