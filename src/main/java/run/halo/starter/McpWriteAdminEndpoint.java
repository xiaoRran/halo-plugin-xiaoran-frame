package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class McpWriteAdminEndpoint implements CustomEndpoint {
    private final McpConfigService config;
    private final McpWriteConfig writeConfig;
    public McpWriteAdminEndpoint(McpConfigService config,McpWriteConfig writeConfig){this.config=config;this.writeConfig=writeConfig;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("console.api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route()
        .GET("/mcpwriteconfig/current",r->writeConfig.view().flatMap(v->ServerResponse.ok().bodyValue(v)))
        .PUT("/mcpwriteconfig/current",r->r.bodyToMono(McpWriteConfig.Input.class).flatMap(writeConfig::save).flatMap(v->ServerResponse.ok().bodyValue(v)))
        .PUT("/mcpagents/{id}/permissions",r->r.bodyToMono(Permission.class).flatMap(input->{if(input.writeEnabled()==null)return ServerResponse.badRequest().build();return (input.writeEnabled()?r.principal().map(java.security.Principal::getName).defaultIfEmpty("").flatMap(writeConfig::initialize).then():reactor.core.publisher.Mono.<Void>empty()).then(config.setAgentWrite(r.pathVariable("id"),input.writeEnabled())).then(ServerResponse.noContent().build());}))
        .build();}
    public record Permission(Boolean writeEnabled){}
}
