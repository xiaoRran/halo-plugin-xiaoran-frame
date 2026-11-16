package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
class StarFontEndpointTest {
 @Test void publicArtifactDoesNotRelayPersonalThemeFonts(){var client=WebTestClient.bindToRouterFunction(new StarFontEndpoint().endpoint()).build();client.get().uri("/fonts/07d1cbe45a67acd089b97c925db37ca9.woff2").exchange().expectStatus().isNotFound();client.get().uri("/fonts/unknown.woff2").exchange().expectStatus().isNotFound();}
}
