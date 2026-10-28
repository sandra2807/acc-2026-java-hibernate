package bootcamp.hibernate_practical.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

@Service
public class BookCatalogService {
    private final RestClient client;
    private final String apiKey;

    public BookCatalogService(RestClient googleBooksClient, @Value("${app.books.api-key:}") String apiKey) {
        this.client = googleBooksClient;
        this.apiKey = apiKey;
    }

    public Map<String, Object> search(String title) {
        if (title.isBlank() || title.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must contain 1 to 255 characters");
        }
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "External book search is not configured. Local inventory is still available.");
        }
        try {
            Map<String, Object> result = client.get()
                    .uri(builder -> builder.path("/volumes")
                            .queryParam("q", "intitle:{title}")
                            .queryParam("maxResults", 5).build(title.trim()))
                    .header("x-goog-api-key", apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (result == null) {
                throw new RestClientException("Empty external response");
            }
            return result;
        } catch (ResourceAccessException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                    throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                            "External book search timed out. Please try again later.");
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "External book search is unavailable. Please try again later.");
        } catch (RestClientException exception) {
            // Never forward the upstream body, headers, or exception (they may contain secrets).
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "External book search failed. Please try again later.");
        }
    }
}
