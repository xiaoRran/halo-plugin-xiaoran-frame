package run.halo.starter;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class SessionEndpoint implements CustomEndpoint {
    private final WechatIdentity identity;
    private final OwnerSessions sessions;
    private final OwnerBindingService bindings;

    public SessionEndpoint(WechatIdentity identity, OwnerSessions sessions, OwnerBindingService bindings) {
        this.identity = identity;
        this.sessions = sessions;
        this.bindings = bindings;
    }

    @Override public GroupVersion groupVersion() {
        return new GroupVersion("api.space.onoe.cn", "v1alpha1");
    }

    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route().POST("/sessions", this::login)
            .POST("/bindings", this::bind)
            .DELETE("/bindings/current", this::unbind)
            .DELETE("/sessions/current", this::logout).build();
    }

    private Mono<ServerResponse> login(ServerRequest request) {
        long generation = sessions.generation();
        return request.bodyToMono(Login.class)
            .flatMap(body -> identity.exchange(body.code()).flatMap(bindings::verify))
            .flatMap(owner -> ServerResponse.ok().header("Cache-Control", "no-store")
                .bodyValue(Map.of("token", sessions.issue(generation), "expiresIn", 43200)))
            .switchIfEmpty(ServerResponse.badRequest().build());
    }

    private Mono<ServerResponse> logout(ServerRequest request) {
        String auth = request.headers().firstHeader("X-Xiaoran-Session");
        if (auth == null || !sessions.valid(auth)) {
            return ServerResponse.status(401).build();
        }
        sessions.revoke(auth);
        return ServerResponse.noContent().build();
    }

    private Mono<ServerResponse> unbind(ServerRequest request) {
        // Fresh WeChat proof also allows the bound owner to unlink while logged out.
        return request.bodyToMono(Login.class)
            .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST)))
            .flatMap(body -> identity.exchange(body.code()).flatMap(bindings::unbind))
            .then(Mono.fromRunnable(sessions::revokeAll))
            .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> bind(ServerRequest request) {
        return request.bodyToMono(Bind.class)
            .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST)))
            .flatMap(body -> identity.exchange(body.code()).flatMap(openid -> bindings.bind(body.bindingCode(), openid)))
            .then(ServerResponse.noContent().build());
    }
    public record Bind(String code, String bindingCode) {}
    public record Login(String code) {}
}
