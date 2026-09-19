package com.chandler.fcc.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 控制面账号口令哈希工具 (PBKDF2-HMAC-SHA256)
 * <p>
 * 用于系统管理员 (fcc_admin_user) 与坐席 (fcc_agent) 的登录口令加盐派生。
 * 采用行业标准的 {@code PBKDF2WithHmacSHA256} 慢哈希算法，杜绝明文与可逆加密存储。
 * </p>
 *
 * <p>存储格式为单字段自描述串 {@code pbkdf2-sha256$迭代次数$盐Hex$哈希Hex}：</p>
 * <pre>
 * pbkdf2-sha256$120000$58e0a9ac0999f1a1d4de31a5ab8e0f3a$a5c1...（64 位 Hex）
 * </pre>
 * <p>
 * 盐值随每次设置口令随机生成，因此同一口令每次派生结果不同；
 * 校验时从串内解析算法与盐，无需额外的数据库字段。
 * </p>
 *
 * @author Chandler
 */
public final class PasswordHasher {

    /**
     * 派生算法标识 (同时作为存储格式前缀)
     */
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * 存储格式前缀
     */
    public static final String FORMAT_PREFIX = "pbkdf2-sha256";

    /**
     * 迭代次数 (口令派生的计算成本，推荐 10 万次以上)
     */
    public static final int DEFAULT_ITERATIONS = 120_000;

    /**
     * 盐值字节长度
     */
    private static final int SALT_BYTES = 16;

    /**
     * 派生密钥字节长度
     */
    private static final int HASH_BYTES = 32;

    /**
     * 分段索引: 迭代次数
     */
    private static final int IDX_ITERATIONS = 1;

    /**
     * 分段索引: 盐值
     */
    private static final int IDX_SALT = 2;

    /**
     * 分段索引: 派生哈希
     */
    private static final int IDX_HASH = 3;

    /**
     * 生成初始随机口令使用的高熵可选字符集 (去除了 0/O/1/l/I 等易混淆字符)
     */
    private static final char[] PASSWORD_ALPHABET =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * 对明文口令进行加盐哈希派生
     *
     * @param rawPassword 明文口令
     * @return 自描述存储串 {@code pbkdf2-sha256$iterations$saltHex$hashHex}
     * @throws IllegalArgumentException 明文口令为空时抛出
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("口令不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(rawPassword.toCharArray(), salt, DEFAULT_ITERATIONS, HASH_BYTES);
        return FORMAT_PREFIX + "$" + DEFAULT_ITERATIONS + "$" + toHex(salt) + "$" + toHex(derived);
    }

    /**
     * 校验明文口令是否与已存储的哈希串匹配 (恒定时间比较，防时序侧信道)
     *
     * @param rawPassword  待校验明文口令
     * @param storedHashed 数据库中存储的哈希串
     * @return 匹配返回 true；口令为空、存储串为空或格式非法时返回 false
     */
    public static boolean verify(String rawPassword, String storedHashed) {
        if (rawPassword == null || rawPassword.isEmpty()
                || storedHashed == null || storedHashed.isBlank()) {
            return false;
        }
        String[] parts = storedHashed.split("\\$");
        if (parts.length != 4 || !FORMAT_PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[IDX_ITERATIONS]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = fromHex(parts[IDX_SALT]);
            byte[] expected = fromHex(parts[IDX_HASH]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(rawPassword.toCharArray(), salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            // 记录格式损坏或含非 Hex 字符，按校验失败处理，不向外抛出
            return false;
        }
    }

    /**
     * 判断给定字符串是否为本工具生成的口令哈希格式
     *
     * @param candidate 待判断字符串
     * @return 符合格式返回 true
     */
    public static boolean looksLikeHash(String candidate) {
        return candidate != null && candidate.startsWith(FORMAT_PREFIX + "$");
    }

    /**
     * 生成指定长度的高熵随机初始口令
     * <p>
     * 用于新增账号时未指定口令的场景，替代任何形式的统一默认口令。
     * </p>
     *
     * @param length 口令长度，小于 8 时按 8 处理
     * @return 随机口令明文 (仅返回一次，调用方须提示用户及时修改)
     */
    public static String generateInitialPassword(int length) {
        int actual = Math.max(length, 8);
        StringBuilder sb = new StringBuilder(actual);
        for (int i = 0; i < actual; i++) {
            sb.append(PASSWORD_ALPHABET[SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * 执行 PBKDF2 派生
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("当前 JVM 不支持口令派生算法: " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * 字节数组转小写十六进制字符串
     */
    private static String toHex(byte[] value) {
        StringBuilder sb = new StringBuilder(value.length * 2);
        for (byte b : value) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转字节数组
     */
    private static byte[] fromHex(String hex) {
        String normalized = hex.trim();
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("非法的十六进制长度");
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(normalized.charAt(2 * i), 16);
            int lo = Character.digit(normalized.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("非法的十六进制字符");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /**
     * 以 UTF-8 字节计算 SHA-256 摘要 (十六进制小写)
     * <p>
     * 供需要口令指纹或完整性校验的场景使用。
     * </p>
     *
     * @param text 原始文本
     * @return 64 位十六进制摘要
     */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 生成 Base64 URL 安全的随机标识串
     *
     * @param bytes 随机字节长度
     * @return URL 安全 Base64 串
     */
    public static String randomToken(int bytes) {
        byte[] buffer = new byte[Math.max(bytes, 8)];
        SECURE_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
