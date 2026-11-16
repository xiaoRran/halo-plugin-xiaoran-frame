package run.halo.starter;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.*;

@Component
public class MomentPublishEndpoint implements CustomEndpoint {
    @org.springframework.beans.factory.annotation.Autowired
    private SubmissionGuard submissions = new SubmissionGuard();
    private final ReactiveExtensionClient client;
    private final OwnerSessions sessions;
    private final String owner;
    @org.springframework.beans.factory.annotation.Autowired
    private run.halo.app.core.extension.service.AttachmentService attachments;
    @org.springframework.beans.factory.annotation.Autowired
    public MomentPublishEndpoint(ReactiveExtensionClient client, OwnerSessions sessions) {
        this(client, sessions, System.getenv("XIAORAN_HALO_OWNER"));
    }
    MomentPublishEndpoint(ReactiveExtensionClient client, OwnerSessions sessions, String owner) {
        this.client=client;this.sessions=sessions;this.owner=owner;
    }
    @Override public GroupVersion groupVersion() { return new GroupVersion("api.space.onoe.cn","v1alpha1"); }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route().POST("/moments",this::publish).build();
    }
    private Mono<ServerResponse> publish(ServerRequest request) {
        if (!sessions.valid(request.headers().firstHeader("X-Xiaoran-Session"))) return ServerResponse.status(401).build();
        if (owner==null || owner.isBlank()) return ServerResponse.status(503).bodyValue(Map.of("detail","尚未配置瞬间作者"));
        return request.bodyToMono(Input.class).flatMap(input -> {
            if (!Boolean.TRUE.equals(input.confirmPublic()) || input.content()==null
                || input.content().isBlank() || input.content().length()>10000) return ServerResponse.badRequest().build();
            String text=input.content().trim();
            String html="<p>"+text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>")+"</p>";
            List<String> names=input.images()==null?List.of():input.images();
            if(names.size()>9 || names.stream().anyMatch(name->name==null || name.isBlank())) return ServerResponse.badRequest().build();
            return reactor.core.publisher.Flux.fromIterable(names).concatMap(name ->
                client.fetch(run.halo.app.core.extension.attachment.Attachment.class,name)
                    .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"图片不存在")))
                    .flatMap(attachment -> attachments.getPermalink(attachment))
                    .map(url -> Map.of("type","PHOTO","url",url.toString(),"originType","attachment")))
                .collectList().flatMap(media -> {
            var moment=new Unstructured(Unstructured.OBJECT_MAPPER.convertValue(Map.of("apiVersion","moment.halo.run/v1alpha1","kind","Moment",
                "metadata",Map.of("name","moment-"+UUID.randomUUID(),"annotations",Map.of("thyuu_post_excerpt",excerpt(text))),
                "spec",Map.of("owner",owner,"visible","PUBLIC","approved",true,"releaseTime",Instant.now().toString(),
                    "content",Map.of("raw",text,"html",html,"medium",media))), java.util.LinkedHashMap.class));
            return submissions.run(input.requestId() == null ? null : "moment-" + input.requestId(), input.toString(), () -> client.create(moment)).flatMap(saved -> ServerResponse.status(201).bodyValue(saved));
            });
        }).switchIfEmpty(ServerResponse.badRequest().build());
    }
    static String excerpt(String text) {
        String normalized=text.strip().replaceAll("\\s+", " ");
        int count=normalized.codePointCount(0,normalized.length());
        return count<=160?normalized:normalized.substring(0,normalized.offsetByCodePoints(0,160))+"…";
    }
    public record Input(String content, Boolean confirmPublic, List<String> images, String requestId) {}
}
