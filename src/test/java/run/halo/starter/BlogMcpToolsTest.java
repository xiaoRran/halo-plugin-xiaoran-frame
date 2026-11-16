package run.halo.starter;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.content.*;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class BlogMcpToolsTest {
 private Post post(){var p=new Post();var m=new Metadata();m.setName("article");p.setMetadata(m);var s=new Post.PostSpec();s.setTitle("公开文章");s.setPublish(true);s.setVisible(Post.VisibleEnum.PUBLIC);s.setPublishTime(Instant.now().minusSeconds(60));s.setReleaseSnapshot("released");p.setSpec(s);p.setStatus(new Post.PostStatus());return p;}
 @Test void privateDraftScheduledDeletedAndHiddenPostsAreExcluded(){var p=post();assertTrue(BlogMcpTools.visible(p));p.getSpec().setVisible(Post.VisibleEnum.PRIVATE);assertFalse(BlogMcpTools.visible(p));p=post();p.getSpec().setPublish(false);assertFalse(BlogMcpTools.visible(p));p=post();p.getSpec().setPublishTime(Instant.now().plusSeconds(60));assertFalse(BlogMcpTools.visible(p));p=post();p.getSpec().setDeleted(true);assertFalse(BlogMcpTools.visible(p));p=post();p.getStatus().setHideFromList(true);assertFalse(BlogMcpTools.visible(p));p=post();p.getSpec().setReleaseSnapshot("");assertFalse(BlogMcpTools.visible(p));}
 @Test void readOnlyReleasedContentAndNeverHeadDraft(){var client=mock(ReactiveExtensionClient.class);var contents=mock(PostContentService.class);var profile=mock(FrameConnectionService.class);when(client.fetch(Post.class,"article")).thenReturn(Mono.just(post()));when(contents.getReleaseContent("article")).thenReturn(Mono.just(ContentWrapper.builder().content("<p>公开内容</p><script>secret()</script>").build()));var tools=new BlogMcpTools(client,contents,profile);var data=tools.call("read_article",Map.of("id","article")).block();assertEquals("公开内容",data.get("text"));verify(contents,never()).getHeadContent(any());assertThrows(Exception.class,()->tools.call("read_article",Map.of("id","../private")).block());assertThrows(Exception.class,()->tools.call("delete_moment",Map.of()).block());}
 @Test void privateContentCannotBeReadEvenWithKnownName(){var client=mock(ReactiveExtensionClient.class);var contents=mock(PostContentService.class);var p=post();p.getSpec().setVisible(Post.VisibleEnum.PRIVATE);when(client.fetch(Post.class,"article")).thenReturn(Mono.just(p));var tools=new BlogMcpTools(client,contents,mock(FrameConnectionService.class));assertThrows(Exception.class,()->tools.call("read_article",Map.of("id","article")).block());verifyNoInteractions(contents);}
 @Test void nativeToolCatalogContainsOnlyReadActions(){var tools=new BlogMcpTools(mock(ReactiveExtensionClient.class),mock(PostContentService.class),mock(FrameConnectionService.class));assertEquals(java.util.List.of("site_info","search_articles","read_article"),tools.list().stream().map(t->t.get("name")).toList());}
}
