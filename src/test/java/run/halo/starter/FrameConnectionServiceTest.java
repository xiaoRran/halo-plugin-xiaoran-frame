package run.halo.starter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FrameConnectionServiceTest {
 @Test void connectRequiresOneTimeAdministratorProof(){var client=mock(ReactiveExtensionClient.class);var profile=new FrameProfile();profile.setEnabled(true);profile.setSiteName("测试博客");profile.setAuthor("测试博主");when(client.fetch(FrameProfile.class,"site")).thenReturn(Mono.just(profile));var service=new FrameConnectionService(client);assertThrows(Exception.class,()->service.verify("wrong").block());var code=service.issue();var response=service.verify(code).block();assertEquals("测试博客",response.get("siteName"));assertFalse(response.containsKey("openid"));assertFalse(response.containsKey("token"));assertThrows(Exception.class,()->service.verify(code).block());}
 @Test void disabledSiteCannotConnectAndOldCodeIsInvalidated(){var client=mock(ReactiveExtensionClient.class);when(client.fetch(FrameProfile.class,"site")).thenReturn(Mono.empty());var service=new FrameConnectionService(client);var old=service.issue();var current=service.issue();assertThrows(Exception.class,()->service.verify(old).block());assertThrows(Exception.class,()->service.verify(current).block());assertEquals(false,service.profile().block().get("enabled"));}
}
