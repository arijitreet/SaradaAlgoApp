package com.sarada.trading.common.crypto;

import com.sarada.trading.common.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for broker tokens at rest.
 * Key comes from APP_CRYPTO_KEY (base64, 32 bytes) — never from config files.
 */
@Component
public class TokenEncryptor {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenEncryptor(AppProperties props) {
        byte[] raw = Base64.getDecoder().decode(props.security().cryptoKey());
        if (raw.length != 32) {
            throw new IllegalStateException("APP_CRYPTO_KEY must be 32 bytes base64-encoded");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("Token decryption failed", e);
        }
    }
}
