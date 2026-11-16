package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class FrameAdminEndpoint implements CustomEndpoint {
    private final FrameConnectionService service;
    public FrameAdminEndpoint(FrameConnectionService service){this.service=service;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("console.api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().POST("/framecodes",r->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(Map.of("code",service.issue(),"expiresIn",300))).build();}
}
