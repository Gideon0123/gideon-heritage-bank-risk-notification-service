package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.EncryptionOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
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
    private static final int NONCE_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final byte FORMAT_VERSION = 1;

    private final NotificationEncryptionProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext, String purpose) {

        if (plaintext == null) {
            throw new IllegalArgumentException(
                    "Plaintext cannot be null."
            );
        }

        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption purpose is required."
            );
        }

        try {
            byte[] key = getEncryptionKey();
            byte[] nonce = new byte[NONCE_LENGTH];

            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            SecretKeySpec secretKey =
                    new SecretKeySpec(key, ALGORITHM);

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
             * Stored binary structure:
             *
             * 1 byte  - format version
             * 12 bytes - nonce
             * remaining bytes - ciphertext + authentication tag
             *
             * The entire structure is Base64 encoded.
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
                    "Unable to encrypt sensitive notification data.",
                    exception
            );
        }
    }

    public String decrypt(String encryptedValue, String purpose) {

        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Encrypted value is required."
            );
        }

        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException(
                    "Decryption purpose is required."
            );
        }

        byte[] storedData;

        try {
            storedData = Base64.getDecoder().decode(encryptedValue);
        } catch (IllegalArgumentException exception) {
            throw new EncryptionOperationException(
                    "Encrypted data has an invalid encoding.",
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

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            SecretKeySpec secretKey =
                    new SecretKeySpec(getEncryptionKey(), ALGORITHM);

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
             * This also catches authentication failures caused by
             * tampering, a wrong key, or the wrong encryption purpose.
             */

            throw new EncryptionOperationException(
                    "Unable to decrypt sensitive notification data. "
                            + "The key may be incorrect or the data may "
                            + "have been modified.",
                    exception
            );
        }
    }

    private byte[] getEncryptionKey() {

        String encodedKey = properties.getKey();

        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "Notification encryption key is not configured."
            );
        }

        byte[] key;

        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Notification encryption key must be valid Base64.",
                    exception
            );
        }

        if (key.length != KEY_LENGTH_BYTES) {
            Arrays.fill(key, (byte) 0);

            throw new IllegalStateException(
                    "Notification encryption key must contain exactly "
                            + KEY_LENGTH_BYTES + " bytes."
            );
        }

        return key;
    }
}