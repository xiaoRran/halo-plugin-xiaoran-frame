package run.halo.starter;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.*;

@Component
public class MomentDeleteEndpoint implements CustomEndpoint {
    private static final GroupVersionKind MOMENT = new GroupVersionKind("moment.halo.run", "v1alpha1", "Moment");
    private final ReactiveExtensionClient client;
    private final OwnerSessions sessions;
    private final String owner;
    @org.springframework.beans.factory.annotation.Autowired
    public MomentDeleteEndpoint(ReactiveExtensionClient client, OwnerSessions sessions) {
        this(client, sessions, System.getenv("XIAORAN_HALO_OWNER"));
    }
    MomentDeleteEndpoint(ReactiveExtensionClient client, OwnerSessions sessions, String owner) {
        this.client=client; this.sessions=sessions; this.owner=owner;
    }
    @Override public GroupVersion groupVersion() { return new GroupVersion("api.space.onoe.cn", "v1alpha1"); }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route().GET("/momentpermissions", this::permissions)
            .DELETE("/moments/{name}", this::delete).build();
    }
    private boolean authorized(ServerRequest request) { return sessions.valid(request.headers().firstHeader("X-Xiaoran-Session")); }
    private boolean configured() { return owner!=null && !owner.isBlank(); }
    private Mono<ServerResponse> permissions(ServerRequest request) {
        if(!authorized(request))return ServerResponse.status(401).build();
        if(!configured())return ServerResponse.status(503).build();
        return ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(Map.of("owner", owner));
    }
    private Mono<ServerResponse> delete(ServerRequest request) {
        if(!authorized(request))return ServerResponse.status(401).build();
        if(!configured())return ServerResponse.status(503).build();
        String name=request.pathVariable("name");
        if(!name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,252}"))return ServerResponse.badRequest().build();
        return client.fetch(MOMENT, name).flatMap(moment -> {
            if(!owner.equals(Unstructured.getNestedValue(moment.getData(), "spec", "owner").orElse(null)))
                return ServerResponse.status(403).bodyValue(Map.of("detail", "只能删除自己的瞬间"));
            if(moment.getMetadata().getDeletionTimestamp()!=null)return ServerResponse.noContent().build();
            // The Moment reconciler handles its normal deletion lifecycle. Attachments are retained.
            return client.delete(moment).then(ServerResponse.noContent().build());
        }).switchIfEmpty(ServerResponse.noContent().build());
    }
}
