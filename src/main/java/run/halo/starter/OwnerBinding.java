package run.halo.starter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group="space.onoe.cn", version="v1alpha1", kind="OwnerBinding", plural="ownerbindings", singular="ownerbinding")
public class OwnerBinding extends AbstractExtension {
    private String openid;
}
