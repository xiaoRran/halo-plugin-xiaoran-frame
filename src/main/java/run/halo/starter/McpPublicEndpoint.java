package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class McpPublicEndpoint implements CustomEndpoint {
    private final McpConfigService config;
    private final McpAccess access;
    private final McpEndpoint mcp;
    public McpPublicEndpoint(McpConfigService config,McpAccess access,McpEndpoint mcp){this.config=config;this.access=access;this.mcp=mcp;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route()
        .GET("/mcpstatus",r->config.settings().flatMap(s->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(Map.of("enabled",s.isEnabled(),"authenticated",mcp.miniAuthorized(r),"tools",3,"protocol",McpEndpoint.PROTOCOL))))
        .POST("/mcpbindings",r->config.settings().flatMap(s->{if(!s.isEnabled())return ServerResponse.status(503).build();return r.bodyToMono(Binding.class).flatMap(b->{String token=access.bind(b.code());return token==null?ServerResponse.status(403).bodyValue(Map.of("detail","MCP 连接码无效或已过期")):ServerResponse.ok().header("Cache-Control","no-store").bodyValue(Map.of("token",token,"expiresIn",43200));});}))
        .POST("/mcpenrollments",r->config.settings().flatMap(s->{if(!s.isEnabled())return ServerResponse.status(503).build();return r.bodyToMono(Enrollment.class).flatMap(input->{if(input.agentName()!=null&&(input.agentName().length()>60||input.agentName().chars().anyMatch(Character::isISOControl)))return ServerResponse.badRequest().build();if(!access.consumeAgentCode(input.code()))return ServerResponse.status(403).bodyValue(Map.of("detail","接入指令已使用或过期，请重新生成"));return config.enrollAgent(input.agentName()).flatMap(v->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(v));});}))
        .DELETE("/mcpsessions/current",r->{String token=r.headers().firstHeader("X-Frame-MCP-Session");if(!access.valid(token))return ServerResponse.status(401).build();access.revoke(token);return ServerResponse.noContent().build();}).build();}
    public record Enrollment(String code,String agentName){}
    public record Binding(String code){}
}
