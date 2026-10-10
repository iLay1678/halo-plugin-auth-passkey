package top.ilay.authpasskey;

import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.model.IModel;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

class PasskeyLoginHeadProcessorTest {

    @Test
    void injectsDeferredScriptIntoLoginPage() {
        var context = new Context(Locale.ROOT);
        context.setVariable("formAuthProviders", Set.of());

        assertThat(render(context)).contains(
            "src=\"/plugins/auth-passkey/assets/static/login-context.js\"", "defer=\"defer\"",
            "</script>"
        );
    }

    @Test
    void leavesOtherPagesUnchanged() {
        assertThat(render(new Context(Locale.ROOT))).isEqualTo("<head></head>");
    }

    private String render(Context context) {
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        engine.addDialect(new AbstractProcessorDialect("test", "test", 1000) {
            @Override
            public Set<IProcessor> getProcessors(String dialectPrefix) {
                return Set.of(new AbstractElementModelProcessor(TemplateMode.HTML,
                    dialectPrefix, "head", false, null, false, 1000) {
                    @Override
                    protected void doProcess(ITemplateContext templateContext, IModel model,
                        IElementModelStructureHandler handler) {
                        if (templateContext.containsVariable("passkeyHeadProcessed")) {
                            return;
                        }
                        handler.setLocalVariable("passkeyHeadProcessed", true);
                        model.remove(model.size() - 1);
                        new PasskeyLoginHeadProcessor().process(templateContext, model, handler)
                            .block();
                        model.add(templateContext.getModelFactory().createCloseElementTag("head"));
                    }
                });
            }
        });
        return engine.process("<head></head>", context);
    }
}
