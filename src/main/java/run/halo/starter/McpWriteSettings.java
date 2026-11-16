package run.halo.starter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data @EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="McpWriteSettings",plural="mcpwritesettings",singular="mcpwritesettings")
public class McpWriteSettings extends AbstractExtension {
    private String authorName="";
    private String policyName="";
}
