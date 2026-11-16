package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PersonalEndpointTest {
 @Test void anonymousCannotReadOrWrite(){var client=mock(ReactiveExtensionClient.class);var http=WebTestClient.bindToRouterFunction(new PersonalEndpoint(client,new OwnerSessions()).endpoint()).build();http.get().uri("/personal/private-draft").exchange().expectStatus().isUnauthorized();http.put().uri("/personal/favorites").exchange().expectStatus().isUnauthorized();verifyNoInteractions(client);}
 @Test void staleVersionCannotOverwriteDraft(){var client=mock(ReactiveExtensionClient.class);var state=new PersonalState();var m=new Metadata();m.setName("private-draft");m.setVersion(2L);state.setMetadata(m);state.setValue("\"云端草稿\"");when(client.fetch(PersonalState.class,"private-draft")).thenReturn(Mono.just(state));var sessions=new OwnerSessions();var http=WebTestClient.bindToRouterFunction(new PersonalEndpoint(client,sessions).endpoint()).build();http.put().uri("/personal/private-draft").header("X-Xiaoran-Session",sessions.issue()).header("Content-Type","application/json").bodyValue(new PersonalEndpoint.Input(1L,"\"本机草稿\"")).exchange().expectStatus().isEqualTo(409);verify(client,never()).update(any(PersonalState.class));}
 @Test void payloadShapeAndLimits(){assertTrue(PersonalEndpoint.valid("private-draft","\"私密草稿\""));assertTrue(PersonalEndpoint.valid("favorites","[]"));assertFalse(PersonalEndpoint.valid("private-draft","[]"));assertFalse(PersonalEndpoint.valid("favorites","{}"));assertFalse(PersonalEndpoint.valid("private-draft","\""+"a".repeat(10001)+"\""));}
}
