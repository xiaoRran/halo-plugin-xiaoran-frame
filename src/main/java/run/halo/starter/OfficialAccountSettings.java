package run.halo.starter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data @EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="OfficialAccountSettings",plural="officialaccountsettings",singular="officialaccountsetting")
public class OfficialAccountSettings extends AbstractExtension {
 private boolean enabled;
 private boolean autoSync;
 private boolean autoPublish;
 private String appId="";
 private String author="";
 private String defaultCoverUrl="";
 private String defaultThumbMediaId="";
}
