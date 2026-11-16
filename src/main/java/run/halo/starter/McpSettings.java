package run.halo.starter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data @EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="McpSettings",plural="mcpsettings",singular="mcpsettings")
public class McpSettings extends AbstractExtension {
    private boolean enabled;
    private String allowedOrigin="";
}
