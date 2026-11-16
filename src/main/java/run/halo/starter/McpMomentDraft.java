package run.halo.starter;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data @EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="McpMomentDraft",plural="mcpmomentdrafts",singular="mcpmomentdraft")
public class McpMomentDraft extends AbstractExtension {
    private String agentId;
    private String authorName;
    private String content;
    private List<String> images;
    private String fingerprint;
    private String state="PENDING";
    private String momentId;
}
