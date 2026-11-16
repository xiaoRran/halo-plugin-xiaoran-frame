package run.halo.starter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
/** Legacy URL retained as a 404; the public plugin uses system fonts. */
@Component
public class StarFontEndpoint implements CustomEndpoint {
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().GET("/fonts/{file}",request->ServerResponse.notFound().build()).build();}
}
