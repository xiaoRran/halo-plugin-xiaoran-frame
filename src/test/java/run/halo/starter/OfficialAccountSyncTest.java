package run.halo.starter;
import java.time.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import run.halo.app.content.*;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.*;
import run.halo.app.infra.ExternalUrlSupplier;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OfficialAccountSyncTest {
 static OfficialAccountJob copy(OfficialAccountJob source){if(source==null)return null;var target=new OfficialAccountJob();BeanUtils.copyProperties(source,target);var m=new Metadata();BeanUtils.copyProperties(source.getMetadata(),m);target.setMetadata(m);return target;}
 static class Fixture {
  final ReactiveExtensionClient client=mock(ReactiveExtensionClient.class);
  final OfficialAccountConfig config=mock(OfficialAccountConfig.class);
  final OfficialAccountApi api=mock(OfficialAccountApi.class);
  final OfficialAccountContent content=mock(OfficialAccountContent.class);
  final PostContentService posts=mock(PostContentService.class);
  final OfficialAccountSettings settings=OfficialAccountConfig.defaults();
  final AtomicReference<OfficialAccountJob> stored=new AtomicReference<>();
  final AtomicInteger pollState=new AtomicInteger(1);
  final Post post=new Post();final OfficialAccountSync sync;
  Fixture(){
   settings.setEnabled(true);settings.setAutoSync(true);settings.setAppId("wx1234567890abcdef");settings.setAuthor("小染");
   post.setMetadata(OfficialAccountConfig.metadata("article"));var spec=new Post.PostSpec();spec.setTitle("公开文章");spec.setPublish(true);spec.setVisible(Post.VisibleEnum.PUBLIC);spec.setPublishTime(Instant.now().minusSeconds(30));spec.setReleaseSnapshot("released");spec.setCover("https://blog.example/cover.jpg");post.setSpec(spec);var status=new Post.PostStatus();status.setPermalink("https://blog.example/archives/article");post.setStatus(status);
   when(config.settings()).thenAnswer(i->Mono.just(settings));when(config.credentials(anyString())).thenReturn(Mono.just(new OfficialAccountConfig.Credentials(settings.getAppId(),"private")));
   when(client.fetch(eq(Post.class),anyString())).thenAnswer(i->Mono.just(post));
   when(client.fetch(eq(OfficialAccountJob.class),anyString())).thenAnswer(i->Mono.justOrEmpty(copy(stored.get())));
   when(client.get(eq(OfficialAccountJob.class),anyString())).thenAnswer(i->Mono.justOrEmpty(copy(stored.get())));
   when(client.create(any(OfficialAccountJob.class))).thenAnswer(i->{if(stored.get()!=null)return Mono.error(new IllegalStateException("exists"));var j=copy(i.getArgument(0));j.getMetadata().setVersion(1L);stored.set(j);return Mono.just(copy(j));});
   when(client.update(any(OfficialAccountJob.class))).thenAnswer(i->{OfficialAccountJob incoming=i.getArgument(0);var previous=stored.get();if(!Objects.equals(incoming.getMetadata().getVersion(),previous.getMetadata().getVersion()))return Mono.error(new OptimisticLockingFailureException("stale"));var j=copy(incoming);j.getMetadata().setVersion(previous.getMetadata().getVersion()+1);stored.set(j);return Mono.just(copy(j));});
   when(posts.getSpecifiedContent("article","released")).thenReturn(Mono.just(ContentWrapper.builder().content("<p>已发布正文</p>").build()));
   when(content.prepare(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(Mono.just(new OfficialAccountContent.Prepared("<p>已发布正文</p>","cover-media")));
   var mapper=new ObjectMapper();when(api.json(anyString(),anyString(),any())).thenAnswer(i->{String path=i.getArgument(1);String json=switch(path){case "/cgi-bin/draft/add"->"{\"media_id\":\"draft-media\"}";case "/cgi-bin/freepublish/submit"->"{\"publish_id\":\"publish-id\"}";case "/cgi-bin/freepublish/get"->"{\"publish_status\":"+pollState.get()+",\"article_detail\":{\"item\":[{\"article_url\":\"https://mp.weixin.qq.com/s/published\"}]}}";default->throw new IllegalStateException("unexpected API");};return Mono.just(mapper.readTree(json));});
   var external=mock(ExternalUrlSupplier.class);when(external.get()).thenReturn(URI.create("https://blog.example/"));sync=new OfficialAccountSync(client,config,api,content,posts,external);
  }
  OfficialAccountJob queued(){return sync.enqueue("article",false).block();}
 }
 @Test void slowImageProcessingDoesNotOverflowWorkerTimer(){var result=OfficialAccountSync.pulses(Duration.ZERO,Duration.ofMillis(5)).concatMap(t->Mono.delay(Duration.ofMillis(60))).take(2).collectList().block(Duration.ofSeconds(2));assertEquals(2,result.size());}
 @Test void relativePermalinkUsesConfiguredExternalUrlEvenWhenHaloReturnsRelativeBase() throws Exception {var external=mock(ExternalUrlSupplier.class);when(external.get()).thenReturn(URI.create("/"));when(external.getRaw()).thenReturn(new java.net.URL("https://blog.example"));assertEquals("https://blog.example/archives/article",OfficialAccountSync.sourceUrl("/archives/article",external));}
 @Test void absolutePermalinkWorksWithoutConfiguredExternalUrl(){var external=mock(ExternalUrlSupplier.class);when(external.get()).thenReturn(URI.create("/"));assertEquals("https://blog.example/archives/article",OfficialAccountSync.sourceUrl("https://blog.example/archives/article",external));assertTrue(assertThrows(IllegalArgumentException.class,()->OfficialAccountSync.sourceUrl("/archives/article",external)).getMessage().contains("HALO_EXTERNAL_URL"));assertThrows(IllegalArgumentException.class,()->OfficialAccountSync.sourceUrl("http://blog.example/article",external));assertThrows(IllegalArgumentException.class,()->OfficialAccountSync.sourceUrl("https://127.0.0.1/article",external));}
 @Test void samePublishedSnapshotIsDeduplicatedAndOnlyReleasedContentIsRead(){var f=new Fixture();var job=f.queued();assertEquals(job.getMetadata().getName(),f.queued().getMetadata().getName());verify(f.client,times(1)).create(any(OfficialAccountJob.class));var done=f.sync.process(job).block();assertEquals("DRAFT_READY",done.getState());assertEquals("draft-media",done.getMediaId());assertTrue(done.getPublishId().isBlank());verify(f.posts).getSpecifiedContent("article","released");verify(f.posts,never()).getHeadContent(any());verify(f.api,never()).json(anyString(),eq("/cgi-bin/freepublish/submit"),any());}
 @Test void submitIsOnlyProcessingUntilWechatReportsSuccess(){var f=new Fixture();f.settings.setAutoPublish(true);var processing=f.sync.process(f.queued()).block();assertEquals("PUBLISHING",processing.getState());assertEquals("publish-id",processing.getPublishId());assertEquals("PUBLISHING",f.sync.process(processing).block().getState());f.pollState.set(0);var done=f.sync.process(copy(f.stored.get())).block();assertEquals("PUBLISHED",done.getState());assertEquals("https://mp.weixin.qq.com/s/published",done.getArticleUrl());}
 @Test void privateAndSupersededPostsCannotBeSynced(){var f=new Fixture();f.post.getSpec().setVisible(Post.VisibleEnum.PRIVATE);assertThrows(IllegalArgumentException.class,f::queued);verify(f.client,never()).create(any(OfficialAccountJob.class));f.post.getSpec().setVisible(Post.VisibleEnum.PUBLIC);var job=f.queued();f.post.getSpec().setReleaseSnapshot("new-release");assertEquals("SUPERSEDED",f.sync.process(job).block().getState());verifyNoInteractions(f.content);}
 @Test void unpublishingDuringImagePreparationPreventsDraftSubmission(){var f=new Fixture();when(f.content.prepare(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{f.post.getSpec().setVisible(Post.VisibleEnum.PRIVATE);return Mono.just(new OfficialAccountContent.Prepared("<p>内容</p>","thumb"));});assertEquals("FAILED",f.sync.process(f.queued()).block().getState());verify(f.api,never()).json(anyString(),anyString(),any());}
 @Test void uncertainRequestIsNotAutomaticallyReplayedAndNeedsExplicitReconciliation(){var f=new Fixture();when(f.api.json(anyString(),eq("/cgi-bin/draft/add"),any())).thenReturn(Mono.error(new OfficialAccountApi.Failure("结果待核对",true)));var done=f.sync.process(f.queued()).block();assertEquals("UNCERTAIN",done.getState());assertNull(f.sync.process(done).block());assertThrows(IllegalArgumentException.class,()->f.sync.retry(done.getMetadata().getName(),false).block());assertEquals("QUEUED",f.sync.retry(done.getMetadata().getName(),true).block().getState());verify(f.api,times(1)).json(anyString(),eq("/cgi-bin/draft/add"),any());}
 @Test void abandonedSubmissionAfterRestartIsUncertainRatherThanDuplicated(){var f=new Fixture();var job=f.queued();job.setState("DRAFT_SUBMITTING");job.setTouched(Instant.now().minusSeconds(700));f.stored.set(copy(job));assertEquals("UNCERTAIN",f.sync.process(job).block().getState());verify(f.api,never()).json(anyString(),anyString(),any());}
 @Test void disablingPausesQueuedJobAndManualPublishRequiresConfirmation(){var f=new Fixture();var job=f.queued();f.settings.setEnabled(false);assertEquals("PAUSED",f.sync.process(job).block().getState());verify(f.api,never()).json(anyString(),anyString(),any());assertThrows(IllegalArgumentException.class,()->f.sync.publish(job.getMetadata().getName(),false).block());}
 @Test void bodyTitleTooLongIsExplicitAndAccountChangesProduceDifferentDedupeKeys(){var job=new OfficialAccountJob();job.setTitle("长".repeat(33));assertThrows(IllegalArgumentException.class,()->OfficialAccountSync.article(job,new OfficialAccountContent.Prepared("正文","thumb")));assertNotEquals(OfficialAccountSync.jobName("one","post","release"),OfficialAccountSync.jobName("two","post","release"));}
 @Test void haloPublishEventsEnqueueOnlyWhenAutomaticSyncIsEnabled(){var f=new Fixture();f.sync.published(new run.halo.app.event.post.PostPublishedEvent(this,"article"));assertNotNull(f.stored.get());f.settings.setAutoSync(false);f.post.getSpec().setReleaseSnapshot("another");f.sync.updated(new run.halo.app.event.post.PostUpdatedEvent(this,"article"));verify(f.client,times(1)).create(any(OfficialAccountJob.class));f.sync.stop();}
}
