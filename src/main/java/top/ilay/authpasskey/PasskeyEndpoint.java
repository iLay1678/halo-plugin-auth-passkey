package top.ilay.authpasskey;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Passkey API endpoints for WebAuthn registration and authentication.
 *
 * @author ilay
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasskeyEndpoint implements CustomEndpoint {

    private final WebAuthnService webAuthnService;
    private final PasskeyCredentialService credentialService;
    private final ReactiveUserDetailsService userDetailsService;
    private final ServerSecurityContextRepository securityContextRepository;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            // Registration endpoints (requires authentication)
            .POST("/registration/options", this::getRegistrationOptions)
            .POST("/registration/verify", this::verifyRegistration)
            // Authentication endpoints (public)
            .POST("/authentication/options", this::getAuthenticationOptions)
            .POST("/authentication/verify", this::verifyAuthentication)
            // Credential management endpoints (requires authentication)
            .GET("/credentials", this::listCredentials)
            .DELETE("/credentials/{name}", this::deleteCredential)
            .PUT("/credentials/{name}", this::updateCredential)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api.passkey.halo.run", "v1alpha1");
    }

    private Mono<ServerResponse> getRegistrationOptions(ServerRequest request) {
        return getCurrentUsername()
            .flatMap(username -> request.bodyToMono(RegistrationOptionsRequest.class)
                .defaultIfEmpty(new RegistrationOptionsRequest(null, null))
                .flatMap(req -> webAuthnService.generateRegistrationOptions(
                    username,
                    req.displayName() != null ? req.displayName() : username,
                    req.origin()
                ))
            )
            .flatMap(options -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(options)
            )
            .onErrorResume(e -> {
                log.error("Failed to generate registration options", e);
                return ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage()));
            });
    }

    private Mono<ServerResponse> verifyRegistration(ServerRequest request) {
        return getCurrentUsername()
            .flatMap(username -> request.bodyToMono(RegistrationVerifyRequest.class)
                .flatMap(req -> webAuthnService.verifyRegistration(
                    username,
                    req.credentialId(),
                    req.attestationObject(),
                    req.clientDataJSON(),
                    req.transports(),
                    req.displayName(),
                    req.origin()
                ))
            )
            .flatMap(credential -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationResponse(
                    credential.getMetadata().getName(),
                    credential.getSpec().getCredentialId(),
                    credential.getSpec().getDisplayName()
                ))
            )
            .onErrorResume(e -> {
                log.error("Failed to verify registration", e);
                return ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage()));
            });
    }

    private Mono<ServerResponse> getAuthenticationOptions(ServerRequest request) {
        return request.bodyToMono(AuthenticationOptionsRequest.class)
            .defaultIfEmpty(new AuthenticationOptionsRequest(null, null))
            .flatMap(req -> webAuthnService.generateAuthenticationOptions(req.username(), req.origin()))
            .flatMap(options -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(options)
            )
            .onErrorResume(e -> {
                log.error("Failed to generate authentication options", e);
                return ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage()));
            });
    }

    private Mono<ServerResponse> verifyAuthentication(ServerRequest request) {
        return request.bodyToMono(AuthenticationVerifyRequest.class)
            .flatMap(req -> webAuthnService.verifyAuthentication(
                req.sessionId(),
                req.credentialId(),
                req.authenticatorData(),
                req.clientDataJSON(),
                req.signature(),
                req.userHandle(),
                req.origin()
            ))
            .flatMap(credential -> {
                String username = credential.getSpec().getUsername();
                // Load user details and create authentication
                return userDetailsService.findByUsername(username)
                    .flatMap(userDetails -> {
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                        );
                        SecurityContext securityContext = new SecurityContextImpl(authentication);
                        // Save security context to establish session
                        return securityContextRepository.save(request.exchange(), securityContext)
                            .then(Mono.just(credential));
                    });
            })
            .flatMap(credential -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationResponse(
                    credential.getSpec().getUsername(),
                    credential.getMetadata().getName(),
                    true
                ))
            )
            .onErrorResume(e -> {
                log.error("Failed to verify authentication", e);
                return ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage()));
            });
    }

    private Mono<ServerResponse> listCredentials(ServerRequest request) {
        return getCurrentUsername()
            .flatMapMany(credentialService::findByUsername)
            .map(credential -> new CredentialInfo(
                credential.getMetadata().getName(),
                credential.getSpec().getCredentialId(),
                credential.getSpec().getDisplayName(),
                credential.getSpec().getCreatedAt().toString(),
                credential.getSpec().getLastUsedAt() != null
                    ? credential.getSpec().getLastUsedAt().toString() : null,
                credential.getSpec().isBackedUp(),
                credential.getSpec().getTransports() != null
                    ? List.of(credential.getSpec().getTransports()) : List.of()
            ))
            .collectList()
            .flatMap(credentials -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CredentialListResponse(credentials))
            );
    }

    private Mono<ServerResponse> deleteCredential(ServerRequest request) {
        String name = request.pathVariable("name");
        return getCurrentUsername()
            .flatMap(username -> credentialService.findByName(name)
                .filter(cred -> cred.getSpec().getUsername().equals(username))
                .switchIfEmpty(Mono.error(new IllegalStateException("凭证不存在或无权访问")))
            )
            .flatMap(credential -> credentialService.delete(credential.getMetadata().getName()))
            .flatMap(deleted -> ServerResponse.ok().bodyValue(new DeleteResponse(true)))
            .onErrorResume(e -> {
                log.error("Failed to delete credential", e);
                return ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage()));
            });
    }

    private Mono<ServerResponse> updateCredential(ServerRequest request) {
        String name = request.pathVariable("name");
        return getCurrentUsername()
            .flatMap(username -> credentialService.findByName(name)
                .filter(cred -> cred.getSpec().getUsername().equals(username))
                .switchIfEmpty(Mono.error(new IllegalStateException("凭证不存在或无权访问")))
            )
            .flatMap(credential -> request.bodyToMono(UpdateCredentialRequest.class)
                .flatMap(req -> {
                    if (req.displayName() != null) {
                        credential.getSpec().setDisplayName(req.displayName());
                    }
                    return credentialService.update(credential);
                })
            )
            .flatMap(updated -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateResponse(true))
            )
            .onErrorResume(e -> {
                log.error("Failed to update credential", e);
                return ServerResponse.badRequest().bodyValue(new ErrorResponse(e.getMessage()));
            });
    }

    private Mono<String> getCurrentUsername() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .switchIfEmpty(Mono.error(new IllegalStateException("用户未登录")));
    }

    // Request/Response records
    record RegistrationOptionsRequest(String displayName, String origin) {}

    record RegistrationVerifyRequest(
        String credentialId,
        String attestationObject,
        String clientDataJSON,
        List<String> transports,
        String displayName,
        String origin
    ) {}

    record AuthenticationOptionsRequest(String username, String origin) {}

    record AuthenticationVerifyRequest(
        String sessionId,
        String credentialId,
        String authenticatorData,
        String clientDataJSON,
        String signature,
        String userHandle,
        String origin
    ) {}

    record RegistrationResponse(String name, String credentialId, String displayName) {}

    record AuthenticationResponse(String username, String credentialName, boolean verified) {}

    record CredentialInfo(
        String name,
        String credentialId,
        String displayName,
        String createdAt,
        String lastUsedAt,
        boolean backedUp,
        List<String> transports
    ) {}

    record CredentialListResponse(List<CredentialInfo> credentials) {}

    record DeleteResponse(boolean success) {}

    record UpdateCredentialRequest(String displayName) {}

    record UpdateResponse(boolean success) {}

    record ErrorResponse(String message) {}
}
