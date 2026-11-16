package run.halo.starter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/** Private records. Never grant anonymous access to this resource. */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "space.onoe.cn", version = "v1alpha1", kind = "LifeRecord",
    plural = "liferecords", singular = "liferecord")
public class LifeRecord extends AbstractExtension {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "LifeRecordSpec")
    public static class Spec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 10000)
        private String content;
        @Schema(maxLength = 100)
        private String mood;
    }
}
