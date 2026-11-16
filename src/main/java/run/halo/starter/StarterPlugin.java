package run.halo.starter;

import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class StarterPlugin extends BasePlugin {
    private final SchemeManager schemes;

    public StarterPlugin(PluginContext context, SchemeManager schemes) {
        super(context);
        this.schemes = schemes;
    }

    @Override
    public void start() {
        schemes.register(LifeRecord.class);
        schemes.register(OwnerBinding.class);
        schemes.register(DailyState.class);
        schemes.register(PersonalState.class);
        schemes.register(FrameProfile.class);
        schemes.register(McpSettings.class);
        schemes.register(McpWriteSettings.class);
        schemes.register(McpMomentDraft.class);
    }

    @Override
    public void stop() {
        schemes.unregister(Scheme.buildFromType(LifeRecord.class));
        schemes.unregister(Scheme.buildFromType(OwnerBinding.class));
        schemes.unregister(Scheme.buildFromType(DailyState.class));
        schemes.unregister(Scheme.buildFromType(PersonalState.class));
        schemes.unregister(Scheme.buildFromType(FrameProfile.class));
        schemes.unregister(Scheme.buildFromType(McpSettings.class));
        schemes.unregister(Scheme.buildFromType(McpWriteSettings.class));
        schemes.unregister(Scheme.buildFromType(McpMomentDraft.class));
    }
}
