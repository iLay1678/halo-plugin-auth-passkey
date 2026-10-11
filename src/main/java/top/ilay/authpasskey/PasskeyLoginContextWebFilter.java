package top.ilay.authpasskey;

import java.nio.charset.StandardCharsets;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.security.AdditionalWebFilter;

/**
 * Load the login context script on authentication pages, which exclude theme head processors.
 */
@Component
public class PasskeyLoginContextWebFilter implements AdditionalWebFilter {

    private static final String BODY_SCRIPT =
        "<script src=\"/plugins/auth-passkey/assets/static/login-context.js?v=2.0.0\"></script>";
    private static final String HEAD_SCRIPT =
        "<script src=\"/plugins/auth-passkey/assets/static/login-context.js?v=2.0.0\" defer></script>";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.GET
            || !"/login".equals(request.getPath().pathWithinApplication().value())) {
            return chain.filter(exchange);
        }

        var response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                var contentType = getHeaders().getContentType();
                if ((getStatusCode() != null && !HttpStatus.OK.equals(getStatusCode()))
                    || contentType == null || !MediaType.TEXT_HTML.isCompatibleWith(contentType)) {
                    return super.writeWith(body);
                }
                var charset = contentType.getCharset() != null
                    ? contentType.getCharset() : StandardCharsets.UTF_8;
                return DataBufferUtils.join(body).flatMap(buffer -> {
                    String html;
                    try {
                        html = buffer.toString(charset);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                    var bodyEnd = html.indexOf("</body>");
                    if (bodyEnd >= 0) {
                        html = html.substring(0, bodyEnd) + BODY_SCRIPT + html.substring(bodyEnd);
                    } else {
                        var headEnd = html.indexOf("</head>");
                        if (headEnd >= 0) {
                            html = html.substring(0, headEnd) + HEAD_SCRIPT + html.substring(headEnd);
                        }
                    }
                    getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                    return super.writeWith(Mono.just(bufferFactory().wrap(html.getBytes(charset))));
                });
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                Publisher<? extends Publisher<? extends DataBuffer>> body) {
                // Thymeleaf renders in chunks; the closing head tag may span multiple buffers.
                return writeWith(Flux.from(body).concatMap(Flux::from));
            }
        };
        return chain.filter(exchange.mutate().response(response).build());
    }
}
