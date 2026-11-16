package run.halo.starter;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
public class OwnerBindingService {
    private final ReactiveExtensionClient client;
    private String code;
    private Instant expiry = Instant.EPOCH;
    public OwnerBindingService(ReactiveExtensionClient client) { this.client = client; }
    public synchronized String issueCode() {
        byte[] bytes = new byte[18]; new SecureRandom().nextBytes(bytes);
        code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        expiry = Instant.now().plusSeconds(300);
        return code;
    }
    private synchronized boolean consume(String supplied) {
        if (supplied == null || code == null || !expiry.isAfter(Instant.now())
            || !java.security.MessageDigest.isEqual(code.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) return false;
        code = null; return true;
    }
    public Mono<Void> bind(String supplied, String openid) {
        if (!consume(supplied)) return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "绑定码无效或已过期"));
        return client.fetch(OwnerBinding.class, "owner").flatMap(existing ->
            Mono.<OwnerBinding>error(new ResponseStatusException(HttpStatus.CONFLICT, "已绑定管理员")))
            .switchIfEmpty(Mono.defer(() -> {
                var binding = new OwnerBinding(); var metadata = new Metadata(); metadata.setName("owner");
                binding.setMetadata(metadata); binding.setOpenid(openid);
                return client.create(binding);
            })).then();
    }
    public Mono<Void> unbind(String openid) {
        return client.fetch(OwnerBinding.class, "owner")
            .filter(binding -> (binding.getMetadata() == null || binding.getMetadata().getDeletionTimestamp() == null) && openid.equals(binding.getOpenid()))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前微信未绑定管理权限")))
            .flatMap(client::delete).then();
    }
    public Mono<Boolean> bound() { return client.fetch(OwnerBinding.class, "owner").filter(binding -> binding.getMetadata() == null || binding.getMetadata().getDeletionTimestamp() == null).hasElement(); }
    public Mono<String> verify(String openid) {
        return client.fetch(OwnerBinding.class, "owner").filter(binding -> (binding.getMetadata() == null || binding.getMetadata().getDeletionTimestamp() == null) && openid.equals(binding.getOpenid()))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前微信未绑定管理权限")))
            .map(OwnerBinding::getOpenid);
    }
}
