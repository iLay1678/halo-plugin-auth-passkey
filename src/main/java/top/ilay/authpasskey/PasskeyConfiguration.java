package top.ilay.authpasskey;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalUrlSupplier;

/**
 * Passkey configuration class.
 *
 * @author ilay
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class PasskeyConfiguration {

    private final ReactiveExtensionClient extensionClient;

    private final ExternalUrlSupplier externalUrlSupplier;

    public ReactiveExtensionClient getExtensionClient() {
        return extensionClient;
    }

    public ExternalUrlSupplier getExternalUrlSupplier() {
        return externalUrlSupplier;
    }
}
