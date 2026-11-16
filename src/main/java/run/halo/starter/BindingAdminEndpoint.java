package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class BindingAdminEndpoint implements CustomEndpoint {
    private final OwnerBindingService bindings;
    public BindingAdminEndpoint(OwnerBindingService bindings) { this.bindings = bindings; }
    @Override public GroupVersion groupVersion() { return new GroupVersion("console.api.space.onoe.cn", "v1alpha1"); }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/bindingstatus", request -> bindings.bound().flatMap(bound -> ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(Map.of("bound", bound))))
            .POST("/bindingcodes", request -> bindings.bound().flatMap(bound -> bound
                ? ServerResponse.status(409).build()
                : ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(Map.of("code", bindings.issueCode(), "expiresIn", 300)))).build();
    }
}
