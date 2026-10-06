package com.veterinaria.expedientes.client;

import com.veterinaria.expedientes.exception.ConflictoException;
import com.veterinaria.expedientes.exception.RecursoNoEncontradoException;
import com.veterinaria.expedientes.exception.ServicioNoDisponibleException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CitaClientImpl implements CitaClient {

    private static final String HEADER_CLAVE_INTERNA = "X-Internal-Key";

    private final RestClient restClient;

    public CitaClientImpl(
            @Value("${veterinaria.clients.cita-service.base-url}") String baseUrl,
            @Value("${veterinaria.security.internal-key}") String internalKey,
            @Value("${veterinaria.clients.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${veterinaria.clients.read-timeout-ms:5000}") long readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HEADER_CLAVE_INTERNA, internalKey)
                .build();
    }

    @Override
    public CitaInternaDTO obtener(Long id) {
        try {
            CitaInternaDTO cita = restClient.get()
                    .uri("/internal/citas/{id}", id)
                    .retrieve()
                    .body(CitaInternaDTO.class);
            if (cita == null) {
                throw new ServicioNoDisponibleException("cita-service no devolvio contenido para la cita " + id);
            }
            return cita;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("Cita no encontrada con id " + id);
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("No se pudo obtener la cita con id " + id, ex);
        }
    }

    @Override
    public CitaInternaDTO completar(Long id) {
        try {
            CitaInternaDTO cita = restClient.post()
                    .uri("/internal/citas/{id}/completar", id)
                    .retrieve()
                    .body(CitaInternaDTO.class);
            if (cita == null) {
                throw new ServicioNoDisponibleException("cita-service no devolvio contenido para la cita " + id);
            }
            return cita;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("Cita no encontrada con id " + id);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new ConflictoException("La cita no está pendiente");
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("No se pudo completar la cita con id " + id, ex);
        }
    }
}
