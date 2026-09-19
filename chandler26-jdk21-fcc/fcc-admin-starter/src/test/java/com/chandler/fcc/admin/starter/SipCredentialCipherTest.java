package com.chandler.fcc.admin.starter;

import com.chandler.fcc.admin.service.SipCredentialCipher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

/** 校验分机密文随机性、篡改检测及旧明文拒绝，不使用真实凭据。 */
class SipCredentialCipherTest {
    /** 同一口令加密结果不同，损坏密文与明文不能被接受。 */
    @Test void encryptsAndRejectsTampering() {
        var cipher = new SipCredentialCipher();
        byte[] testKey = new byte[32];
        new java.security.SecureRandom().nextBytes(testKey);
        ReflectionTestUtils.setField(cipher, "encodedKey", Base64.getEncoder().encodeToString(testKey));
        byte[] first = cipher.encrypt("test-only-secret");
        byte[] second = cipher.encrypt("test-only-secret");
        assertFalse(java.util.Arrays.equals(first, second));
        assertEquals("test-only-secret", cipher.decrypt(first));
        first[first.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(first));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("old-plaintext".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
