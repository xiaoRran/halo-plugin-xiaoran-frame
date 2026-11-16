package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class McpAdminEndpoint implements CustomEndpoint {
    private final McpConfigService config;
    private final McpAccess access;
    public McpAdminEndpoint(McpConfigService config,McpAccess access){this.config=config;this.access=access;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("console.api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route()
        .GET("/mcpconfig/current",r->config.view().flatMap(v->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(v)))
        .PUT("/mcpconfig/current",r->r.bodyToMono(McpConfigService.Input.class).flatMap(config::save).flatMap(v->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(v)))
        .DELETE("/mcptokens/current",r->config.revokeMcpToken().then(ServerResponse.noContent().build()))
        .POST("/mcptokens",r->config.issueMcpToken().flatMap(v->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(v)))
        .GET("/mcpagents",r->config.listAgents().flatMap(v->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(v)))
        .DELETE("/mcpagents/{id}",r->config.revokeAgent(r.pathVariable("id")).then(ServerResponse.noContent().build()))
        .POST("/mcpagentcodes",r->config.settings().flatMap(s->{if(!s.isEnabled())return ServerResponse.status(409).bodyValue(Map.of("detail","请先启用MCP"));return config.listAgents().flatMap(agents->agents.size()>=8?ServerResponse.status(409).bodyValue(Map.of("detail","最多连接8个Agent，请先断开一个")):ServerResponse.ok().header("Cache-Control","no-store").bodyValue(Map.of("code",access.issueAgentCode(),"expiresIn",300)));}))
        .POST("/mcpconnectcodes",r->config.settings().flatMap(s->s.isEnabled()?ServerResponse.ok().header("Cache-Control","no-store").bodyValue(Map.of("code",access.issueCode(),"expiresIn",300)):ServerResponse.status(409).bodyValue(Map.of("detail","请先启用 MCP")))).build();}
}
