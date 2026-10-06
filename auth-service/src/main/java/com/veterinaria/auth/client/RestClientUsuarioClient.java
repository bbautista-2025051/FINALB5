package com.veterinaria.auth.client;

import com.veterinaria.auth.dto.UsuarioDTO;
import com.veterinaria.auth.dto.UsuarioInternoRequest;
import com.veterinaria.auth.exception.ConflictoException;
import com.veterinaria.auth.exception.CredencialesInvalidasException;
import com.veterinaria.auth.exception.RecursoNoEncontradoException;
import com.veterinaria.auth.exception.ServicioNoDisponibleException;
import com.veterinaria.auth.exception.SolicitudInvalidaException;
import java.util.Map;
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

    public static final String HEADER_CLAVE_INTERNA = "X-Internal-Key";

    private final RestClient restClient;
    private final String claveInterna;

    public RestClientUsuarioClient(
            @Value("${veterinaria.clients.user-service.base-url}") String baseUrl,
            @Value("${veterinaria.clients.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${veterinaria.clients.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${veterinaria.security.internal-key}") String claveInterna
    ) {
        if (claveInterna == null || claveInterna.isBlank()) {
            throw new IllegalStateException("INTERNAL_SERVICE_KEY no configurado");
        }
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
    public UsuarioDTO verificarCredenciales(String email, String password) {
        try {
            return restClient.post()
                    .uri("/internal/usuarios/verificar-credenciales")
                    .header(HEADER_CLAVE_INTERNA, claveInterna)
                    .body(Map.of("email", email, "password", password))
                    .retrieve()
                    .body(UsuarioDTO.class);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new CredencialesInvalidasException("Credenciales inválidas");
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("El usuario no existe");
        } catch (RestClientException ex) {
            log.error("Error consultando user-service para verificar credenciales: {}", ex.getMessage());
            throw new ServicioNoDisponibleException("user-service no disponible", ex);
        }
    }

    @Override
    public UsuarioDTO crear(UsuarioInternoRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/usuarios")
                    .header(HEADER_CLAVE_INTERNA, claveInterna)
                    .body(request)
                    .retrieve()
                    .body(UsuarioDTO.class);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new ConflictoException("El email ya está registrado");
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new SolicitudInvalidaException("Datos de registro inválidos según user-service");
        } catch (RestClientException ex) {
            log.error("Error consultando user-service para crear usuario: {}", ex.getMessage());
            throw new ServicioNoDisponibleException("user-service no disponible", ex);
        }
    }

    @Override
    public UsuarioDTO obtener(Long id) {
        try {
            return restClient.get()
                    .uri("/internal/usuarios/{id}", id)
                    .header(HEADER_CLAVE_INTERNA, claveInterna)
                    .retrieve()
                    .body(UsuarioDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("El usuario ya no existe");
        } catch (RestClientException ex) {
            log.error("Error consultando user-service para el usuario {}: {}", id, ex.getMessage());
            throw new ServicioNoDisponibleException("user-service no disponible", ex);
        }
    }
}
