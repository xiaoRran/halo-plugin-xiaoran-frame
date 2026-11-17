package run.halo.starter;

import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class StarterPlugin extends BasePlugin {
    private final SchemeManager schemes;
    @org.springframework.beans.factory.annotation.Autowired
    private OfficialAccountSync officialAccount;

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
        schemes.register(OfficialAccountSettings.class);
        schemes.register(OfficialAccountJob.class);
        if (officialAccount != null) officialAccount.start();
    }

    @Override
    public void stop() {
        if (officialAccount != null) officialAccount.stop();
        schemes.unregister(Scheme.buildFromType(OfficialAccountJob.class));
        schemes.unregister(Scheme.buildFromType(OfficialAccountSettings.class));
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
