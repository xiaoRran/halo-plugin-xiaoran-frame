package run.halo.starter;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data
@EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="DailyState",plural="dailystates",singular="dailystate")
public class DailyState extends AbstractExtension {
    private List<Item> items;
    public record Item(String id,String text,Boolean done,String date,String url) {}
}
