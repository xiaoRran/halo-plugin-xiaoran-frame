package run.halo.starter;
import java.util.Set;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
@Component
public class PersonalEndpoint implements CustomEndpoint {
 private final ReactiveExtensionClient client; private final OwnerSessions sessions;
 public PersonalEndpoint(ReactiveExtensionClient client,OwnerSessions sessions){this.client=client;this.sessions=sessions;}
 @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
 @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().GET("/personal/{key}",this::get).PUT("/personal/{key}",this::put).build();}
 private String key(ServerRequest r){String key=r.pathVariable("key");if(!Set.of("favorites","private-draft","moment-draft","moment-images").contains(key))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return key;}
 private boolean allowed(ServerRequest r){return sessions.valid(r.headers().firstHeader("X-Xiaoran-Session"));}
 private Mono<ServerResponse> get(ServerRequest r){if(!allowed(r))return ServerResponse.status(401).build();return client.fetch(PersonalState.class,key(r)).flatMap(x->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(x)).switchIfEmpty(ServerResponse.notFound().build());}
 private Mono<ServerResponse> put(ServerRequest r){
  if(!allowed(r))return ServerResponse.status(401).build();String k=key(r);
  return r.bodyToMono(Input.class).flatMap(input->{
   if(!valid(k,input.value()))return ServerResponse.badRequest().build();
   return client.fetch(PersonalState.class,k).flatMap(existing->{
    if(!Objects.equals(existing.getMetadata().getVersion(),input.revision()))return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT));
    existing.setValue(input.value());return client.update(existing);
   }).switchIfEmpty(Mono.defer(()->{if(input.revision()!=null)return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT));var state=new PersonalState();var metadata=new Metadata();metadata.setName(k);state.setMetadata(metadata);state.setValue(input.value());return client.create(state);}))
   .flatMap(saved->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(saved));
  }).switchIfEmpty(ServerResponse.badRequest().build());
 }
 static boolean valid(String key,String value){try{if(value==null||value.length()>500000)return false;var tree=new ObjectMapper().readTree(value);if(key.equals("moment-images"))return tree.isArray()&&tree.size()<=9;return key.equals("favorites")?tree.isArray()&&tree.size()<=200:tree.isTextual()&&tree.textValue().length()<=10000;}catch(Exception e){return false;}}
 public record Input(Long revision,String value){}
}
