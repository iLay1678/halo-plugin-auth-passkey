package top.ilay.authpasskey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthPasskeyPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemeManager;

    AuthPasskeyPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new AuthPasskeyPlugin(context, schemeManager);
    }

    @Test
    void contextLoads() {
        doNothing().when(schemeManager).register(eq(PasskeyCredential.class), any());
        doNothing().when(schemeManager).unregister(any());

        plugin.start();
        plugin.stop();

        verify(schemeManager).register(eq(PasskeyCredential.class), any());
        verify(schemeManager).unregister(any());
    }
}
