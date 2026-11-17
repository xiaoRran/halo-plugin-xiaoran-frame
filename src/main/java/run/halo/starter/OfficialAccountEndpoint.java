package run.halo.starter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
@Component
public class OfficialAccountEndpoint implements CustomEndpoint {
 private final OfficialAccountConfig config;
 private final OfficialAccountSync sync;
 private final OfficialAccountApi api;
 public OfficialAccountEndpoint(OfficialAccountConfig config,OfficialAccountSync sync,OfficialAccountApi api){this.config=config;this.sync=sync;this.api=api;}
 @Override public GroupVersion groupVersion(){return new GroupVersion("console.api.space.onoe.cn","v1alpha1");}
 private Mono<ServerResponse> response(Mono<?> value){return value.flatMap(v->ServerResponse.ok().header("Cache-Control","no-store").bodyValue(v)).onErrorResume(error->ServerResponse.badRequest().header("Cache-Control","no-store").bodyValue(Map.of("detail",OfficialAccountSync.safeError(error))));}
 @Override public RouterFunction<ServerResponse> endpoint(){return RouterFunctions.route()
  .GET("/officialaccount/config",r->response(config.view()))
  .PUT("/officialaccount/config",r->response(r.bodyToMono(OfficialAccountConfig.Input.class).switchIfEmpty(Mono.error(new IllegalArgumentException("请填写配置"))).flatMap(config::save)))
  .POST("/officialaccount/test",r->response(config.settings().flatMap(s->api.token(s.getAppId())).map(token->Map.of("connected",true,"message","公众号凭据验证成功；草稿和发表权限仍以微信后台为准"))))
  .GET("/officialaccount/jobs",r->response(sync.jobs()))
  .GET("/officialaccount/articles",r->response(sync.articles()))
  .POST("/officialaccount/sync",r->response(r.bodyToMono(SyncInput.class).switchIfEmpty(Mono.error(new IllegalArgumentException("请选择文章"))).flatMap(input->sync.enqueue(input.postName(),false)).map(OfficialAccountSync::view)))
  .POST("/officialaccount/jobs/{id}/retry",r->response(r.bodyToMono(Action.class).defaultIfEmpty(new Action(false,false)).flatMap(input->sync.retry(r.pathVariable("id"),input.confirmNoDuplicate())).map(OfficialAccountSync::view)))
  .POST("/officialaccount/jobs/{id}/publish",r->response(r.bodyToMono(Action.class).defaultIfEmpty(new Action(false,false)).flatMap(input->sync.publish(r.pathVariable("id"),input.confirmPublic())).map(OfficialAccountSync::view))).build();}
 public record SyncInput(String postName){}
 public record Action(boolean confirmPublic,boolean confirmNoDuplicate){}
}
