package run.halo.starter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.assertThrows;
import run.halo.app.extension.ReactiveExtensionClient;
import static org.mockito.Mockito.*;
class OwnerBindingServiceTest {
    @Test void invalidCodeCannotAccessStorage() {
        var client=mock(ReactiveExtensionClient.class);
        var service=new OwnerBindingService(client);
        service.issueCode();
        assertThrows(Exception.class, () -> service.bind("incorrect", "openid").block());
        verifyNoInteractions(client);
    }
    @Test void codeIsSingleUseAndExistingBindingCannotBeReplaced() {
        var client=mock(ReactiveExtensionClient.class);
        var existing=new OwnerBinding();existing.setOpenid("original");
        when(client.fetch(OwnerBinding.class,"owner")).thenReturn(Mono.just(existing));
        var service=new OwnerBindingService(client);
        var code=service.issueCode();
        assertThrows(Exception.class, () -> service.bind(code,"replacement").block());
        assertThrows(Exception.class, () -> service.bind(code,"replacement").block());
        verify(client,times(1)).fetch(OwnerBinding.class,"owner");
        verifyNoMoreInteractions(client);
    }
    @Test void unlinkOnlyDeletesMatchingBindingAndNeverBusinessData() {
        var client=mock(ReactiveExtensionClient.class);var binding=new OwnerBinding();binding.setOpenid("owner");
        when(client.fetch(OwnerBinding.class,"owner")).thenReturn(Mono.just(binding));when(client.delete(binding)).thenReturn(Mono.just(binding));
        var service=new OwnerBindingService(client);
        assertThrows(Exception.class,()->service.unbind("other").block());verify(client,never()).delete(any(OwnerBinding.class));
        service.unbind("owner").block();verify(client).delete(binding);verify(client,times(2)).fetch(OwnerBinding.class,"owner");verifyNoMoreInteractions(client);
    }
}
