package run.halo.starter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class McpConfigServiceTest {
 @Test void connectionCodeIsOneTimeAndRevocable(){var access=new McpAccess();var code=access.issueCode();assertNull(access.bind("wrong"));var token=access.bind(code);assertTrue(access.valid(token));assertNull(access.bind(code));access.revoke(token);assertFalse(access.valid(token));var first=access.issueCode();access.issueCode();assertNull(access.bind(first));}
 @Test void permanentAgentCredentialIsHashedMaskedAndCanBeRevoked(){var client=mock(ReactiveExtensionClient.class);var saved=new AtomicReference<Secret>();when(client.fetch(eq(Secret.class),anyString())).thenAnswer(i->Mono.justOrEmpty(saved.get()));when(client.fetch(eq(McpSettings.class),anyString())).thenReturn(Mono.empty());when(client.create(any(Secret.class))).thenAnswer(i->{var s=(Secret)i.getArgument(0);s.getMetadata().setVersion(0L);saved.set(s);return Mono.just(s);});when(client.update(any(Secret.class))).thenAnswer(i->{saved.set(i.getArgument(0));return Mono.just(saved.get());});var config=new McpConfigService(client);var token=config.issueMcpToken().block().get("token");assertTrue(config.validMcpToken(token).block());assertNotEquals(token,McpConfigService.value(saved.get(),"mcpHash"));assertFalse(config.view().block().containsKey("token"));var next=config.issueMcpToken().block().get("token");assertFalse(config.validMcpToken(token).block());assertTrue(config.validMcpToken(next).block());config.revokeMcpToken().block();assertFalse(config.validMcpToken(next).block());}
}
