package run.halo.starter;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
@Component
public class DailyEndpoint implements CustomEndpoint {
    private final ReactiveExtensionClient client; private final OwnerSessions sessions;
    public DailyEndpoint(ReactiveExtensionClient client,OwnerSessions sessions){this.client=client;this.sessions=sessions;}
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().GET("/daily/{kind}",this::get).PUT("/daily/{kind}",this::put).build();}
    private boolean allowed(ServerRequest r){return sessions.valid(r.headers().firstHeader("X-Xiaoran-Session"));}
    private String kind(ServerRequest r){String k=r.pathVariable("kind");if(!Set.of("todo","anniversary","link").contains(k))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return k;}
    private Mono<ServerResponse> get(ServerRequest r){if(!allowed(r))return ServerResponse.status(401).build();return client.fetch(DailyState.class,kind(r)).flatMap(x->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(x)).switchIfEmpty(ServerResponse.notFound().build());}
    private Mono<ServerResponse> put(ServerRequest r){
        if(!allowed(r))return ServerResponse.status(401).build();String k=kind(r);
        return r.bodyToMono(Input.class).flatMap(input->{
            if(input.items()==null||input.items().size()>200)return ServerResponse.badRequest().build();
            Set<String> ids=new java.util.HashSet<>();
            for(var i:input.items()){
                if(i==null||i.id()==null||i.id().length()>100||!ids.add(i.id())||i.text()==null||i.text().isBlank()||i.text().length()>100)return ServerResponse.badRequest().build();
                if(k.equals("link")&&(i.url()==null||i.url().length()>2000||!i.url().matches("https?://[^\\s]+")))return ServerResponse.badRequest().build();
                if(k.equals("anniversary")){try{java.time.LocalDate.parse(i.date());}catch(Exception e){return ServerResponse.badRequest().build();}}
            }
            return client.fetch(DailyState.class,k).flatMap(existing->{
                if(!Objects.equals(existing.getMetadata().getVersion(),input.revision()))return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"云端已更新，请先重新拉取"));
                existing.setItems(input.items());return client.update(existing);
            }).switchIfEmpty(Mono.defer(()->{
                if(input.revision()!=null)return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT));
                var state=new DailyState();var metadata=new Metadata();metadata.setName(k);state.setMetadata(metadata);state.setItems(input.items());return client.create(state);
            })).flatMap(saved->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(saved));
        }).switchIfEmpty(ServerResponse.badRequest().build());
    }
    public record Input(Long revision,List<DailyState.Item> items){}
}
