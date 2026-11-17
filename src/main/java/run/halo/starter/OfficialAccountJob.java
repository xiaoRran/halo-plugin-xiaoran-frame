package run.halo.starter;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
@Data @EqualsAndHashCode(callSuper=true)
@GVK(group="space.onoe.cn",version="v1alpha1",kind="OfficialAccountJob",plural="officialaccountjobs",singular="officialaccountjob")
public class OfficialAccountJob extends AbstractExtension {
 private String appId;
 private String postName;
 private String snapshot;
 private String title;
 private String cover;
 private String sourceUrl;
 private String author;
 private boolean publish;
 private boolean manualPublish;
 private String state="QUEUED";
 private String mediaId="";
 private String publishId="";
 private String articleUrl="";
 private String error="";
 private int attempts;
 private int polls;
 private Instant touched=Instant.now();
}
