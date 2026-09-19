package com.chandler.fcc.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 使用部署密钥加密分机凭据；不兼容历史明文，旧数据必须重新开通或轮换。 */
@Service
public class SipCredentialCipher {
    private static final byte[] FORMAT = "FCC1".getBytes(StandardCharsets.US_ASCII);
    @Value("${fcc.sip.encryption-key:}")
    private String encodedKey;

    /** 加密一个独立分机口令，使用随机 GCM nonce。
     * @param secret 明文口令，仅短暂驻留内存
     * @return 带格式头和 nonce 的密文
     */
    public byte[] encrypt(String secret) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(FORMAT);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(4 + 12 + encrypted.length).put(FORMAT).put(nonce).put(encrypted).array();
        } catch (Exception e) { throw new IllegalStateException("SIP 凭据加密不可用，请检查部署密钥"); }
    }

    /** 解密已授权坐席的凭据，拒绝明文或损坏密文。
     * @param value 密文
     * @return 本人注册口令
     */
    public String decrypt(byte[] value) {
        if (value == null || value.length < 32 || !Arrays.equals(FORMAT, Arrays.copyOf(value, 4)))
            throw new IllegalStateException("分机凭据格式不受支持，请重新开通或轮换");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(value, 4, 16)));
            cipher.updateAAD(FORMAT);
            return new String(cipher.doFinal(Arrays.copyOfRange(value, 16, value.length)), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("SIP 凭据无法解密，请检查部署密钥或轮换凭据"); }
    }

    /** 加载由部署注入的 256 位密钥，禁止使用默认值。
     * @return AES 密钥
     */
    private SecretKeySpec key() {
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) throw new IllegalArgumentException("SIP 密钥必须为 32 字节 Base64");
        return new SecretKeySpec(bytes, "AES");
    }
}
