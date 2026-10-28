package bootcamp.hibernate_practical.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookCatalogServiceTest {
    private static final String TEST_KEY = "dummy-test-key-never-use-in-production";
    private MockRestServiceServer server;
    private BookCatalogService service;
    private RestClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://www.googleapis.com/books/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        service = new BookCatalogService(client, TEST_KEY);
    }

    @Test
    void requestUsesApiKeyHeaderAndReturnsBooks() {
        server.expect(requestTo("https://www.googleapis.com/books/v1/volumes?q=intitle:Dune&maxResults=5"))
                .andExpect(header("x-goog-api-key", TEST_KEY))
                .andRespond(withSuccess("{\"totalItems\":1,\"items\":[{\"id\":\"test-volume\"}]}", MediaType.APPLICATION_JSON));
        assertThat(service.search("Dune")).containsEntry("totalItems", 1);
        server.verify();
    }

    @Test
    void missingKeyDoesNotSendAnyRequest() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new BookCatalogService(client, " ").search("Dune"));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void upstreamHttpErrorsAreSafe(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("Upstream echoed secret: " + TEST_KEY));
        assertSafeError(HttpStatus.BAD_GATEWAY, "External book search failed. Please try again later.");
    }

    @Test
    void timeoutsHaveClearSafeError() {
        server.expect(anything()).andRespond(withException(new HttpTimeoutException(TEST_KEY)));
        assertSafeError(HttpStatus.GATEWAY_TIMEOUT, "External book search timed out. Please try again later.");
    }

    @Test
    void unavailableServiceHasClearSafeError() {
        server.expect(anything()).andRespond(withException(new ConnectException(TEST_KEY)));
        assertSafeError(HttpStatus.SERVICE_UNAVAILABLE,
                "External book search is unavailable. Please try again later.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{bad json", ""})
    void malformedOrEmptyResponseHasSafeError(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertSafeError(HttpStatus.BAD_GATEWAY, "External book search failed. Please try again later.");
    }

    @Test
    void blankOrExcessivelyLongTitleDoesNotSendRequest() {
        for (String title : new String[]{" ", "x".repeat(256)}) {
            var error = assertThrows(ResponseStatusException.class, () -> service.search(title));
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        server.verify();
    }

    private void assertSafeError(HttpStatus expectedStatus, String expectedMessage) {
        var error = assertThrows(ResponseStatusException.class, () -> service.search("Dune"));
        assertThat(error.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(error.getReason()).isEqualTo(expectedMessage).doesNotContain(TEST_KEY);
        assertThat(error.getCause()).isNull();
        server.verify();
    }
}
