package top.ilay.authpasskey;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for handling WebAuthn operations.
 *
 * @author ilay
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebAuthnService {

    private final PasskeyCredentialService credentialService;

    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final AttestedCredentialDataConverter attestedCredentialDataConverter =
        new AttestedCredentialDataConverter(objectConverter);
    private final SecureRandom secureRandom = new SecureRandom();

    // Temporary storage for challenges (in production, use a distributed cache like Redis)
    private final Map<String, ChallengeData> challengeStore = new ConcurrentHashMap<>();

    /**
     * Extract rpId (hostname) from origin URL.
     */
    private String extractRpId(String origin) {
        if (origin == null || origin.isEmpty()) {
            throw new IllegalStateException("请求缺少 origin 参数");
        }
        try {
            java.net.URI uri = new java.net.URI(origin);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException("无法从 origin 解析域名: " + origin);
            }
            return host;
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("无效的 origin 格式: " + origin, e);
        }
    }

    /**
     * Generate registration options for a user.
     */
    public Mono<RegistrationOptions> generateRegistrationOptions(String username, String displayName, String origin) {
        return credentialService.findByUsername(username)
            .map(cred -> cred.getSpec().getCredentialId())
            .collectList()
            .map(excludeCredentialIds -> {
                String rpId = extractRpId(origin);

                byte[] challengeBytes = new byte[32];
                secureRandom.nextBytes(challengeBytes);
                String challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

                byte[] userIdBytes = new byte[32];
                secureRandom.nextBytes(userIdBytes);
                String userIdBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(userIdBytes);

                String rpName = "Halo";

                // Store challenge for verification
                challengeStore.put(username, new ChallengeData(challengeBytes, userIdBytes, System.currentTimeMillis()));

                return new RegistrationOptions(
                    challengeBase64,
                    new RelyingParty(rpId, rpName),
                    new User(userIdBase64, username, displayName != null ? displayName : username),
                    excludeCredentialIds,
                    new AuthenticatorSelection(
                        null,  // Allow both platform and cross-platform authenticators
                        "preferred",
                        "preferred"
                    ),
                    60000L,
                    List.of(
                        new PublicKeyCredentialParameters("public-key", -7),  // ES256
                        new PublicKeyCredentialParameters("public-key", -257) // RS256
                    )
                );
            });
    }

    /**
     * Verify registration response and save credential.
     */
    public Mono<PasskeyCredential> verifyRegistration(
        String username,
        String credentialId,
        String attestationObject,
        String clientDataJSON,
        List<String> transports,
        String displayName,
        String origin
    ) {
        return Mono.fromCallable(() -> {
            ChallengeData challengeData = challengeStore.remove(username);
            if (challengeData == null) {
                throw new IllegalStateException("未找到用户的挑战信息: " + username);
            }

            if (System.currentTimeMillis() - challengeData.createdAt() > 120000) {
                throw new IllegalStateException("挑战已过期");
            }

            String rpId = extractRpId(origin);
            String originStr = origin.replaceAll("/$", "");
            Origin webAuthnOrigin = new Origin(originStr);
            Challenge challenge = new DefaultChallenge(challengeData.challenge());

            byte[] credentialIdBytes = Base64.getUrlDecoder().decode(credentialId);
            byte[] attestationObjectBytes = Base64.getUrlDecoder().decode(attestationObject);
            byte[] clientDataJSONBytes = Base64.getUrlDecoder().decode(clientDataJSON);

            RegistrationRequest registrationRequest = new RegistrationRequest(
                attestationObjectBytes,
                clientDataJSONBytes,
                null,
                Set.of()
            );

            RegistrationParameters registrationParameters = new RegistrationParameters(
                new ServerProperty(webAuthnOrigin, rpId, challenge, null),
                null,
                false,
                true
            );

            RegistrationData registrationData = webAuthnManager.parse(registrationRequest);
            webAuthnManager.verify(registrationData, registrationParameters);

            AttestedCredentialData attestedCredentialData = registrationData
                .getAttestationObject()
                .getAuthenticatorData()
                .getAttestedCredentialData();

            if (attestedCredentialData == null) {
                throw new IllegalStateException("未找到凭证数据");
            }

            byte[] aaguid = attestedCredentialData.getAaguid().getBytes();
            // Serialize the attested credential data for storage
            byte[] attestedCredBytes = attestedCredentialDataConverter.convert(attestedCredentialData);

            boolean userVerified = registrationData.getAttestationObject()
                .getAuthenticatorData()
                .isFlagUV();

            boolean backupEligible = registrationData.getAttestationObject()
                .getAuthenticatorData()
                .isFlagBE();

            boolean backedUp = registrationData.getAttestationObject()
                .getAuthenticatorData()
                .isFlagBS();

            long signatureCount = registrationData.getAttestationObject()
                .getAuthenticatorData()
                .getSignCount();

            return credentialService.createCredential(
                username,
                credentialIdBytes,
                attestedCredBytes,  // Store the full attested credential data
                signatureCount,
                displayName != null ? displayName : "Passkey",
                aaguid,
                true,
                userVerified,
                backupEligible,
                backedUp,
                transports
            );
        }).flatMap(credentialService::save);
    }

    /**
     * Generate authentication options.
     */
    public Mono<AuthenticationOptions> generateAuthenticationOptions(String username, String origin) {
        Mono<List<String>> allowCredentialsMono;
        if (username != null && !username.isEmpty()) {
            allowCredentialsMono = credentialService.findByUsername(username)
                .map(cred -> cred.getSpec().getCredentialId())
                .collectList();
        } else {
            allowCredentialsMono = Mono.just(Collections.emptyList());
        }

        return allowCredentialsMono.map(allowCredentials -> {
            String rpId = extractRpId(origin);

            byte[] challengeBytes = new byte[32];
            secureRandom.nextBytes(challengeBytes);
            String challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

            String sessionId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes());

            challengeStore.put(sessionId, new ChallengeData(challengeBytes, null, System.currentTimeMillis()));

            return new AuthenticationOptions(
                challengeBase64,
                rpId,
                60000L,
                allowCredentials,
                "preferred",
                sessionId
            );
        });
    }

    /**
     * Verify authentication response.
     */
    public Mono<PasskeyCredential> verifyAuthentication(
        String sessionId,
        String credentialId,
        String authenticatorData,
        String clientDataJSON,
        String signature,
        String userHandle,
        String origin
    ) {
        return credentialService.findByCredentialId(credentialId)
            .switchIfEmpty(Mono.error(new IllegalStateException("凭证不存在")))
            .flatMap(credential -> Mono.fromCallable(() -> {
                ChallengeData challengeData = challengeStore.remove(sessionId);
                if (challengeData == null) {
                    throw new IllegalStateException("会话挑战信息不存在");
                }

                if (System.currentTimeMillis() - challengeData.createdAt() > 120000) {
                    throw new IllegalStateException("挑战已过期");
                }

                String rpId = extractRpId(origin);
                String originStr = origin.replaceAll("/$", "");
                Origin webAuthnOrigin = new Origin(originStr);
                Challenge challenge = new DefaultChallenge(challengeData.challenge());

                byte[] credentialIdBytes = Base64.getUrlDecoder().decode(credentialId);
                byte[] authenticatorDataBytes = Base64.getUrlDecoder().decode(authenticatorData);
                byte[] clientDataJSONBytes = Base64.getUrlDecoder().decode(clientDataJSON);
                byte[] signatureBytes = Base64.getUrlDecoder().decode(signature);
                byte[] userHandleBytes = userHandle != null ? Base64.getUrlDecoder().decode(userHandle) : null;

                // Deserialize the stored attested credential data
                byte[] storedPublicKeyBytes = Base64.getUrlDecoder().decode(credential.getSpec().getPublicKey());
                AttestedCredentialData attestedCredentialData =
                    attestedCredentialDataConverter.convert(storedPublicKeyBytes);

                AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                    credentialIdBytes,
                    userHandleBytes,
                    authenticatorDataBytes,
                    clientDataJSONBytes,
                    null,
                    signatureBytes
                );

                CredentialRecordImpl credentialRecord = new CredentialRecordImpl(
                    null,  // attestationStatement
                    credential.getSpec().isUserVerified(),  // uvInitialized
                    credential.getSpec().isBackupEligible(),  // backupEligible
                    credential.getSpec().isBackedUp(),  // backupState
                    credential.getSpec().getSignatureCount(),  // counter
                    attestedCredentialData,
                    null,  // authenticatorExtensions
                    null,  // clientData
                    null,  // clientExtensions
                    null   // transports
                );

                AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                    new ServerProperty(webAuthnOrigin, rpId, challenge, null),
                    credentialRecord,
                    List.of(credentialIdBytes),
                    false,
                    false
                );

                AuthenticationData authenticationData = webAuthnManager.parse(authenticationRequest);
                webAuthnManager.verify(authenticationData, authenticationParameters);

                return authenticationData.getAuthenticatorData().getSignCount();
            }).flatMap(newSignCount ->
                credentialService.updateSignatureCount(credentialId, newSignCount)
            ));
    }

    /**
     * Clean up expired challenges periodically.
     */
    public void cleanupExpiredChallenges() {
        long now = System.currentTimeMillis();
        challengeStore.entrySet().removeIf(entry ->
            now - entry.getValue().createdAt() > 300000  // 5 minutes
        );
    }

    record ChallengeData(byte[] challenge, byte[] userId, long createdAt) {}

    public record RegistrationOptions(
        String challenge,
        RelyingParty rp,
        User user,
        List<String> excludeCredentials,
        AuthenticatorSelection authenticatorSelection,
        Long timeout,
        List<PublicKeyCredentialParameters> pubKeyCredParams
    ) {}

    public record RelyingParty(String id, String name) {}

    public record User(String id, String name, String displayName) {}

    public record AuthenticatorSelection(
        String authenticatorAttachment,
        String residentKey,
        String userVerification
    ) {}

    public record PublicKeyCredentialParameters(String type, int alg) {}

    public record AuthenticationOptions(
        String challenge,
        String rpId,
        Long timeout,
        List<String> allowCredentials,
        String userVerification,
        String sessionId
    ) {}
}
