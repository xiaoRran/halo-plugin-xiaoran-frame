package run.halo.starter;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.GroupVersion;

@Component
public class ImageUploadEndpoint implements CustomEndpoint {
    private final AttachmentService attachments;
    private final OwnerSessions sessions;
    public ImageUploadEndpoint(AttachmentService attachments, OwnerSessions sessions) {
        this.attachments=attachments;this.sessions=sessions;
    }
    @Override public GroupVersion groupVersion(){return new GroupVersion("api.space.onoe.cn","v1alpha1");}
    @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route().POST("/images",this::upload).build();}
    private Mono<ServerResponse> upload(ServerRequest request){
        if(!sessions.valid(request.headers().firstHeader("X-Xiaoran-Session")))return ServerResponse.status(401).build();
        String owner=System.getenv("XIAORAN_HALO_OWNER");
        String policy=System.getenv("XIAORAN_PUBLIC_IMAGE_POLICY");
        if(owner==null || policy==null)return ServerResponse.status(503).build();
        return request.multipartData().flatMap(parts->{
            if(!(parts.getFirst("file") instanceof FilePart file))return ServerResponse.badRequest().build();
            return DataBufferUtils.join(file.content(),ImageFormat.MAX_BYTES).flatMap(buffer->{
                byte[] bytes=new byte[buffer.readableByteCount()];
                try{buffer.read(bytes);}finally{DataBufferUtils.release(buffer);}
                String type;
                try{type=ImageFormat.detect(bytes);}catch(IllegalArgumentException error){return ServerResponse.badRequest().bodyValue(Map.of("detail",error.getMessage()));}
                String extension=switch(type){case "image/jpeg"->"jpg";case "image/png"->"png";case "image/gif"->"gif";default->"webp";};
                return attachments.upload(policy,null,"xiaoran-"+UUID.randomUUID()+"."+extension,
                    Flux.defer(()->Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))),MediaType.parseMediaType(type))
                    .flatMap(attachment->attachments.getPermalink(attachment).flatMap(url->ServerResponse.status(201)
                        .bodyValue(Map.of("name",attachment.getMetadata().getName(),"url",url.toString(),"public",true))))
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new UsernamePasswordAuthenticationToken(owner,null,java.util.List.of())));
            });
        }).onErrorResume(org.springframework.core.io.buffer.DataBufferLimitException.class,
            error -> ServerResponse.status(413).bodyValue(Map.of("detail","图片不能超过10MB")));
    }
}
