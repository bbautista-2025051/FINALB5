package com.veterinaria.expedientes.client;

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
public class MascotaClientImpl implements MascotaClient {

    private static final String HEADER_CLAVE_INTERNA = "X-Internal-Key";

    private final RestClient restClient;

    public MascotaClientImpl(
            @Value("${veterinaria.clients.mascota-service.base-url}") String baseUrl,
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
    public MascotaInternaDTO obtener(Long id) {
        try {
            MascotaInternaDTO mascota = restClient.get()
                    .uri("/internal/mascotas/{id}", id)
                    .retrieve()
                    .body(MascotaInternaDTO.class);
            if (mascota == null) {
                throw new ServicioNoDisponibleException("mascota-service no devolvio contenido para la mascota " + id);
            }
            return mascota;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("Mascota no encontrada con id " + id);
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("No se pudo obtener la mascota con id " + id, ex);
        }
    }
}
