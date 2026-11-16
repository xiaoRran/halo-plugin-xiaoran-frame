package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import static org.mockito.Mockito.*;
class DailyEndpointTest {
 @Test void anonymousCannotReadOrWrite(){var client=mock(ReactiveExtensionClient.class);var http=WebTestClient.bindToRouterFunction(new DailyEndpoint(client,new OwnerSessions()).endpoint()).build();http.get().uri("/daily/todo").exchange().expectStatus().isUnauthorized();http.put().uri("/daily/todo").exchange().expectStatus().isUnauthorized();verifyNoInteractions(client);}
 @Test void staleVersionCannotOverwriteCloud(){var client=mock(ReactiveExtensionClient.class);var state=new DailyState();var metadata=new Metadata();metadata.setName("todo");metadata.setVersion(2L);state.setMetadata(metadata);when(client.fetch(DailyState.class,"todo")).thenReturn(Mono.just(state));var sessions=new OwnerSessions();var http=WebTestClient.bindToRouterFunction(new DailyEndpoint(client,sessions).endpoint()).build();http.put().uri("/daily/todo").header("X-Xiaoran-Session",sessions.issue()).header("Content-Type","application/json").bodyValue("{\"revision\":1,\"items\":[]}").exchange().expectStatus().isEqualTo(409);verify(client,never()).update(any(DailyState.class));}
}
