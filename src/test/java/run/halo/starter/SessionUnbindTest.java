package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SessionUnbindTest {
 @Test void freshWechatProofRequiredAndFailureKeepsSessions(){var identity=mock(WechatIdentity.class);var bindings=mock(OwnerBindingService.class);var sessions=new OwnerSessions();String token=sessions.issue();when(identity.exchange("bad")).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)));var http=WebTestClient.bindToRouterFunction(new SessionEndpoint(identity,sessions,bindings).endpoint()).build();http.delete().uri("/bindings/current").exchange().expectStatus().isBadRequest();http.method(org.springframework.http.HttpMethod.DELETE).uri("/bindings/current").bodyValue(new SessionEndpoint.Login("bad")).exchange().expectStatus().isUnauthorized();assertTrue(sessions.valid(token));verifyNoInteractions(bindings);}
 @Test void unlinkRevokesAllSessionsAndBlockedInFlightLoginCannotIssueToken(){var identity=mock(WechatIdentity.class);var bindings=mock(OwnerBindingService.class);var sessions=new OwnerSessions();String a=sessions.issue(),b=sessions.issue();long epoch=sessions.generation();when(identity.exchange("fresh")).thenReturn(Mono.just("owner"));when(bindings.unbind("owner")).thenReturn(Mono.empty());var http=WebTestClient.bindToRouterFunction(new SessionEndpoint(identity,sessions,bindings).endpoint()).build();http.method(org.springframework.http.HttpMethod.DELETE).uri("/bindings/current").bodyValue(new SessionEndpoint.Login("fresh")).exchange().expectStatus().isNoContent();assertFalse(sessions.valid(a));assertFalse(sessions.valid(b));assertThrows(ResponseStatusException.class,()->sessions.issue(epoch));verify(bindings).unbind("owner");}
}
