package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import static org.mockito.Mockito.*;
class FramePublicEndpointTest {
 @Test void publicManifestDoesNotGrantManagementAndProofIsConsumed(){var client=mock(ReactiveExtensionClient.class);var profile=new FrameProfile();profile.setEnabled(true);profile.setSiteName("博客");when(client.fetch(FrameProfile.class,"site")).thenReturn(Mono.just(profile));var service=new FrameConnectionService(client);var api=WebTestClient.bindToRouterFunction(new FramePublicEndpoint(service).endpoint()).build();api.get().uri("/frame/site").exchange().expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(true).jsonPath("$.token").doesNotExist();var code=service.issue();api.post().uri("/frameverifications").bodyValue(new FramePublicEndpoint.Proof(code)).exchange().expectStatus().isOk();api.post().uri("/frameverifications").bodyValue(new FramePublicEndpoint.Proof(code)).exchange().expectStatus().isForbidden();api.post().uri("/frameverifications").bodyValue(new FramePublicEndpoint.Proof(null)).exchange().expectStatus().isForbidden();}
}
