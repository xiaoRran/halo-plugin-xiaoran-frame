package run.halo.starter;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
@Component
public class FrameConnectionService {
    private final ReactiveExtensionClient client;
    private String code;
    private Instant expiry=Instant.EPOCH;
    public FrameConnectionService(ReactiveExtensionClient client){this.client=client;}
    public Mono<Map<String,Object>> profile(){return client.fetch(FrameProfile.class,"site").map(p->Map.<String,Object>of(
        "protocol","xiaoran-frame/1","enabled",p.isEnabled(),"siteName",text(p.getSiteName(),60),
        "author",text(p.getAuthor(),40),"description",text(p.getDescription(),240),"tagline",text(p.getTagline(),120),"avatar",text(p.getAvatar(),2048)))
        .defaultIfEmpty(Map.of("protocol","xiaoran-frame/1","enabled",false));}
    private static String text(String value,int max){return value==null?"":value.trim().substring(0,Math.min(value.trim().length(),max));}
    public synchronized String issue(){byte[] bytes=new byte[18];new SecureRandom().nextBytes(bytes);code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);expiry=Instant.now().plusSeconds(300);return code;}
    private synchronized boolean consume(String value){if(value==null||code==null||!expiry.isAfter(Instant.now())||!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),value.getBytes(StandardCharsets.UTF_8)))return false;code=null;return true;}
    public Mono<Map<String,Object>> verify(String value){if(!consume(value))return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,"连接码无效或已过期"));return profile().flatMap(p->Boolean.TRUE.equals(p.get("enabled"))?Mono.just(p):Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,"请先启用 Frame 连接")));}
}
