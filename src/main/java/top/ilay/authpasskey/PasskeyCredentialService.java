package top.ilay.authpasskey;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/**
 * Service for managing Passkey credentials.
 *
 * @author ilay
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyCredentialService {

    private final ReactiveExtensionClient extensionClient;

    /**
     * Find all credentials for a user.
     */
    public Flux<PasskeyCredential> findByUsername(String username) {
        var listOptions = ListOptions.builder()
            .fieldQuery(Queries.equal("spec.username", username))
            .build();
        return extensionClient.listAll(PasskeyCredential.class, listOptions, null);
    }

    /**
     * Find a credential by its credential ID.
     */
    public Mono<PasskeyCredential> findByCredentialId(String credentialId) {
        var listOptions = ListOptions.builder()
            .fieldQuery(Queries.equal("spec.credentialId", credentialId))
            .build();
        return extensionClient.listAll(PasskeyCredential.class, listOptions, null)
            .next();
    }

    /**
     * Find a credential by its metadata name.
     */
    public Mono<PasskeyCredential> findByName(String name) {
        return extensionClient.get(PasskeyCredential.class, name);
    }

    /**
     * Save a new credential.
     */
    public Mono<PasskeyCredential> save(PasskeyCredential credential) {
        return extensionClient.create(credential);
    }

    /**
     * Update an existing credential.
     */
    public Mono<PasskeyCredential> update(PasskeyCredential credential) {
        return extensionClient.update(credential);
    }

    /**
     * Delete a credential by name.
     */
    public Mono<PasskeyCredential> delete(String name) {
        return extensionClient.get(PasskeyCredential.class, name)
            .flatMap(extensionClient::delete);
    }

    /**
     * Update the signature count and last used timestamp for a credential.
     */
    public Mono<PasskeyCredential> updateSignatureCount(String credentialId, long newCount) {
        return findByCredentialId(credentialId)
            .flatMap(credential -> {
                credential.getSpec().setSignatureCount(newCount);
                credential.getSpec().setLastUsedAt(Instant.now());
                return extensionClient.update(credential);
            });
    }

    /**
     * Create a new PasskeyCredential entity.
     */
    public PasskeyCredential createCredential(
        String username,
        byte[] credentialId,
        byte[] publicKey,
        long signatureCount,
        String displayName,
        byte[] aaguid,
        boolean discoverable,
        boolean userVerified,
        boolean backupEligible,
        boolean backedUp,
        List<String> transports
    ) {
        var credential = new PasskeyCredential();
        var metadata = new Metadata();
        metadata.setGenerateName("passkey-");
        credential.setMetadata(metadata);

        var spec = new PasskeyCredential.PasskeyCredentialSpec();
        spec.setUsername(username);
        spec.setCredentialId(Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId));
        spec.setPublicKey(Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey));
        spec.setSignatureCount(signatureCount);
        spec.setDisplayName(displayName);
        if (aaguid != null) {
            spec.setAaguid(Base64.getUrlEncoder().withoutPadding().encodeToString(aaguid));
        }
        spec.setDiscoverable(discoverable);
        spec.setUserVerified(userVerified);
        spec.setBackupEligible(backupEligible);
        spec.setBackedUp(backedUp);
        if (transports != null) {
            spec.setTransports(transports.toArray(new String[0]));
        }
        spec.setCreatedAt(Instant.now());

        credential.setSpec(spec);
        return credential;
    }
}
