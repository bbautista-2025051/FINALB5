package com.veterinaria.citas.client;

import com.veterinaria.citas.exception.RecursoNoEncontradoException;
import com.veterinaria.citas.exception.ServicioNoDisponibleException;
import com.veterinaria.citas.security.InternalKeyFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("usuarioClient")
public class UsuarioClientImpl implements UsuarioClient {

    private final RestClient restClient;
    private final String claveInterna;

    public UsuarioClientImpl(
            @Value("${veterinaria.clients.user-service.base-url}") String baseUrl,
            @Value("${veterinaria.security.internal-key}") String claveInterna,
            @Value("${veterinaria.clients.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${veterinaria.clients.read-timeout-ms:5000}") long readTimeoutMs
    ) {
        this.claveInterna = claveInterna;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public UsuarioInternaDTO obtener(Long id) {
        try {
            return restClient.get()
                    .uri("/internal/usuarios/{id}", id)
                    .header(InternalKeyFilter.HEADER, claveInterna)
                    .retrieve()
                    .body(UsuarioInternaDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("Veterinario no encontrado con id " + id);
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("user-service no disponible", ex);
        }
    }
}