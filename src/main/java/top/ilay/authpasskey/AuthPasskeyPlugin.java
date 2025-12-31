package top.ilay.authpasskey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author ilay
 * @since 1.0.0
 */
@Slf4j
@Component
public class AuthPasskeyPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public AuthPasskeyPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        log.info("Passkey authentication plugin starting...");
        schemeManager.register(PasskeyCredential.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<PasskeyCredential, String>single("spec.username", String.class)
                .indexFunc(credential -> credential.getSpec().getUsername())
            );
            indexSpecs.add(IndexSpecs.<PasskeyCredential, String>single("spec.credentialId", String.class)
                .indexFunc(credential -> credential.getSpec().getCredentialId())
                .unique(true)
            );
        });
        log.info("Passkey authentication plugin started successfully!");
    }

    @Override
    public void stop() {
        log.info("Passkey authentication plugin stopping...");
        schemeManager.unregister(Scheme.buildFromType(PasskeyCredential.class));
        log.info("Passkey authentication plugin stopped!");
    }
}
