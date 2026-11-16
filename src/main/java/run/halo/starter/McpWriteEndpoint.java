package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class McpWriteEndpoint implements CustomEndpoint {
    private final McpConfigService config;
    private final McpImageService images;
    public McpWriteEndpoint(McpConfigService config,McpImageService images){this.config=config;this.images=images;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().POST("/mcpimages",request->config.settings().flatMap(settings->{if(!settings.isEnabled())return ServerResponse.status(503).build();return config.agentAccess(request.headers().firstHeader("X-Frame-MCP-Token")).flatMap(access->{if(access.agentId().isBlank())return ServerResponse.status(401).build();if(!access.canWrite())return ServerResponse.status(403).bodyValue(Map.of("detail","请在Halo为这个Agent开启写入权限"));return request.multipartData().flatMap(parts->{if(!(parts.getFirst("file") instanceof FilePart file)||!(parts.getFirst("request_id") instanceof FormFieldPart requestId))return ServerResponse.badRequest().build();return images.multipart(access.agentId(),file,requestId.value()).flatMap(value->ServerResponse.status(201).header("Cache-Control","no-store").bodyValue(value));});});}).onErrorResume(org.springframework.core.io.buffer.DataBufferLimitException.class,e->ServerResponse.status(413).bodyValue(Map.of("detail","图片不能超过10MB"))).onErrorResume(IllegalArgumentException.class,e->ServerResponse.badRequest().bodyValue(Map.of("detail",e.getMessage())))).build();}
}
