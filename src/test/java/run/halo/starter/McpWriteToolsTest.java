package run.halo.starter;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class McpWriteToolsTest {
 @Test void uploadLinksResolveAgainstCurrentMcpOriginInsteadOfInternalHaloAddress() throws Exception {var cfg=mock(McpConfigService.class);when(cfg.requireWriter("agent")).thenReturn(Mono.empty());var wc=mock(McpWriteConfig.class);when(wc.ready()).thenReturn(Mono.just(new McpWriteSettings()));var external=mock(ExternalUrlSupplier.class);when(external.get()).thenReturn(URI.create("/"));when(external.getRaw()).thenReturn(URI.create("http://localhost:8090").toURL());var tools=new McpWriteTools(cfg,wc,mock(McpImageService.class),mock(McpMomentService.class),external);assertEquals("/apis/api.space.onoe.cn/v1alpha1/mcpimages",tools.call("agent","upload_image",Map.of()).block().get("uploadUrl"));when(external.getRaw()).thenReturn(null);assertEquals("/apis/api.space.onoe.cn/v1alpha1/mcpimages",tools.call("agent","upload_image",Map.of()).block().get("uploadUrl"));}
}
