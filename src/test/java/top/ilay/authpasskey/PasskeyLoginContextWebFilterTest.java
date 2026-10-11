package top.ilay.authpasskey;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class PasskeyLoginContextWebFilterTest {

    private final PasskeyLoginContextWebFilter filter = new PasskeyLoginContextWebFilter();

    @Test
    void injectsScriptIntoLoginResponseAcrossThymeleafChunks() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/login?method=local&redirect_uri=%2Foidc%2Fauthorize"));
        filter.filter(exchange, filtered -> {
            var response = filtered.getResponse();
            response.getHeaders().setContentType(MediaType.TEXT_HTML);
            response.getHeaders().setContentLength(10);
            return response.writeAndFlushWith(Flux.just(
                Mono.just(buffer(filtered, "<html><head><title>登录</title></he")),
                Mono.just(buffer(filtered, "ad><body><form id=\"login-form\"></form></body></html>"))
            ));
        }).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo(
            "<html><head><title>登录</title></head><body><form id=\"login-form\"></form>"
                + "<script src=\"/plugins/auth-passkey/assets/static/login-context.js?v=2.0.0\"></script>"
                + "</body></html>"
        );
        assertThat(exchange.getResponse().getHeaders().getContentLength()).isEqualTo(-1);
    }

    @Test
    void injectsScriptIntoPasskeyPage() {
        assertThat(render(HttpMethod.GET, "/login?method=passkey", MediaType.TEXT_HTML,
            HttpStatus.OK, "<head></head><body></body>"))
            .contains("<script src=\"/plugins/auth-passkey/assets/static/login-context.js?v=2.0.0\"></script></body>");
    }

    @Test
    void fallsBackToHeadScriptWhenResponseHasNoBody() {
        assertThat(render(HttpMethod.GET, "/login?method=passkey", MediaType.TEXT_HTML,
            HttpStatus.OK, "<head></head>"))
            .contains("login-context.js?v=2.0.0\" defer></script></head>");
    }

    @Test
    void leavesOtherRoutesAndPostRequestsUnchanged() {
        for (var path : new String[] {"/", "/signup", "/post", "/login/other"}) {
            assertThat(render(HttpMethod.GET, path, MediaType.TEXT_HTML,
                HttpStatus.OK, "<head></head>")).isEqualTo("<head></head>");
        }
        assertThat(render(HttpMethod.POST, "/login", MediaType.TEXT_HTML,
            HttpStatus.OK, "<head></head>")).isEqualTo("<head></head>");
    }

    @Test
    void leavesNonHtmlAndErrorResponsesUnchanged() {
        assertThat(render(HttpMethod.GET, "/login", MediaType.APPLICATION_JSON,
            HttpStatus.OK, "{\"message\":\"</head>\"}"))
            .isEqualTo("{\"message\":\"</head>\"}");
        assertThat(render(HttpMethod.GET, "/login", MediaType.TEXT_HTML,
            HttpStatus.INTERNAL_SERVER_ERROR, "<head></head>"))
            .isEqualTo("<head></head>");
        assertThat(render(HttpMethod.GET, "/login", MediaType.TEXT_HTML,
            HttpStatus.OK, "<body>登录</body>"))
            .isEqualTo("<body>登录<script src=\"/plugins/auth-passkey/assets/static/login-context.js?v=2.0.0\"></script></body>");
    }

    private String render(HttpMethod method, String path, MediaType contentType,
        HttpStatus status, String html) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        filter.filter(exchange, filtered -> {
            var response = filtered.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(contentType);
            return response.writeWith(Mono.just(buffer(filtered, html)));
        }).block();
        return exchange.getResponse().getBodyAsString().block();
    }

    private DataBuffer buffer(ServerWebExchange exchange, String value) {
        return exchange.getResponse().bufferFactory().wrap(value.getBytes(StandardCharsets.UTF_8));
    }
}
