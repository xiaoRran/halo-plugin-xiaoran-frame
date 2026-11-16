package run.halo.starter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.plugin.PluginContext;
import run.halo.app.extension.SchemeManager;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StarterPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemes;

    @InjectMocks
    StarterPlugin plugin;

    @Test
    void contextLoads() {
        plugin.start();
        verify(schemes).register(LifeRecord.class);
        plugin.stop();
    }
}
