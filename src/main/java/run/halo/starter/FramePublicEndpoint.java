package run.halo.starter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class FramePublicEndpoint implements CustomEndpoint {
    private final FrameConnectionService service;
    public FramePublicEndpoint(FrameConnectionService service){this.service=service;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route()
        .GET("/frame/site",r->service.profile().flatMap(p->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(p)))
        .POST("/frameverifications",r->r.bodyToMono(Proof.class).flatMap(p->service.verify(p.code())).flatMap(p->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(p))).build();}
    public record Proof(String code){}
}
