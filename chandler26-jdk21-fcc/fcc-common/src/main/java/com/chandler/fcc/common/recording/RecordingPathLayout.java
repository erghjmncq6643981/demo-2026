package com.chandler.fcc.common.recording;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 通话录音共享路径布局规则 (纯函数工具)
 * <p>
 * 统一 fcc-server 的「指令声明路径」与 fcc-admin 的「文件读取校验」两侧的路径算法，
 * 确保写入的绝对路径与后续读取的绝对路径完全一致。布局如下：
 * </p>
 * <pre>
 * {baseDir}/{yyyy}/{MM}/{dd}/{callId}.{format}
 * 例: /var/lib/fcc/recordings/2026/09/19/18...81.wav
 * </pre>
 *
 * <p>文件名直接使用业务 {@code call_id}：它由 FCC 生成并在转移、重试、多信道场景下保持稳定，
 * 天然满足唯一性且不含路径分隔符等不安全字符。</p>
 *
 * @author Chandler
 */
public final class RecordingPathLayout {

    /**
     * 录音业务唯一标识前缀
     */
    public static final String RECORDING_ID_PREFIX = "rec-";

    private RecordingPathLayout() {
    }

    /**
     * 由业务通话标识推导录音业务唯一标识 (recording_id)
     *
     * @param callId 业务通话标识，如 1234567890
     * @return 录音业务唯一标识；入参为空时返回 null
     */
    public static String recordingIdOf(String callId) {
        if (callId == null || callId.isBlank()) {
            return null;
        }
        String normalized = sanitizeSegment(callId);
        return normalized.startsWith(RECORDING_ID_PREFIX) ? normalized : RECORDING_ID_PREFIX + normalized;
    }

    /**
     * 计算录音文件的共享绝对路径
     *
     * @param props 录音存储配置
     * @param callId 业务通话标识
     * @param startedAt 录音开始时间 (决定按日期分层的子目录)
     * @return 跨 FreeSWITCH 与 FCC 服务一致的绝对路径字符串；入参不完整时返回 null
     */
    public static String buildAbsolutePath(RecordingStorageProperties props, String callId, LocalDateTime startedAt) {
        if (props == null || callId == null || callId.isBlank()) {
            return null;
        }
        LocalDateTime at = startedAt != null ? startedAt : LocalDateTime.now();
        String format = normalizeFormat(props.getFileFormat());
        String fileName = sanitizeSegment(callId) + "." + format;
        Path base = Paths.get(props.getBaseDir());
        Path subDir = subDirOf(props, at);
        return base.resolve(subDir).resolve(fileName).toString();
    }

    /**
     * 计算按日期分层的相对子目录 (如 2026/09/19)
     *
     * @param props 录音存储配置
     * @param at 录音开始时间
     * @return 相对子目录路径；格式非法时回退为 yyyy/MM/dd
     */
    public static Path subDirOf(RecordingStorageProperties props, LocalDateTime at) {
        String pattern = (props != null && props.getSubDirPattern() != null && !props.getSubDirPattern().isBlank())
                ? props.getSubDirPattern().trim()
                : "yyyy/MM/dd";
        LocalDateTime target = at != null ? at : LocalDateTime.now();
        String rendered;
        try {
            rendered = DateTimeFormatter.ofPattern(pattern).format(target);
        } catch (IllegalArgumentException ex) {
            rendered = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(target);
        }
        return Paths.get(rendered);
    }

    /**
     * 把数据库中的录音路径解析为可用于读取的绝对路径
     * <p>
     * 支持两种库内取值：绝对路径原样返回；相对路径则拼接到共享根目录之下。
     * http/https 等远端对象存储地址不参与本地文件解析，调用方需自行处理。
     * </p>
     *
     * @param props 录音存储配置
     * @param storedPath 数据库中存储的录音路径
     * @return 解析后的绝对路径；入参为空或为远端 URL 时返回 null
     */
    public static Path resolveStoredPath(RecordingStorageProperties props, String storedPath) {
        if (props == null || storedPath == null || storedPath.isBlank()) {
            return null;
        }
        String trimmed = storedPath.trim();
        if (isRemoteUrl(trimmed)) {
            return null;
        }
        Path candidate = Paths.get(trimmed);
        if (!candidate.isAbsolute()) {
            candidate = Paths.get(props.getBaseDir()).resolve(trimmed);
        }
        return candidate.normalize();
    }

    /**
     * 校验路径是否位于共享录音根目录之内
     *
     * @param props 录音存储配置
     * @param resolved 已解析的绝对路径
     * @return 位于共享目录内返回 true；配置放开越界限制时恒为 true
     */
    public static boolean isInsideBaseDir(RecordingStorageProperties props, Path resolved) {
        if (props == null || resolved == null) {
            return false;
        }
        if (props.isAllowOutsideBaseDir()) {
            return true;
        }
        Path base = Paths.get(props.getBaseDir()).toAbsolutePath().normalize();
        Path target = resolved.toAbsolutePath().normalize();
        return target.startsWith(base);
    }

    /**
     * 判断给定取值是否为远端对象存储地址
     *
     * @param value 待判断取值
     * @return http/https 开头返回 true
     */
    public static boolean isRemoteUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 提取录音文件的展示文件名 (含扩展名)
     *
     * @param resolved 录音文件绝对路径
     * @param fallbackName 路径解析失败时的兜底文件名
     * @return 用于 HTTP 下载的文件名
     */
    public static String fileNameOf(Path resolved, String fallbackName) {
        if (resolved != null && resolved.getFileName() != null) {
            return resolved.getFileName().toString();
        }
        return (fallbackName == null || fallbackName.isBlank()) ? "recording.wav" : fallbackName;
    }

    /**
     * 归一化封装格式
     */
    private static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "wav";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    /**
     * 清理用于文件名的片段，剔除路径分隔符与不安全字符
     */
    private static String sanitizeSegment(String value) {
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
