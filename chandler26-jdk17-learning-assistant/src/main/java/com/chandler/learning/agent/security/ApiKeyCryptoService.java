package com.chandler.learning.agent.security;

import com.chandler.learning.agent.config.LearningSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ApiKeyCryptoService {

    private static final String PREFIX = "enc:v1:";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final LearningSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        if (plainText.startsWith(PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 加密失败", ex);
        }
    }

    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            return storedValue;
        }
        if (!storedValue.startsWith(PREFIX)) {
            return storedValue;
        }
        try {
            String payload = storedValue.substring(PREFIX.length());
            String[] parts = payload.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("API Key 密文格式错误");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 解密失败", ex);
        }
    }

    public String mask(String storedValue) {
        return maskPlain(decrypt(storedValue));
    }

    public boolean isEncrypted(String storedValue) {
        return StringUtils.hasText(storedValue) && storedValue.startsWith(PREFIX);
    }

    private SecretKeySpec keySpec() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(properties.getApiKeySecret().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 密钥初始化失败", ex);
        }
    }

    private String maskPlain(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 10) {
            return "****";
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public String fingerprint(String storedValue) {
        String plainText = decrypt(storedValue);
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plainText.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 指纹计算失败", ex);
        }
    }
}
