package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.EncryptionOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AesGcmEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KDF_ALGORITHM = "HmacSHA256";

    private static final int NONCE_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private static final byte FORMAT_VERSION = 1;

    private static final byte[] KDF_SALT =
            "GIDEON-HERITAGE-BANK-NOTIFICATION-KEY-V1"
                    .getBytes(StandardCharsets.UTF_8);

    private final NotificationEncryptionProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext, String purpose) {

        if (plaintext == null) {
            throw new IllegalArgumentException(
                    "Plaintext cannot be null."
            );
        }

        validatePurpose(purpose);

        byte[] derivedKey = null;

        try {

            derivedKey = deriveKey(purpose);

            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            SecretKeySpec secretKey =
                    new SecretKeySpec(derivedKey, ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonce);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            cipher.updateAAD(
                    purpose.getBytes(StandardCharsets.UTF_8)
            );

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            /*
             * Stored envelope:
             *
             * 1 byte: format version
             * 12 bytes: random nonce
             * Remaining bytes: ciphertext + GCM authentication tag
             *
             * Entire envelope is Base64 encoded.
             */

            ByteBuffer buffer = ByteBuffer.allocate(
                    1 + NONCE_LENGTH + ciphertext.length
            );

            buffer.put(FORMAT_VERSION);
            buffer.put(nonce);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(
                    buffer.array()
            );

        } catch (GeneralSecurityException exception) {

            throw new EncryptionOperationException(
                    "Unable to encrypt notification content.",
                    exception
            );

        } finally {

            if (derivedKey != null) {
                Arrays.fill(derivedKey, (byte) 0);
            }
        }
    }

    public String decrypt(
            String encryptedValue,
            String purpose
    ) {

        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Encrypted value is required."
            );
        }

        validatePurpose(purpose);

        byte[] storedData;

        try {

            storedData = Base64.getDecoder().decode(encryptedValue);

        } catch (IllegalArgumentException exception) {

            throw new EncryptionOperationException(
                    "Encrypted data has invalid Base64 encoding.",
                    exception
            );
        }

        if (storedData.length <= 1 + NONCE_LENGTH) {
            throw new EncryptionOperationException(
                    "Encrypted data is incomplete."
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(storedData);

        byte version = buffer.get();

        if (version != FORMAT_VERSION) {
            throw new EncryptionOperationException(
                    "Unsupported encrypted data format version."
            );
        }

        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        byte[] derivedKey = null;

        try {

            derivedKey = deriveKey(purpose);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            SecretKeySpec secretKey =
                    new SecretKeySpec(derivedKey, ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonce);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            cipher.updateAAD(
                    purpose.getBytes(StandardCharsets.UTF_8)
            );

            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(
                    plaintext,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException exception) {

            /*
             * Authentication failures include:
             * - incorrect key
             * - modified ciphertext
             * - incorrect purpose
             */

            throw new EncryptionOperationException(
                    "Unable to decrypt notification content. "
                            + "The key may be incorrect or the data "
                            + "may have been modified.",
                    exception
            );

        } finally {

            if (derivedKey != null) {
                Arrays.fill(derivedKey, (byte) 0);
            }
        }
    }

    /*
     * HKDF-style HMAC-SHA256 derivation.
     *
     * The inbox service continues using the existing master key
     * and its original encryption format.
     *
     * This component derives independent keys for:
     * NOTIFICATION_DELIVERY_CONTENT
     * NOTIFICATION_DELIVERY_DESTINATION
     */
    private byte[] deriveKey(String purpose)
            throws GeneralSecurityException {

        String encodedMasterKey = properties.getEncryptionKey();

        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            throw new IllegalStateException(
                    "Notification inbox encryption key is not configured."
            );
        }

        byte[] masterKey;

        try {

            masterKey = Base64.getDecoder().decode(encodedMasterKey);

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "Notification inbox encryption key must be valid Base64.",
                    exception
            );
        }

        if (masterKey.length != KEY_LENGTH_BYTES) {

            Arrays.fill(masterKey, (byte) 0);

            throw new IllegalStateException(
                    "Notification inbox encryption key must contain "
                            + KEY_LENGTH_BYTES + " bytes."
            );
        }

        byte[] pseudoRandomKey = null;

        try {

            Mac extract = Mac.getInstance(KDF_ALGORITHM);

            extract.init(
                    new SecretKeySpec(KDF_SALT, KDF_ALGORITHM)
            );

            pseudoRandomKey = extract.doFinal(masterKey);

            Mac expand = Mac.getInstance(KDF_ALGORITHM);

            expand.init(
                    new SecretKeySpec(
                            pseudoRandomKey,
                            KDF_ALGORITHM
                    )
            );

            expand.update(
                    ("GHB-NOTIFICATION:" + purpose)
                            .getBytes(StandardCharsets.UTF_8)
            );

            expand.update((byte) 0x01);

            return Arrays.copyOf(
                    expand.doFinal(),
                    KEY_LENGTH_BYTES
            );

        } finally {

            Arrays.fill(masterKey, (byte) 0);

            if (pseudoRandomKey != null) {
                Arrays.fill(pseudoRandomKey, (byte) 0);
            }
        }
    }

    private void validatePurpose(String purpose) {

        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption purpose is required."
            );
        }
    }
}