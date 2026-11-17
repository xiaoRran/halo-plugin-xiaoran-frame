package run.halo.starter;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import reactor.core.Disposable;
import reactor.core.publisher.*;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.event.post.PostPublishedEvent;
import run.halo.app.event.post.PostUpdatedEvent;
import run.halo.app.extension.*;
import run.halo.app.infra.ExternalUrlSupplier;
@Component
public class OfficialAccountSync {
 private final ReactiveExtensionClient client;
 private final OfficialAccountConfig config;
 private final OfficialAccountApi api;
 private final OfficialAccountContent content;
 private final PostContentService posts;
 private final ExternalUrlSupplier external;
 private Disposable worker;
 private reactor.core.Disposable.Composite events=reactor.core.Disposables.composite();
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(OfficialAccountSync.class);
 public OfficialAccountSync(ReactiveExtensionClient client,OfficialAccountConfig config,OfficialAccountApi api,OfficialAccountContent content,PostContentService posts,ExternalUrlSupplier external){this.client=client;this.config=config;this.api=api;this.content=content;this.posts=posts;this.external=external;}
 static Flux<Long> pulses(Duration initial,Duration period){return Flux.interval(initial,period).onBackpressureDrop();}
 public synchronized void start(){if(events.isDisposed())events=reactor.core.Disposables.composite();if(worker==null||worker.isDisposed())worker=pulses(Duration.ofSeconds(5),Duration.ofSeconds(10)).concatMap(t->tick().onErrorResume(e->{log.warn("公众号同步任务暂时无法处理，请在插件中检查同步状态");return Mono.empty();})).subscribe();}
 public synchronized void stop(){if(worker!=null)worker.dispose();events.dispose();}
 @EventListener public void published(PostPublishedEvent event){automatic(event.getName());}
 @EventListener public void updated(PostUpdatedEvent event){automatic(event.getName());}
 private void automatic(String name){var subscriptions=events;var task=new BaseSubscriber<OfficialAccountJob>(){
  @Override protected void hookOnError(Throwable error){log.warn("文章公众号同步未能入队：{}，请在插件中手动重试",name);}
  @Override protected void hookFinally(SignalType signal){subscriptions.remove(this);}
 };if(subscriptions.add(task))config.settings().filter(s->s.isEnabled()&&s.isAutoSync()).flatMap(s->enqueue(name,true)).subscribe(task);}

