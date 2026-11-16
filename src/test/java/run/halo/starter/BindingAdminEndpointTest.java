package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
class BindingAdminEndpointTest {
 @Test void boundOwnerHasStatusButCannotGenerateAnotherCode(){var bindings=mock(OwnerBindingService.class);when(bindings.bound()).thenReturn(Mono.just(true));var http=WebTestClient.bindToRouterFunction(new BindingAdminEndpoint(bindings).endpoint()).build();http.get().uri("/bindingstatus").exchange().expectStatus().isOk().expectBody().jsonPath("$.bound").isEqualTo(true);http.post().uri("/bindingcodes").exchange().expectStatus().isEqualTo(409);verify(bindings,never()).issueCode();}
}
