package com.veterinaria.mascotas.client;

import com.veterinaria.mascotas.exception.RecursoNoEncontradoException;
import com.veterinaria.mascotas.exception.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientUsuarioClient implements UsuarioClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientUsuarioClient.class);

    private final RestClient restClient;
    private final String claveInterna;

    public RestClientUsuarioClient(
            @Value("${veterinaria.clients.user-service.base-url}") String baseUrl,
            @Value("${veterinaria.clients.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${veterinaria.clients.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${veterinaria.security.internal-key}") String claveInterna
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.claveInterna = claveInterna;
    }

    @Override
    public UsuarioInternaDTO obtener(Long id) {
        try {
            return restClient.get()
                    .uri("/internal/usuarios/{id}", id)
                    .header("X-Internal-Key", claveInterna)
                    .retrieve()
                    .body(UsuarioInternaDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Usuario {} no encontrado en user-service", id);
            throw new RecursoNoEncontradoException("El cliente no existe");
        } catch (RestClientException ex) {
            log.error("Error consultando user-service para el usuario {}: {}", id, ex.getMessage());
            throw new ServicioNoDisponibleException("user-service no disponible", ex);
        }
    }
}
