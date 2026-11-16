package run.halo.starter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MomentPublishEndpointTest {
    @Test void themeExcerptCollapsesWhitespaceAndPreservesUnicode() {
        assertEquals("测试 一下",MomentPublishEndpoint.excerpt("  测试\n一下  "));
        String text="星".repeat(159)+"🌟"+"末尾";
        String summary=MomentPublishEndpoint.excerpt(text);
        assertEquals(161,summary.codePointCount(0,summary.length()));
        assertTrue(summary.endsWith("🌟…"));
    }

    @Test void anonymousAndUnconfirmedRequestsCannotPublish() {
        var client=mock(ReactiveExtensionClient.class);var sessions=new OwnerSessions();
        var http=WebTestClient.bindToRouterFunction(new MomentPublishEndpoint(client,sessions,"owner").endpoint()).build();
        http.post().uri("/moments").exchange().expectStatus().isUnauthorized();
        http.post().uri("/moments").header("X-Xiaoran-Session",sessions.issue())
            .header("Content-Type","application/json").bodyValue("{\"content\":\"hello\",\"confirmPublic\":false}")
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(client);
    }
    @Test void publicTextIsEscapedAndOwnerIsServerControlled() {
        var client=mock(ReactiveExtensionClient.class);var sessions=new OwnerSessions();
        when(client.create(any(Unstructured.class))).thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        var http=WebTestClient.bindToRouterFunction(new MomentPublishEndpoint(client,sessions,"owner").endpoint()).build();
        http.post().uri("/moments").header("X-Xiaoran-Session",sessions.issue()).header("Content-Type","application/json")
            .bodyValue("{\"content\":\"<script>bad</script>\",\"confirmPublic\":true}")
            .exchange().expectStatus().isCreated();
        verify(client).create(argThat((Unstructured moment)->{
            assertEquals("<script>bad</script>",moment.getMetadata().getAnnotations().get("thyuu_post_excerpt"));
            assertEquals("owner",Unstructured.getNestedValue(moment.getData(),"spec","owner").orElseThrow());
            assertEquals("<p>&lt;script&gt;bad&lt;/script&gt;</p>",Unstructured.getNestedValue(moment.getData(),"spec","content","html").orElseThrow());return true;
        }));
    }
}
