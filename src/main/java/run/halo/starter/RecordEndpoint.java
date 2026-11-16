package run.halo.starter;

import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.*;

@Component
public class RecordEndpoint implements CustomEndpoint {
    @org.springframework.beans.factory.annotation.Autowired
    private SubmissionGuard submissions = new SubmissionGuard();
    private final ReactiveExtensionClient client;
    private final OwnerSessions sessions;
    public RecordEndpoint(ReactiveExtensionClient client, OwnerSessions sessions) {
        this.client = client;
        this.sessions = sessions;
    }
    @Override public GroupVersion groupVersion() {
        return new GroupVersion("api.space.onoe.cn", "v1alpha1");
    }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route().GET("/records", this::list)
            .POST("/records", this::create).build();
    }
    private boolean authorized(ServerRequest request) {
        String auth = request.headers().firstHeader("X-Xiaoran-Session");
        return auth != null && sessions.valid(auth);
    }
    private Mono<ServerResponse> list(ServerRequest request) {
        if (!authorized(request)) return ServerResponse.status(401).build();
        int page;
        try { page = Integer.parseInt(request.queryParam("page").orElse("1")); }
        catch (NumberFormatException error) { return ServerResponse.badRequest().build(); }
        if (page < 1 || page > 10000) return ServerResponse.badRequest().build();
        return client.list(LifeRecord.class, record -> true,
                Comparator.comparing((LifeRecord record) -> record.getMetadata().getCreationTimestamp(),
                    Comparator.nullsLast(Comparator.reverseOrder())), page, 20)
            .flatMap(records -> ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(records));
    }
    private Mono<ServerResponse> create(ServerRequest request) {
        if (!authorized(request)) return ServerResponse.status(401).build();
        return request.bodyToMono(Input.class).flatMap(input -> {
            if (input.content() == null || input.content().isBlank() || input.content().length() > 10000
                || input.mood() != null && input.mood().length() > 100) {
                return ServerResponse.badRequest().build();
            }
            var record = new LifeRecord();
            var metadata = new Metadata();
            metadata.setName("record-" + UUID.randomUUID());
            record.setMetadata(metadata);
            var spec = new LifeRecord.Spec();
            spec.setContent(input.content().trim());
            spec.setMood(input.mood() == null ? "" : input.mood().trim());
            record.setSpec(spec);
            return submissions.run(input.requestId() == null ? null : "record-" + input.requestId(), input.toString(), () -> client.create(record)).flatMap(saved -> ServerResponse.status(201)
                .header("Cache-Control", "no-store").bodyValue(saved));
        }).switchIfEmpty(ServerResponse.badRequest().build());
    }
    public record Input(String content, String mood, String requestId) {}
}
