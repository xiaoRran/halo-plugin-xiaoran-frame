package run.halo.starter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data
@EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="FrameProfile",plural="frameprofiles",singular="frameprofile")
public class FrameProfile extends AbstractExtension {
    private boolean enabled;
    private String siteName;
    private String author;
    private String description;
    private String tagline;
    private String avatar;
}
