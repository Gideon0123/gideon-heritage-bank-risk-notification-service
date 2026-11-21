package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class InboxPayloadEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int AUTHENTICATION_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${notification.inbox.encryption-key}")
    private String encodedEncryptionKey;

    private SecretKeySpec secretKey;

    @PostConstruct
    public void initialize() {

        try {
            byte[] key = Base64.getDecoder().decode(encodedEncryptionKey);

            if (key.length != KEY_LENGTH) {
                throw new IllegalStateException(
                        "Notification inbox encryption key must contain exactly 32 bytes."
                );
            }

            this.secretKey = new SecretKeySpec(key, "AES");

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "Notification inbox encryption key must be valid Base64.",
                    exception
            );
        }
    }

    public String encrypt(String plainText) {

        try {
            byte[] nonce = new byte[NONCE_LENGTH];

            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH, nonce)
            );

            byte[] encryptedBytes = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            /*
             * Store nonce + ciphertext + authentication tag.
             *
             * The nonce is not secret. It is required for decryption.
             */
            ByteBuffer buffer = ByteBuffer.allocate(
                    nonce.length + encryptedBytes.length
            );

            buffer.put(nonce);
            buffer.put(encryptedBytes);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not encrypt notification inbox payload.",
                    exception
            );
        }
    }

    public String decrypt(String encryptedPayload) {

        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPayload);

            if (encryptedBytes.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Encrypted inbox payload is invalid.");
            }

            ByteBuffer buffer = ByteBuffer.wrap(encryptedBytes);

            byte[] nonce = new byte[NONCE_LENGTH];

            buffer.get(nonce);

            byte[] cipherText = new byte[buffer.remaining()];

            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(
                            AUTHENTICATION_TAG_LENGTH,
                            nonce
                    )
            );

            byte[] plainText = cipher.doFinal(cipherText);

            return new String(
                    plainText,
                    StandardCharsets.UTF_8
            );

        } catch (Exception exception) {

            /* Do not log the encrypted payload or decrypted content. */
            log.error(
                    "Could not decrypt notification inbox payload. failureType={}",
                    exception.getClass().getSimpleName()
            );

            throw new IllegalStateException("Could not decrypt notification inbox payload.");
        }
    }
}