 static String jobName(String appId,String post,String snapshot){return "wx-"+McpConfigService.hash(appId+"\n"+post+"\n"+snapshot).substring(0,48);}
 static String annotation(Post p,String name,String fallback){var map=p.getMetadata().getAnnotations();return map==null?fallback:Objects.toString(map.getOrDefault(name,fallback),fallback);}
 static boolean syncable(Post p){return BlogMcpTools.visible(p)&&!"false".equalsIgnoreCase(annotation(p,"space.onoe.cn/wechat-sync","true"));}
 static String sourceUrl(String link,ExternalUrlSupplier external){
  String base=external.getRaw()!=null?external.getRaw().toString():external.get().toString();
  try{if(!java.net.URI.create(link).isAbsolute()&&!java.net.URI.create(base).isAbsolute())throw new IllegalArgumentException("请先在Halo配置本站公网HTTPS访问地址（HALO_EXTERNAL_URL），用于补全文章原文链接");}catch(NullPointerException e){throw new IllegalArgumentException("文章缺少原文链接或站点地址");}
  return OfficialAccountContent.absolute(link,base);
 }
 private Mono<Post> post(String name){if(name==null||!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,252}"))return Mono.error(new IllegalArgumentException("文章编号不正确"));return client.fetch(Post.class,name).filter(OfficialAccountSync::syncable).switchIfEmpty(Mono.error(new IllegalArgumentException("仅支持同步已发布的公开文章，或该文章已关闭公众号同步")));}
 public Mono<OfficialAccountJob> enqueue(String name,boolean automatic){return config.settings().flatMap(s->{if(!s.isEnabled()||automatic&&!s.isAutoSync())return Mono.error(new IllegalArgumentException("请先在插件中启用公众号同步"));return config.credentials(s.getAppId()).then(post(name)).flatMap(p->{String id=jobName(s.getAppId(),name,p.getSpec().getReleaseSnapshot());return client.fetch(OfficialAccountJob.class,id).switchIfEmpty(Mono.defer(()->{
  var job=new OfficialAccountJob();job.setMetadata(OfficialAccountConfig.metadata(id));job.setAppId(s.getAppId());job.setPostName(name);job.setSnapshot(p.getSpec().getReleaseSnapshot());job.setTitle(annotation(p,"space.onoe.cn/wechat-title",p.getSpec().getTitle()));job.setCover(Objects.toString(p.getSpec().getCover(),""));job.setAuthor(s.getAuthor());job.setPublish(s.isAutoPublish());String link=Objects.toString(p.getStatus().getPermalink(),"");job.setSourceUrl(sourceUrl(link,external));return client.create(job).onErrorResume(error->client.fetch(OfficialAccountJob.class,id).switchIfEmpty(Mono.error(error)));
 }));});});}
 private Flux<OfficialAccountJob> all(){return client.listAll(OfficialAccountJob.class,new ListOptions(),Sort.by(Sort.Direction.DESC,"metadata.creationTimestamp"));}
 public Mono<List<Map<String,Object>>> jobs(){return all().take(100).map(OfficialAccountSync::view).collectList();}
 public Mono<List<Map<String,String>>> articles(){return client.listAll(Post.class,new ListOptions(),Sort.by(Sort.Direction.DESC,"spec.publishTime")).filter(OfficialAccountSync::syncable).take(100).map(p->Map.of("id",p.getMetadata().getName(),"title",p.getSpec().getTitle())).collectList();}
 static Map<String,Object> view(OfficialAccountJob j){var m=new LinkedHashMap<String,Object>();m.put("id",j.getMetadata().getName());m.put("appId",j.getAppId());m.put("postName",j.getPostName());m.put("title",j.getTitle());m.put("state",j.getState());m.put("mediaId",Objects.toString(j.getMediaId(),""));m.put("publishId",Objects.toString(j.getPublishId(),""));m.put("articleUrl",Objects.toString(j.getArticleUrl(),""));m.put("error",Objects.toString(j.getError(),""));m.put("attempts",j.getAttempts());m.put("touched",Objects.toString(j.getTouched(),""));return m;}
 Mono<Void> tick(){return all().filter(j->Set.of("QUEUED","PUBLISHING","PREPARING","DRAFT_SUBMITTING","PUBLISH_SUBMITTING").contains(j.getState())).take(10).concatMap(this::process).then();}
 private Mono<OfficialAccountJob> save(OfficialAccountJob job,String state){return Mono.defer(()->{job.setState(state);job.setTouched(Instant.now());return client.update(job).map(saved->{job.setMetadata(saved.getMetadata());return job;});});}
 Mono<OfficialAccountJob> process(OfficialAccountJob job){
  if(job.getState().equals("PUBLISHING"))return poll(job);
  if(!job.getState().equals("QUEUED")){
   if(job.getTouched()!=null&&job.getTouched().isAfter(Instant.now().minusSeconds(600)))return Mono.empty();
   job.setError("上一次任务未完成，请先核对公众号后台再重试");return save(job,job.getState().equals("PREPARING")?"QUEUED":"UNCERTAIN");
  }
  job.setAttempts(job.getAttempts()+1);job.setError("");
  return save(job,"PREPARING").onErrorResume(e->Mono.empty()).flatMap(claimed->config.settings().flatMap(s->{
   if(!s.isEnabled()||!Objects.equals(s.getAppId(),claimed.getAppId())){claimed.setError("同步已停用或公众号已切换，任务已暂停");return save(claimed,"PAUSED");}
   return post(claimed.getPostName()).flatMap(p->{if(!Objects.equals(claimed.getSnapshot(),p.getSpec().getReleaseSnapshot())){claimed.setError("文章已发布新版本，请同步最新版本");return save(claimed,"SUPERSEDED");}
    Mono<OfficialAccountJob> draft;
    if(claimed.getMediaId()!=null&&!claimed.getMediaId().isBlank())draft=Mono.just(claimed);
    else draft=posts.getSpecifiedContent(claimed.getPostName(),claimed.getSnapshot()).switchIfEmpty(Mono.error(new IllegalArgumentException("已发布正文快照不存在"))).flatMap(body->content.prepare(claimed.getAppId(),body.getContent(),claimed.getSourceUrl(),claimed.getCover(),s.getDefaultCoverUrl(),s.getDefaultThumbMediaId())).flatMap(prepared->{var article=article(claimed,prepared);return requireCurrent(claimed).then(save(claimed,"DRAFT_SUBMITTING")).flatMap(j->api.json(j.getAppId(),"/cgi-bin/draft/add",Map.of("articles",List.of(article)))).flatMap(data->{claimed.setMediaId(OfficialAccountApi.required(data,"media_id"));return save(claimed,"DRAFT_READY");});});
    return draft.flatMap(j->{if(!j.isPublish())return save(j,"DRAFT_READY");return config.settings().flatMap(current->{if(!current.isEnabled()||!Objects.equals(current.getAppId(),j.getAppId())||!j.isManualPublish()&&!current.isAutoPublish()){j.setError("自动发表已关闭，文章保留在草稿箱");return save(j,"DRAFT_READY");}return submit(j);});});
   });
  }).onErrorResume(error->failed(claimed,error)));
 }
 static Map<String,Object> article(OfficialAccountJob job,OfficialAccountContent.Prepared prepared){String title=Objects.toString(job.getTitle(),"").strip();if(title.isBlank()||title.codePointCount(0,title.length())>32)throw new IllegalArgumentException("公众号标题最多32字，可在文章元数据space.onoe.cn/wechat-title中设置短标题");var m=new LinkedHashMap<String,Object>();m.put("article_type","news");m.put("title",title);m.put("author",Objects.toString(job.getAuthor(),""));m.put("content",prepared.html());m.put("thumb_media_id",prepared.thumbMediaId());m.put("content_source_url",job.getSourceUrl());m.put("need_open_comment",0);return m;}
 private Mono<Void> requireCurrent(OfficialAccountJob job){return post(job.getPostName()).flatMap(p->Objects.equals(p.getSpec().getReleaseSnapshot(),job.getSnapshot())?Mono.<Void>empty():Mono.error(new IllegalArgumentException("文章已发布新版本，请同步最新版本")));}
 private Mono<OfficialAccountJob> submit(OfficialAccountJob job){return requireCurrent(job).then(save(job,"PUBLISH_SUBMITTING")).flatMap(j->api.json(j.getAppId(),"/cgi-bin/freepublish/submit",Map.of("media_id",j.getMediaId()))).flatMap(data->{job.setPublishId(OfficialAccountApi.required(data,"publish_id"));job.setPolls(0);return save(job,"PUBLISHING");});}
 private Mono<OfficialAccountJob> failed(OfficialAccountJob job,Throwable error){boolean uncertain=(job.getState().equals("DRAFT_SUBMITTING")||job.getState().equals("PUBLISH_SUBMITTING"))&&(!(error instanceof OfficialAccountApi.Failure failure)||failure.ambiguous());job.setError(safeError(error));return save(job,uncertain?"UNCERTAIN":"FAILED");}
 static String safeError(Throwable error){return error instanceof IllegalArgumentException||error instanceof OfficialAccountApi.Failure?Objects.toString(error.getMessage(),"同步失败"):"任务未能完成，请核对公众号后台后重试";}
 private Mono<OfficialAccountJob> poll(OfficialAccountJob job){if(job.getPublishId()==null||job.getPublishId().isBlank())return Mono.error(new IllegalArgumentException("任务缺少微信发表编号"));return api.json(job.getAppId(),"/cgi-bin/freepublish/get",Map.of("publish_id",job.getPublishId())).flatMap(data->{int state=data.path("publish_status").asInt(-1);if(state==0){String url=data.path("article_detail").path("item").path(0).path("article_url").asText("");if(!url.isBlank()){try{var u=java.net.URI.create(url);if("https".equals(u.getScheme())&&"mp.weixin.qq.com".equals(u.getHost()))job.setArticleUrl(url);}catch(Exception ignored){}}job.setError("");return save(job,"PUBLISHED");}job.setPolls(job.getPolls()+1);if(state==1){if(job.getPolls()>=180){job.setError("微信仍在处理中，稍后点击查询结果；请勿重复发表");return save(job,"PUBLISH_UNKNOWN");}return save(job,"PUBLISHING");}job.setError("微信未发表成功，平台状态码："+state+"；请到公众号后台查看原因");return save(job,"PUBLISH_FAILED");}).onErrorResume(error->{job.setError(safeError(error));job.setPolls(job.getPolls()+1);return save(job,job.getPolls()>=180?"PUBLISH_UNKNOWN":"PUBLISHING");});}
 public Mono<OfficialAccountJob> retry(String id,boolean confirmed){return client.get(OfficialAccountJob.class,id).flatMap(j->{if(Set.of("PREPARING","DRAFT_SUBMITTING","PUBLISH_SUBMITTING","QUEUED").contains(j.getState()))return Mono.error(new IllegalArgumentException("任务正在处理中，请勿重复操作"));if(j.getPublishId()!=null&&!j.getPublishId().isBlank())return poll(j);if(j.getState().equals("PUBLISHED")||j.getState().equals("DRAFT_READY"))return Mono.just(j);if(j.getState().equals("UNCERTAIN")&&!confirmed)return Mono.error(new IllegalArgumentException("上次请求结果不确定，请先确认公众号后台没有重复内容"));j.setError("");return save(j,"QUEUED");});}
 public Mono<OfficialAccountJob> publish(String id,boolean confirmed){if(!confirmed)return Mono.error(new IllegalArgumentException("请确认将草稿公开发表"));return client.get(OfficialAccountJob.class,id).flatMap(j->{if(!j.getState().equals("DRAFT_READY")||j.getMediaId()==null||j.getMediaId().isBlank())return Mono.error(new IllegalArgumentException("仅能发表已同步完成的草稿"));j.setPublish(true);j.setManualPublish(true);return save(j,"QUEUED");});}
}
