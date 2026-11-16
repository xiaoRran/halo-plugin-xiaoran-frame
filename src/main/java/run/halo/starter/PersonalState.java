package run.halo.starter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data
@EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="PersonalState",plural="personalstates",singular="personalstate")
public class PersonalState extends AbstractExtension { private String value; }
