package top.ilay.authpasskey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.AuthenticationSecurityWebFilter;

/**
 * Security web filter for Passkey authentication.
 * Implements FormLoginSecurityWebFilter to integrate with Halo's authentication system.
 *
 * @author ilay
 * @since 1.0.0
 */
@Slf4j
@Component
public class PasskeyAuthenticationWebFilter implements AuthenticationSecurityWebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Passkey 认证通过 PasskeyEndpoint API 处理
        // 此过滤器用于注册到 Halo 认证系统
        return chain.filter(exchange);
    }
}
