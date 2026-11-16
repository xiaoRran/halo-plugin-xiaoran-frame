package run.halo.starter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import static org.mockito.Mockito.*;
class MomentDeleteEndpointTest {
 private final GroupVersionKind gvk=new GroupVersionKind("moment.halo.run","v1alpha1","Moment");
 private Unstructured moment(String owner){return new Unstructured(Unstructured.OBJECT_MAPPER.convertValue(Map.of("apiVersion","moment.halo.run/v1alpha1","kind","Moment","metadata",Map.of("name","moment-test"),"spec",Map.of("owner",owner)),java.util.LinkedHashMap.class));}
 @Test void anonymousAndExpiredSessionsCannotInspectOrDelete(){var client=mock(ReactiveExtensionClient.class);var sessions=new OwnerSessions();var api=WebTestClient.bindToRouterFunction(new MomentDeleteEndpoint(client,sessions,"owner").endpoint()).build();api.delete().uri("/moments/moment-test").exchange().expectStatus().isUnauthorized();api.get().uri("/momentpermissions").exchange().expectStatus().isUnauthorized();String token=sessions.issue();sessions.revoke(token);api.delete().uri("/moments/moment-test").header("X-Xiaoran-Session",token).exchange().expectStatus().isUnauthorized();verifyNoInteractions(client);}
 @Test void ownerCanDeleteAndRepeatedRequestsAreIdempotent(){var client=mock(ReactiveExtensionClient.class);var sessions=new OwnerSessions();var item=moment("owner");when(client.fetch(gvk,"moment-test")).thenReturn(Mono.just(item),Mono.empty());when(client.delete(item)).thenReturn(Mono.just(item));var api=WebTestClient.bindToRouterFunction(new MomentDeleteEndpoint(client,sessions,"owner").endpoint()).build();var token=sessions.issue();api.get().uri("/momentpermissions").header("X-Xiaoran-Session",token).exchange().expectStatus().isOk().expectBody().jsonPath("$.owner").isEqualTo("owner");for(int i=0;i<2;i++)api.delete().uri("/moments/moment-test").header("X-Xiaoran-Session",token).exchange().expectStatus().isNoContent();verify(client,times(1)).delete(item);}
 @Test void anotherAuthorsMomentIsNeverDeleted(){var client=mock(ReactiveExtensionClient.class);var sessions=new OwnerSessions();when(client.fetch(gvk,"moment-test")).thenReturn(Mono.just(moment("other")));var api=WebTestClient.bindToRouterFunction(new MomentDeleteEndpoint(client,sessions,"owner").endpoint()).build();api.delete().uri("/moments/moment-test").header("X-Xiaoran-Session",sessions.issue()).exchange().expectStatus().isForbidden();verify(client,never()).delete(any(Unstructured.class));}
 @Test void missingOwnerConfigurationCannotDelete(){var client=mock(ReactiveExtensionClient.class);var sessions=new OwnerSessions();var api=WebTestClient.bindToRouterFunction(new MomentDeleteEndpoint(client,sessions,null).endpoint()).build();api.delete().uri("/moments/moment-test").header("X-Xiaoran-Session",sessions.issue()).exchange().expectStatus().isEqualTo(503);verifyNoInteractions(client);}
}
