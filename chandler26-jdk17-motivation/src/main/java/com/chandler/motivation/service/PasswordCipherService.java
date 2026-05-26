package com.chandler.motivation.service;

import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.config.MotivationSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 孩子子账户密码的可查看副本加密服务。
 * <p>
 * 登录仍使用不可逆 hash，本服务只服务于家长查看/重置孩子子账户密码的业务入口。
 */
@Service
@RequiredArgsConstructor
public class PasswordCipherService {

    private static final String PREFIX = "aesgcm$";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final MotivationSecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "$"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new MotivationException("PASSWORD_CIPHER_FAILED", "孩子密码加密失败");
        }
    }

    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            throw new MotivationException("CHILD_PASSWORD_NOT_VIEWABLE", "当前孩子账号没有可查看密码，请先修改一次密码");
        }
        if (!storedValue.startsWith(PREFIX)) {
            throw new MotivationException("CHILD_PASSWORD_NOT_VIEWABLE", "当前孩子账号没有可查看密码，请先修改一次密码");
        }
        try {
            String[] parts = storedValue.substring(PREFIX.length()).split("\\$");
            if (parts.length != 2) {
                throw new MotivationException("CHILD_PASSWORD_NOT_VIEWABLE", "当前孩子账号没有可查看密码，请先修改一次密码");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (MotivationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MotivationException("CHILD_PASSWORD_NOT_VIEWABLE", "当前孩子账号没有可查看密码，请先修改一次密码");
        }
    }

    private SecretKeySpec keySpec() {
        String secret = StringUtils.hasText(securityProperties.getPasswordCipherSecret())
                ? securityProperties.getPasswordCipherSecret()
                : securityProperties.getJwtSecret();
        if (!StringUtils.hasText(secret) || secret.length() < 16) {
            throw new MotivationException("PASSWORD_CIPHER_SECRET_INVALID", "密码加密密钥长度不足");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new MotivationException("PASSWORD_CIPHER_SECRET_INVALID", "密码加密密钥不可用");
        }
    }
}
