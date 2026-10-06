package com.veterinaria.citas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.veterinaria.citas.client.MascotaClient;
import com.veterinaria.citas.client.MascotaInternaDTO;
import com.veterinaria.citas.client.UsuarioClient;
import com.veterinaria.citas.client.UsuarioInternaDTO;
import com.veterinaria.citas.dto.CrearCitaRequest;
import com.veterinaria.citas.exception.ConflictoException;
import com.veterinaria.citas.repository.BloqueoRecursoRepository;
import com.veterinaria.citas.repository.CitaRepository;
import com.veterinaria.citas.security.UsuarioAutenticado;
import com.veterinaria.citas.service.CitaService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CitaConcurrenciaTest {

    @Autowired
    private CitaService citaService;

    @Autowired
    private CitaRepository repository;

    @Autowired
    private BloqueoRecursoRepository bloqueoRepository;

    @MockitoBean
    private MascotaClient mascotaClient;

    @MockitoBean
    private UsuarioClient usuarioClient;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
        bloqueoRepository.deleteAll();
    }

    @Test
    void soloUnaCitaSeCreaParaElMismoVeterinarioYHora() throws Exception {
        when(mascotaClient.obtener(101L))
                .thenReturn(new MascotaInternaDTO(101L, "Firu", "Perro", "Labrador", 3, 10L, "Cliente Uno"));
        when(usuarioClient.obtener(7L))
                .thenReturn(new UsuarioInternaDTO(7L, "Dra. Vera", "3001111111", "vera@vet.com", "VET"));

        LocalDateTime fechaHora = LocalDateTime.of(2035, 6, 15, 10, 0);
        UsuarioAutenticado actor = new UsuarioAutenticado(10L, "cliente@test.com", "CLIENTE");
        CrearCitaRequest request = new CrearCitaRequest(101L, 7L, fechaHora, "Consulta concurrencia");

        int hilos = 10;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);
        AtomicInteger exitos = new AtomicInteger();
        List<Throwable> inesperadas = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < hilos; i++) {
            executor.submit(() -> {
                listos.countDown();
                try {
                    inicio.await();
                    citaService.crear(request, actor);
                    exitos.incrementAndGet();
                } catch (ConflictoException ex) {
                    // esperado: la concurrencia debe traducirse en conflictos
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    inesperadas.add(ex);
                } catch (Throwable ex) {
                    inesperadas.add(ex);
                } finally {
                    fin.countDown();
                }
            });
        }

        assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
        inicio.countDown();
        assertThat(fin.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(inesperadas).isEmpty();
        assertThat(exitos.get()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }
}