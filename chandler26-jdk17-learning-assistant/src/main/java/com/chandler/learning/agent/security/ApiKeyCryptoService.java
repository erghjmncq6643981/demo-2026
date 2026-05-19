package com.chandler.learning.agent.security;

import com.chandler.learning.agent.config.LearningSecurityProperties;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
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

/**
 * AI 模型 API Key 加密服务。
 * <p>
 * 数据库存储 AES-GCM 密文，业务调用时再解密；日志中只允许输出脱敏值或指纹。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyCryptoService {

    private final LearningSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        if (plainText.startsWith(LearningConstants.Crypto.API_KEY_PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[LearningConstants.Crypto.API_KEY_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(LearningConstants.Crypto.API_KEY_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(LearningConstants.Crypto.API_KEY_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return LearningConstants.Crypto.API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.API_KEY_CRYPTO_FAILED,
                    "API Key 加密失败",
                    ex);
        }
    }

    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            return storedValue;
        }
        if (!storedValue.startsWith(LearningConstants.Crypto.API_KEY_PREFIX)) {
            return storedValue;
        }
        try {
            String payload = storedValue.substring(LearningConstants.Crypto.API_KEY_PREFIX.length());
            String[] parts = payload.split("\\.");
            if (parts.length != LearningConstants.Crypto.API_KEY_CIPHER_PART_COUNT) {
                throw LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.API_KEY_CIPHER_INVALID,
                        "API Key 密文格式错误");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[LearningConstants.ZERO]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[LearningConstants.FIRST_SEQUENCE]);
            Cipher cipher = Cipher.getInstance(LearningConstants.Crypto.API_KEY_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(LearningConstants.Crypto.API_KEY_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.API_KEY_CRYPTO_FAILED,
                    "API Key 解密失败",
                    ex);
        }
    }

    public String mask(String storedValue) {
        return maskPlain(decrypt(storedValue));
    }

    public boolean isEncrypted(String storedValue) {
        return StringUtils.hasText(storedValue) && storedValue.startsWith(LearningConstants.Crypto.API_KEY_PREFIX);
    }

    private SecretKeySpec keySpec() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(properties.getApiKeySecret().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.API_KEY_CRYPTO_FAILED,
                    "API Key 密钥初始化失败",
                    ex);
        }
    }

    private String maskPlain(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= LearningConstants.Crypto.API_KEY_MASK_THRESHOLD) {
            return "****";
        }
        return apiKey.substring(LearningConstants.ZERO, LearningConstants.Crypto.API_KEY_MASK_PREFIX_LENGTH)
                + "****"
                + apiKey.substring(apiKey.length() - LearningConstants.Crypto.API_KEY_MASK_SUFFIX_LENGTH);
    }

    public String fingerprint(String storedValue) {
        String plainText = decrypt(storedValue);
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(plainText.getBytes(StandardCharsets.UTF_8)))
                    .substring(LearningConstants.ZERO, LearningConstants.Crypto.API_KEY_FINGERPRINT_LENGTH);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.API_KEY_CRYPTO_FAILED,
                    "API Key 指纹计算失败",
                    ex);
        }
    }
}
