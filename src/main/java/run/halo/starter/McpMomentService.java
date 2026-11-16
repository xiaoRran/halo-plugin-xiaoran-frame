package run.halo.starter;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
@Component
public class McpMomentService {
    private static final GroupVersionKind MOMENT=new GroupVersionKind("moment.halo.run","v1alpha1","Moment");
    public static final String DRAFT_ANNOTATION="space.onoe.cn/mcp-draft";
    private final ReactiveExtensionClient client;
    private final McpConfigService config;
    private final McpWriteConfig writeConfig;
    private final McpImageService images;
    private final SubmissionGuard submissions;
    public McpMomentService(ReactiveExtensionClient client,McpConfigService config,McpWriteConfig writeConfig,McpImageService images,SubmissionGuard submissions){this.client=client;this.config=config;this.writeConfig=writeConfig;this.images=images;this.submissions=submissions;}
    public Mono<Map<String,Object>> create(String agentId,String content,List<String> attachments,String requestId){
        if(content==null||content.isBlank()||content.length()>10000||requestId==null||!requestId.matches("[A-Za-z0-9-]{16,80}")||attachments==null||attachments.size()>9||attachments.stream().anyMatch(id->id==null||!id.matches("[\\w-]{1,253}"))||new HashSet<>(attachments).size()!=attachments.size())return Mono.error(new IllegalArgumentException("正文须为1到10000字，图片最多9张且不能重复，request_id建议使用UUID"));
        String text=content.strip(),fingerprint=McpConfigService.hash(text+"\n"+McpEndpoint.serialize(attachments)),name="mcp-draft-"+McpConfigService.hash(agentId+"\n"+requestId).substring(0,40);
        return config.requireWriter(agentId).then(submissions.run("mcpdraft-"+McpConfigService.hash(agentId+"\n"+requestId),fingerprint,()->client.fetch(McpMomentDraft.class,name).flatMap(d->!agentId.equals(d.getAgentId())||!fingerprint.equals(d.getFingerprint())?Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"相同request_id已用于不同内容")):Mono.just(d)).switchIfEmpty(Mono.defer(()->writeConfig.ready().flatMap(settings->images.images(agentId,attachments).then(client.listAll(McpMomentDraft.class,new ListOptions(),Sort.unsorted()).filter(d->agentId.equals(d.getAgentId())&&Set.of("PENDING","PUBLISHING").contains(d.getState())).take(20).count()).flatMap(count->{if(count>=20)return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"有较多未完成的发布，请先重试已有提交"));var d=new McpMomentDraft();var m=new Metadata();m.setName(name);d.setMetadata(m);d.setAgentId(agentId);d.setAuthorName(settings.getAuthorName());d.setContent(text);d.setImages(List.copyOf(attachments));d.setFingerprint(fingerprint);return client.create(d);})))))).flatMap(this::view);
    }
    public Mono<Map<String,Object>> publishConfirmed(String agentId,String content,List<String> attachments,String requestId,Boolean confirmed){if(!Boolean.TRUE.equals(confirmed))return Mono.error(new IllegalArgumentException("请先在Agent对话中展示图文并获得用户确认，再设置confirm_public=true发布"));return create(agentId,content,attachments,requestId).flatMap(result->review(result.get("id").toString(),"publish"));}
    private Map<String,Object> summary(McpMomentDraft d,List<Map<String,String>> media,boolean failed){var result=new LinkedHashMap<String,Object>();result.put("id",d.getMetadata().getName());result.put("agentId",d.getAgentId());result.put("authorName",d.getAuthorName());result.put("content",d.getContent());result.put("images",media);result.put("state",d.getState());result.put("momentId",Objects.toString(d.getMomentId(),""));result.put("requiresApproval",false);result.put("createdAt",Objects.toString(d.getMetadata().getCreationTimestamp(),""));if(failed)result.put("imageError","图片已失效，请重新上传并使用新的request_id");return result;}
    private Mono<Map<String,Object>> view(McpMomentDraft d){return images.images(d.getAgentId(),d.getImages()).map(media->summary(d,media,false)).onErrorResume(e->Mono.just(summary(d,List.of(),true)));}
    public Mono<Map<String,Object>> status(String agentId,String id){return client.fetch(McpMomentDraft.class,id).filter(d->agentId.equals(d.getAgentId())).switchIfEmpty(Mono.error(new IllegalArgumentException("发布回执不存在或不属于当前Agent"))).flatMap(this::view);}
    public Mono<Map<String,Object>> review(String id,String action){
        if(id==null||!id.matches("mcp-draft-[a-f0-9]{40}")||!Set.of("publish","reject").contains(Objects.toString(action,"")))return Mono.error(new IllegalArgumentException("确认参数不正确"));
        return client.fetch(McpMomentDraft.class,id).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"瞬间草稿不存在"))).flatMap(d->{
            if(action.equals("reject")){if(d.getState().equals("REJECTED"))return view(d);if(!d.getState().equals("PENDING"))return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"该瞬间已发布或正在发布，不能拒绝"));d.setState("REJECTED");return client.update(d).flatMap(this::view);}
            if(d.getState().equals("PUBLISHED"))return view(d);if(!Set.of("PENDING","PUBLISHING").contains(d.getState()))return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"草稿已被拒绝"));
            return config.settings().flatMap(s->s.isEnabled()?config.requireWriter(d.getAgentId()):Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MCP服务已停用"))).then(submissions.run("mcpreview-"+McpConfigService.hash(id),d.getFingerprint(),()->publish(d))).flatMap(this::view);
        });
    }
    private Mono<McpMomentDraft> publish(McpMomentDraft draft){
        return config.requireWriter(draft.getAgentId()).then(images.images(draft.getAgentId(),draft.getImages())).flatMap(media->{
            Mono<McpMomentDraft> claim;if(draft.getState().equals("PENDING")){draft.setState("PUBLISHING");claim=client.update(draft);}else claim=Mono.just(draft);
            return claim.flatMap(d->{String momentName="moment-frame-"+McpConfigService.hash(d.getMetadata().getName()).substring(0,40);
                return client.fetch(MOMENT,momentName).flatMap(existing->d.getMetadata().getName().equals(existing.getMetadata().getAnnotations()==null?null:existing.getMetadata().getAnnotations().get(DRAFT_ANNOTATION))&&d.getAuthorName().equals(Unstructured.getNestedValue(existing.getData(),"spec","owner").orElse(null))?Mono.just(existing):Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"目标瞬间编号冲突"))).switchIfEmpty(Mono.defer(()->{
                    String text=d.getContent(),html="<p>"+text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>")+"</p>";
                    var moment=new Unstructured(Unstructured.OBJECT_MAPPER.convertValue(Map.of("apiVersion","moment.halo.run/v1alpha1","kind","Moment","metadata",Map.of("name",momentName,"annotations",Map.of("thyuu_post_excerpt",MomentPublishEndpoint.excerpt(text),DRAFT_ANNOTATION,d.getMetadata().getName())),"spec",Map.of("owner",d.getAuthorName(),"visible","PUBLIC","approved",true,"releaseTime",Instant.now().toString(),"content",Map.of("raw",text,"html",html,"medium",media))),LinkedHashMap.class));
                    return config.requireWriter(d.getAgentId()).then(client.create(moment));
                })).flatMap(moment->{d.setMomentId(moment.getMetadata().getName());d.setState("PUBLISHED");return client.update(d);});
            });
        });
    }
}
