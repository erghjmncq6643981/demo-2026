package com.chandler.motivation.security;

import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.config.MotivationSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final MotivationSecurityProperties properties;

    public JwtTokenService(MotivationSecurityProperties properties) {
        this.properties = properties;
    }

    public String createToken(Long userId, String username) {
        long expiredAt = Instant.now().plusSeconds(properties.getTokenExpireHours() * 3600).getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + userId + "\",\"username\":\"" + escape(username) + "\",\"exp\":" + expiredAt + "}");
        String unsigned = header + "." + payload;
        return unsigned + "." + sign(unsigned);
    }

    public JwtClaims parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new MotivationException("AUTH_REQUIRED", "请先登录");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new MotivationException("AUTH_INVALID", "登录状态无效");
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!sign(unsigned).equals(parts[2])) {
            throw new MotivationException("AUTH_INVALID", "登录状态无效");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Long userId = Long.valueOf(readJsonValue(payload, "sub"));
        String username = readJsonValue(payload, "username");
        long expiredAt = Long.parseLong(readJsonValue(payload, "exp"));
        if (Instant.now().getEpochSecond() > expiredAt) {
            throw new MotivationException("AUTH_EXPIRED", "登录已过期，请重新登录");
        }
        return new JwtClaims(userId, username, LocalDateTime.ofInstant(Instant.ofEpochSecond(expiredAt), ZoneId.systemDefault()));
    }

    private String readJsonValue(String json, String key) {
        String quotedKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(quotedKey);
        if (keyIndex < 0) {
            throw new MotivationException("AUTH_INVALID", "登录状态无效");
        }
        int valueStart = keyIndex + quotedKey.length();
        if (json.charAt(valueStart) == '"') {
            int start = valueStart + 1;
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        }
        int end = json.indexOf(',', valueStart);
        if (end < 0) {
            end = json.indexOf('}', valueStart);
        }
        return json.substring(valueStart, end).trim();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MotivationException("AUTH_SIGN_FAILED", "令牌签名失败");
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
