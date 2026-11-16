package run.halo.starter;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.HttpSecurityUtils;
@Component
public class McpImageService {
    public static final String AGENT="space.onoe.cn/mcp-agent";
    private final McpConfigService config;
    private final McpWriteConfig writeConfig;
    private final AttachmentService attachments;
    private final ReactiveExtensionClient client;
    private final SubmissionGuard submissions;
    private final java.util.concurrent.atomic.AtomicInteger transfers=new java.util.concurrent.atomic.AtomicInteger();
    private <T> Mono<T> transfer(java.util.function.Supplier<Mono<T>> action){return Mono.defer(()->{if(transfers.incrementAndGet()>3){transfers.decrementAndGet();return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"图片上传正忙，请稍后重试"));}return Mono.defer(action).doFinally(signal->transfers.decrementAndGet());});}
    private static void requestId(String id){if(id==null||!id.matches("[A-Za-z0-9-]{16,80}"))throw new IllegalArgumentException("请提供16到80位request_id，建议使用UUID");}
    private final WebClient web=WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpSecurityUtils.secureHttpClient().followRedirect(false).responseTimeout(Duration.ofSeconds(12))))
        .codecs(c->c.defaultCodecs().maxInMemorySize(ImageFormat.MAX_BYTES)).filter(HttpSecurityUtils.maxResponseSizeFilter(ImageFormat.MAX_BYTES)).build();
    public McpImageService(McpConfigService config,McpWriteConfig writeConfig,AttachmentService attachments,ReactiveExtensionClient client,SubmissionGuard submissions){this.config=config;this.writeConfig=writeConfig;this.attachments=attachments;this.client=client;this.submissions=submissions;}
    static URI source(String value){try{URI u=URI.create(value);String host=u.getHost();if(value.length()>4096||!"https".equalsIgnoreCase(u.getScheme())||host==null||!host.contains(".")||host.matches("[0-9.]+")||host.contains(":")||u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()!=-1&&u.getPort()!=443)throw new IllegalArgumentException();return u;}catch(Exception e){throw new IllegalArgumentException("图片来源须为公网HTTPS地址");}}
    public Mono<Map<String,Object>> fromUrl(String agentId,String url,String requestId){URI uri=source(url);requestId(requestId);return transfer(()->config.requireWriter(agentId).then(web.get().uri(uri).retrieve().bodyToMono(byte[].class).timeout(Duration.ofSeconds(15)).onErrorMap(e->new IllegalArgumentException("图片无法读取、地址发生跳转或文件超过10MB"))).flatMap(bytes->uploadBytes(agentId,bytes,requestId)));}
    public Mono<Map<String,Object>> multipart(String agentId,org.springframework.http.codec.multipart.FilePart file,String requestId){requestId(requestId);return transfer(()->config.requireWriter(agentId).then(org.springframework.core.io.buffer.DataBufferUtils.join(file.content(),ImageFormat.MAX_BYTES)).flatMap(buffer->{byte[] bytes=new byte[buffer.readableByteCount()];try{buffer.read(bytes);}finally{org.springframework.core.io.buffer.DataBufferUtils.release(buffer);}return uploadBytes(agentId,bytes,requestId);}));}
    public Mono<Map<String,Object>> upload(String agentId,byte[] bytes,String requestId){return transfer(()->uploadBytes(agentId,bytes,requestId));}
    private Mono<Map<String,Object>> uploadBytes(String agentId,byte[] bytes,String requestId){
        String type=ImageFormat.detect(bytes);if(requestId==null||!requestId.matches("[A-Za-z0-9-]{16,80}"))return Mono.error(new IllegalArgumentException("请提供16到80位request_id，建议使用UUID"));
        String fingerprint;try{fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}
        return config.requireWriter(agentId).then(writeConfig.ready()).flatMap(settings->submissions.run("mcpimg-"+McpConfigService.hash(agentId+"\n"+requestId),fingerprint,()->{
            String extension=switch(type){case "image/jpeg"->"jpg";case "image/png"->"png";case "image/gif"->"gif";default->"webp";};
            String filename="frame-agent-"+UUID.randomUUID()+"."+extension;
            org.springframework.http.codec.multipart.FilePart part=new org.springframework.http.codec.multipart.FilePart(){
                public String filename(){return filename;}
                public String name(){return "file";}
                public org.springframework.http.HttpHeaders headers(){var h=new org.springframework.http.HttpHeaders();h.setContentType(MediaType.parseMediaType(type));h.setContentDispositionFormData("file",filename);return h;}
                public Flux<org.springframework.core.io.buffer.DataBuffer> content(){return Flux.defer(()->Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));}
                public Mono<Void> transferTo(java.nio.file.Path target){return org.springframework.core.io.buffer.DataBufferUtils.write(content(),target);}
            };
            return config.requireWriter(agentId).then(attachments.upload(settings.getAuthorName(),settings.getPolicyName(),null,part,a->{
                if(a.getMetadata()==null)a.setMetadata(new run.halo.app.extension.Metadata());
                var annotations=new HashMap<String,String>(a.getMetadata().getAnnotations()==null?Map.of():a.getMetadata().getAnnotations());annotations.put(AGENT,agentId);a.getMetadata().setAnnotations(annotations);
            }))
                .flatMap(a->attachments.getPermalink(a).map(url->Map.<String,Object>of("attachmentId",a.getMetadata().getName(),"url",url.toString(),"public",true,"storage","本博客Halo附件存储")))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(settings.getAuthorName(),null,List.of())));
        }));
    }
    public Mono<List<Map<String,String>>> images(String agentId,List<String> ids){return Flux.fromIterable(ids).concatMap(id->client.fetch(Attachment.class,id).filter(a->a.getMetadata().getDeletionTimestamp()==null&&a.getMetadata().getAnnotations()!=null&&agentId.equals(a.getMetadata().getAnnotations().get(AGENT))).switchIfEmpty(Mono.error(new IllegalArgumentException("图片不存在或不属于当前Agent"))).flatMap(a->attachments.getPermalink(a).map(url->Map.of("type","PHOTO","url",url.toString(),"originType","attachment")))).collectList();}
}
