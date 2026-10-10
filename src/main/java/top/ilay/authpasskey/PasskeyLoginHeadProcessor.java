package top.ilay.authpasskey;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.theme.dialect.TemplateHeadProcessor;

/**
 * Preserve the login context when switching between form authentication providers.
 */
@Component
public class PasskeyLoginHeadProcessor implements TemplateHeadProcessor {

    @Override
    public Mono<Void> process(ITemplateContext context, IModel model,
        IElementModelStructureHandler structureHandler) {
        if (!context.containsVariable("formAuthProviders")) {
            return Mono.empty();
        }
        var factory = context.getModelFactory();
        model.add(factory.createOpenElementTag("script", Map.of(
            "src", "/plugins/auth-passkey/assets/static/login-context.js",
            "defer", "defer"
        ), AttributeValueQuotes.DOUBLE, false));
        model.add(factory.createCloseElementTag("script"));
        return Mono.empty();
    }
}
