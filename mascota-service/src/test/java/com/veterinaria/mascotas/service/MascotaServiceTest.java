package com.veterinaria.mascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class MascotaServiceTest {

    @Mock
    private MascotaRepository repository;

    @Mock
    private UsuarioClient usuarioClient;

    @InjectMocks
    private MascotaService mascotaService;

    private final AtomicLong secuencia = new AtomicLong(1);

    @BeforeEach
    void limpiar() {
        secuencia.set(1);
    }

    @Test
    void clienteRegistraMascotaIgnoraElClienteIdDelBodyYUsaElSuyo() {
        UsuarioAutenticado actor = new UsuarioAutenticado(10L, "cliente@test.com", "CLIENTE");
        when(usuarioClient.obtener(10L)).thenReturn(
                new UsuarioInternaDTO(10L, "Ana Pérez", "3001112222", "cliente@test.com", "CLIENTE"));
        when(repository.save(any(Mascota.class))).thenAnswer(invocacion -> {
            Mascota mascota = invocacion.getArgument(0);
            mascota.setId(secuencia.getAndIncrement());
            return mascota;
        });

        MascotaDTO resultado = mascotaService.crear(
                new CrearMascotaRequest("Firulais", Especie.PERRO, "Mestizo", 3, 999L), actor);

        assertThat(resultado.clienteId()).isEqualTo(10L);
        assertThat(resultado.clienteNombre()).isEqualTo("Ana Pérez");
        assertThat(resultado.nombre()).isEqualTo("Firulais");

        verify(usuarioClient).obtener(10L);
        verify(usuarioClient, never()).obtener(999L);

        ArgumentCaptor<Mascota> captor = ArgumentCaptor.forClass(Mascota.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClienteId()).isEqualTo(10L);
        assertThat(captor.getValue().getClienteNombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void adminSinClienteIdLanzaSolicitudInvalida() {
        UsuarioAutenticado actor = new UsuarioAutenticado(1L, "admin@test.com", "ADMIN");

        assertThatThrownBy(() -> mascotaService.crear(
                new CrearMascotaRequest("Firulais", Especie.PERRO, null, 3, null), actor))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("clienteId");

        verify(usuarioClient, never()).obtener(anyLong());
        verify(repository, never()).save(any(Mascota.class));
    }

    @Test
    void clienteInexistentePropagaRecursoNoEncontrado() {
        UsuarioAutenticado actor = new UsuarioAutenticado(10L, "cliente@test.com", "CLIENTE");
        when(usuarioClient.obtener(10L))
                .thenThrow(new RecursoNoEncontradoException("El cliente no existe"));

        assertThatThrownBy(() -> mascotaService.crear(
                new CrearMascotaRequest("Firulais", Especie.PERRO, null, 3, null), actor))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El cliente no existe");

        verify(repository, never()).save(any(Mascota.class));
    }

    @Test
    void clienteConRolDistintoAlDeClienteLanzaReglaDeNegocio() {
        UsuarioAutenticado actor = new UsuarioAutenticado(10L, "vet@test.com", "ADMIN");
        when(usuarioClient.obtener(10L)).thenReturn(
                new UsuarioInternaDTO(10L, "Dr. Pérez", "3003334444", "vet@test.com", "VET"));

        assertThatThrownBy(() -> mascotaService.crear(
                new CrearMascotaRequest("Firulais", Especie.PERRO, null, 3, 10L), actor))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El usuario indicado no es un cliente");

        verify(repository, never()).save(any(Mascota.class));
    }

    @Test
    void obtenerMascotaInexistenteLanza404() {
        when(repository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mascotaService.obtener(77L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Mascota no encontrada con id 77");
    }

    @Test
    void listarLimitaElTamanoDePagina() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        mascotaService.listar(null, null, PageRequest.of(0, 5000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
