package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyAudioConstants;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 英语词汇发音音频本地持久化与缓存服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyAudioService {

    @Value("${learning.audio.storage-path:./data/audio/}")
    private String storagePath;

    @Qualifier("aiTaskExecutor")
    private final Executor aiTaskExecutor;

    private final EnglishVocabularyStudyRecordMapper studyRecordMapper;
    private final LearningWordbookEntryMapper wordbookEntryMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(VocabularyAudioConstants.DOWNLOAD_TIMEOUT_SECONDS))
            .build();

    private final ConcurrentHashMap<String, Object> downloadLocks = new ConcurrentHashMap<>();

    /**
     * 规范化音频文件名（转小写、去除特殊字符）。
     */
    public String normalizeAudioTerm(String term) {
        if (!StringUtils.hasText(term)) {
            return "";
        }
        return term.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zA-Z0-9_\\-\\s']", "")
                .trim();
    }

    /**
     * 解析并获取指定单词和口音的发音音频文件资源。
     * <p>
     * 若本地磁盘已存在则直接返回；若未命中则从远程音源同步下载并原子落盘。
     *
     * @param rawTerm   单词（如 technique）
     * @param voiceType 口音类型（us: 美音, uk: 英音）
     * @return 音频文件 Resource，失败或无效时返回 null
     */
    public Resource resolveAudioResource(String rawTerm, String voiceType) {
        String cleanTerm = normalizeAudioTerm(rawTerm);
        if (!StringUtils.hasText(cleanTerm)) {
            return null;
        }
        String type = VocabularyAudioConstants.VOICE_TYPE_UK.equalsIgnoreCase(voiceType)
                ? VocabularyAudioConstants.VOICE_TYPE_UK
                : VocabularyAudioConstants.VOICE_TYPE_US;

        Path dir = Paths.get(storagePath, type);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException ex) {
            log.error("创建音频本地存储目录失败 path={}: {}", dir, ex.getMessage());
            return null;
        }

        Path targetFile = dir.resolve(cleanTerm + VocabularyAudioConstants.DEFAULT_AUDIO_EXTENSION);
        if (Files.exists(targetFile)) {
            try {
                if (Files.size(targetFile) >= VocabularyAudioConstants.MIN_VALID_AUDIO_BYTES) {
                    return new FileSystemResource(targetFile);
                }
            } catch (IOException ignored) {
            }
        }

        // 同步下载并使用细粒度锁防重入
        String lockKey = type + ":" + cleanTerm;
        Object lock = downloadLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            try {
                if (Files.exists(targetFile) && Files.size(targetFile) >= VocabularyAudioConstants.MIN_VALID_AUDIO_BYTES) {
                    return new FileSystemResource(targetFile);
                }
                boolean downloaded = downloadAudio(cleanTerm, type, targetFile);
                if (downloaded && Files.exists(targetFile)) {
                    return new FileSystemResource(targetFile);
                }
            } catch (Exception ex) {
                log.warn("获取/下载发音音频异常 term={} type={}: {}", cleanTerm, type, ex.getMessage());
            } finally {
                downloadLocks.remove(lockKey);
            }
        }
        return null;
    }

    /**
     * 异步静默预热指定单词的美音与英音。
     */
    public void prefetchAudio(String rawTerm) {
        String cleanTerm = normalizeAudioTerm(rawTerm);
        if (!StringUtils.hasText(cleanTerm)) {
            return;
        }
        aiTaskExecutor.execute(() -> {
            try {
                resolveAudioResource(cleanTerm, VocabularyAudioConstants.VOICE_TYPE_US);
                resolveAudioResource(cleanTerm, VocabularyAudioConstants.VOICE_TYPE_UK);
            } catch (Exception ex) {
                log.debug("异步预热音频跳过 term={}: {}", cleanTerm, ex.getMessage());
            }
        });
    }

    /**
     * 判断本地是否已存在有效的单词发音音频。
     */
    public boolean hasValidAudio(String rawTerm, String voiceType) {
        String cleanTerm = normalizeAudioTerm(rawTerm);
        if (!StringUtils.hasText(cleanTerm)) {
            return false;
        }
        String type = VocabularyAudioConstants.VOICE_TYPE_UK.equalsIgnoreCase(voiceType)
                ? VocabularyAudioConstants.VOICE_TYPE_UK
                : VocabularyAudioConstants.VOICE_TYPE_US;
        Path targetFile = Paths.get(storagePath, type, cleanTerm + VocabularyAudioConstants.DEFAULT_AUDIO_EXTENSION);
        try {
            return Files.exists(targetFile) && Files.size(targetFile) >= VocabularyAudioConstants.MIN_VALID_AUDIO_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查并确保指定单词的美音与英音均已落盘，若缺省则自动从远程词典下载。
     *
     * @param rawTerm 单词
     * @return 本次新下载成功的音频文件数量（0~2）
     */
    public int syncEnsureAudio(String rawTerm) {
        String cleanTerm = normalizeAudioTerm(rawTerm);
        if (!StringUtils.hasText(cleanTerm)) {
            return 0;
        }
        int downloaded = 0;
        if (!hasValidAudio(cleanTerm, VocabularyAudioConstants.VOICE_TYPE_US)) {
            if (resolveAudioResource(cleanTerm, VocabularyAudioConstants.VOICE_TYPE_US) != null) {
                downloaded++;
            }
        }
        if (!hasValidAudio(cleanTerm, VocabularyAudioConstants.VOICE_TYPE_UK)) {
            if (resolveAudioResource(cleanTerm, VocabularyAudioConstants.VOICE_TYPE_UK) != null) {
                downloaded++;
            }
        }
        return downloaded;
    }

    /**
     * 分页扫描词本表与词卡学习记录表中的所有有效词汇，返回去重后的归一化词汇集合。
     */
    public Set<String> collectAllVocabularyTerms() {
        Set<String> allTerms = new LinkedHashSet<>();
        long pageSize = 500;

        // 1. 从个人单词本词条表批量扫描
        long current = 1;
        while (true) {
            Page<LearningWordbookEntry> page = new Page<>(current, pageSize);
            QueryWrapper<LearningWordbookEntry> qw = new QueryWrapper<>();
            qw.select("DISTINCT normalized_term");
            qw.isNotNull("normalized_term");
            qw.ne("normalized_term", "");
            IPage<LearningWordbookEntry> result = wordbookEntryMapper.selectPage(page, qw);
            if (result == null || result.getRecords().isEmpty()) {
                break;
            }
            for (LearningWordbookEntry entry : result.getRecords()) {
                if (StringUtils.hasText(entry.getNormalizedTerm())) {
                    String clean = normalizeAudioTerm(entry.getNormalizedTerm());
                    if (StringUtils.hasText(clean)) {
                        allTerms.add(clean);
                    }
                }
            }
            if (current >= result.getPages()) {
                break;
            }
            current++;
        }

        // 2. 从 AI 学习记录缓存表批量扫描
        current = 1;
        while (true) {
            Page<EnglishVocabularyStudyRecord> page = new Page<>(current, pageSize);
            QueryWrapper<EnglishVocabularyStudyRecord> qw = new QueryWrapper<>();
            qw.select("DISTINCT normalized_term");
            qw.isNotNull("normalized_term");
            qw.ne("normalized_term", "");
            IPage<EnglishVocabularyStudyRecord> result = studyRecordMapper.selectPage(page, qw);
            if (result == null || result.getRecords().isEmpty()) {
                break;
            }
            for (EnglishVocabularyStudyRecord record : result.getRecords()) {
                if (StringUtils.hasText(record.getNormalizedTerm())) {
                    String clean = normalizeAudioTerm(record.getNormalizedTerm());
                    if (StringUtils.hasText(clean)) {
                        allTerms.add(clean);
                    }
                }
            }
            if (current >= result.getPages()) {
                break;
            }
            current++;
        }

        allTerms.remove("");
        return allTerms;
    }

    private boolean downloadAudio(String term, String voiceType, Path targetFile) {
        int youdaoType = VocabularyAudioConstants.VOICE_TYPE_UK.equals(voiceType)
                ? VocabularyAudioConstants.YOUDAO_TYPE_UK
                : VocabularyAudioConstants.YOUDAO_TYPE_US;
        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);
        List<String> candidateUrls = List.of(
                String.format("https://dict.youdao.com/dictvoice?audio=%s&type=%d", encodedTerm, youdaoType),
                String.format("http://dict.youdao.com/dictvoice?audio=%s&type=%d", encodedTerm, youdaoType)
        );

        for (String url : candidateUrls) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(VocabularyAudioConstants.DOWNLOAD_TIMEOUT_SECONDS))
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                        .header("Referer", "https://dict.youdao.com/")
                        .header("Accept", "*/*")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200 && response.body() != null && response.body().length >= VocabularyAudioConstants.MIN_VALID_AUDIO_BYTES) {
                    Path tmpFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".tmp." + System.currentTimeMillis());
                    Files.write(tmpFile, response.body());
                    Files.move(tmpFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    log.info("单词发音音频已成功下载并持久化至本地: {} ({} bytes)", targetFile.toAbsolutePath(), response.body().length);
                    return true;
                }
            } catch (Exception ex) {
                log.debug("从音源下载音频重试 url={} error={}", url, ex.getMessage());
            }
        }
        log.warn("下载远程音频失败 term={} type={}", term, voiceType);
        return false;
    }
}
