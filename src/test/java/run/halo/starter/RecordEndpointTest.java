package run.halo.starter;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.app.extension.ReactiveExtensionClient;
import static org.mockito.Mockito.*;

class RecordEndpointTest {
    @Test void anonymousAndForgedSessionsCannotReadOrWrite() {
        var client = mock(ReactiveExtensionClient.class);
        var endpoint = new RecordEndpoint(client, new OwnerSessions());
        var http = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
        http.get().uri("/records").exchange().expectStatus().isUnauthorized();
        http.post().uri("/records").header("X-Xiaoran-Session", "forged")
            .exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(client);
    }
    @Test void invalidInputAndPaginationNeverReachStorage() {
        var client = mock(ReactiveExtensionClient.class);
        var sessions = new OwnerSessions();
        var endpoint = new RecordEndpoint(client, sessions);
        var http = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
        String auth = sessions.issue();
        http.get().uri("/records?page=-1").header("X-Xiaoran-Session", auth)
            .exchange().expectStatus().isBadRequest();
        http.post().uri("/records").header("X-Xiaoran-Session", auth)
            .header("Content-Type", "application/json").bodyValue("{\"content\":\"   \"}")
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(client);
    }
}
