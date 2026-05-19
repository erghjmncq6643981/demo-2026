package com.chandler.learning.agent.security;

import com.chandler.learning.agent.config.LearningSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final LearningSecurityProperties properties;

    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(1, properties.getJwtExpireDays()) * 86400L);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", properties.getJwtIssuer());
        payload.put("sub", String.valueOf(userId));
        payload.put("username", username);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String unsigned = base64Url(JsonSupport.toJson(header)) + "." + base64Url(JsonSupport.toJson(payload));
        return unsigned + "." + sign(unsigned);
    }

    public JwtClaims parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("JWT 为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT 格式错误");
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            throw new IllegalArgumentException("JWT 签名无效");
        }

        Map<String, Object> payload = JsonSupport.fromJson(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        if (!properties.getJwtIssuer().equals(String.valueOf(payload.get("iss")))) {
            throw new IllegalArgumentException("JWT issuer 无效");
        }
        long expiresAt = readLong(payload.get("exp"));
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("JWT 已过期");
        }
        Long userId = readLong(payload.get("sub"));
        String username = String.valueOf(payload.getOrDefault("username", ""));
        LocalDateTime expiredTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresAt), ZoneId.systemDefault());
        return new JwtClaims(userId, username, expiredTime);
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
