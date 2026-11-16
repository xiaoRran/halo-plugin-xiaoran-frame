package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.attachment.Policy;
@Component
public class McpWriteConfig {
    private final ReactiveExtensionClient client;
    public McpWriteConfig(ReactiveExtensionClient client){this.client=client;}
    private String env(String name){return java.util.Objects.toString(System.getenv(name),"");}
    public Mono<McpWriteSettings> current(){return client.fetch(McpWriteSettings.class,"config").switchIfEmpty(Mono.fromSupplier(()->{var s=new McpWriteSettings();var m=new Metadata();m.setName("config");s.setMetadata(m);s.setAuthorName(env("XIAORAN_HALO_OWNER"));s.setPolicyName(env("XIAORAN_PUBLIC_IMAGE_POLICY"));return s;}));}
    public Mono<McpWriteSettings> ready(){return current().flatMap(s->s.getAuthorName()==null||s.getAuthorName().isBlank()||s.getPolicyName()==null||s.getPolicyName().isBlank()?Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"请先在Halo附件设置中选择默认存储，然后重新开启Agent写入权限")):Mono.just(s));}
    public Mono<McpWriteSettings> initialize(String authorHint){return current().flatMap(s->{if(s.getAuthorName()==null||s.getAuthorName().isBlank())s.setAuthorName(authorHint);Mono<String> policy;if(s.getPolicyName()!=null&&!s.getPolicyName().isBlank())policy=Mono.just(s.getPolicyName());else policy=client.fetch(ConfigMap.class,"system").map(cm->{try{String raw=cm.getData().getOrDefault("attachment","{}");return Unstructured.OBJECT_MAPPER.readTree(raw).path("console").path("policyName").asText("");}catch(Exception e){return "";}}).filter(v->!v.isBlank()).switchIfEmpty(client.listAll(Policy.class,new ListOptions(),Sort.unsorted()).filter(p->p.getMetadata().getDeletionTimestamp()==null).take(2).collectList().flatMap(rows->rows.size()==1?Mono.just(rows.get(0).getMetadata().getName()):Mono.empty()));return policy.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"请先在Halo附件设置中选择默认图片存储"))).flatMap(value->save(new Input(s.getAuthorName(),value))).then(ready());});}
    public Mono<Map<String,Object>> view(){return Mono.zip(current(),client.listAll(Policy.class,new ListOptions(),Sort.unsorted()).filter(p->p.getMetadata().getDeletionTimestamp()==null).map(p->Map.of("id",p.getMetadata().getName(),"name",p.getSpec().getDisplayName())).collectList(),client.listAll(User.class,new ListOptions(),Sort.unsorted()).filter(u->u.getMetadata().getDeletionTimestamp()==null).map(u->Map.of("id",u.getMetadata().getName(),"name",java.util.Objects.toString(u.getSpec().getDisplayName(),u.getMetadata().getName()))).collectList()).map(t->Map.of("authorName",t.getT1().getAuthorName(),"policyName",t.getT1().getPolicyName(),"policies",t.getT2(),"authors",t.getT3()));}
    public Mono<Map<String,Object>> save(Input input){if(input==null||input.authorName()==null||input.policyName()==null||!input.authorName().matches("[\\w-]{1,253}")||!input.policyName().matches("[\\w-]{1,253}"))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择有效的作者和存储"));return Mono.zip(client.fetch(User.class,input.authorName()).filter(u->u.getMetadata().getDeletionTimestamp()==null).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"作者不存在"))),client.fetch(Policy.class,input.policyName()).filter(p->p.getMetadata().getDeletionTimestamp()==null).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"存储不存在")))).then(current()).flatMap(s->{s.setAuthorName(input.authorName());s.setPolicyName(input.policyName());return s.getMetadata().getVersion()==null?client.create(s):client.update(s);}).then(view());}
    public record Input(String authorName,String policyName){}
}
