package top.ilay.authpasskey;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * Passkey credential extension for storing WebAuthn credentials.
 *
 * @author ilay
 * @since 1.0.0
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(group = "passkey.halo.run", version = "v1alpha1", kind = "PasskeyCredential",
    plural = "passkeycredentials", singular = "passkeycredential")
public class PasskeyCredential extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private PasskeyCredentialSpec spec;

    @Data
    @ToString
    public static class PasskeyCredentialSpec {

        /**
         * The username this credential belongs to.
         */
        @Schema(requiredMode = REQUIRED)
        private String username;

        /**
         * Base64url encoded credential ID.
         */
        @Schema(requiredMode = REQUIRED)
        private String credentialId;

        /**
         * Base64url encoded public key in COSE format.
         */
        @Schema(requiredMode = REQUIRED)
        private String publicKey;

        /**
         * Signature counter for replay attack protection.
         */
        @Schema(requiredMode = REQUIRED)
        private long signatureCount;

        /**
         * User-friendly name for this credential.
         */
        private String displayName;

        /**
         * The AAGUID of the authenticator.
         */
        private String aaguid;

        /**
         * Whether this credential is discoverable (resident key).
         */
        private boolean discoverable;

        /**
         * Whether user verification was performed during registration.
         */
        private boolean userVerified;

        /**
         * Whether the credential is eligible for backup (BE flag).
         */
        private boolean backupEligible;

        /**
         * Whether the credential is backed up (e.g., synced to cloud).
         */
        private boolean backedUp;

        /**
         * Transports supported by the authenticator (e.g., usb, nfc, ble, internal).
         */
        private String[] transports;

        /**
         * When this credential was created.
         */
        @Schema(requiredMode = REQUIRED)
        private Instant createdAt;

        /**
         * When this credential was last used.
         */
        private Instant lastUsedAt;
    }
}
