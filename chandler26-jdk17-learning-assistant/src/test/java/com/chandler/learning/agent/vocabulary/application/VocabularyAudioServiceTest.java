package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyAudioConstants;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("词汇发音音频服务测试")
class VocabularyAudioServiceTest {

    private VocabularyAudioService audioService;

    @Mock
    private EnglishVocabularyStudyRecordMapper studyRecordMapper;

    @Mock
    private LearningWordbookEntryMapper wordbookEntryMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        audioService = new VocabularyAudioService(
                Executors.newSingleThreadExecutor(),
                studyRecordMapper,
                wordbookEntryMapper
        );
        ReflectionTestUtils.setField(audioService, "storagePath", tempDir.toString());
    }

    @Test
    @DisplayName("单词文件名规整化应去除空格、特殊字符并转小写")
    void shouldNormalizeAudioTerm() {
        assertThat(audioService.normalizeAudioTerm("  Technique! "))
                .isEqualTo("technique");
        assertThat(audioService.normalizeAudioTerm("State-of-the-Art"))
                .isEqualTo("state-of-the-art");
        assertThat(audioService.normalizeAudioTerm("let's"))
                .isEqualTo("let's");
        assertThat(audioService.normalizeAudioTerm(""))
                .isEmpty();
        assertThat(audioService.normalizeAudioTerm(null))
                .isEmpty();
    }

    @Test
    @DisplayName("本地已存在音频文件时应直接返回本地 Resource")
    void shouldReturnExistingLocalResource() throws IOException {
        Path usDir = tempDir.resolve("us");
        Files.createDirectories(usDir);
        Path audioFile = usDir.resolve("technique.mp3");
        byte[] dummyAudio = new byte[256];
        Files.write(audioFile, dummyAudio);

        Resource resource = audioService.resolveAudioResource("technique", VocabularyAudioConstants.VOICE_TYPE_US);
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(256);
        assertThat(audioService.hasValidAudio("technique", VocabularyAudioConstants.VOICE_TYPE_US)).isTrue();
        assertThat(audioService.hasValidAudio("technique", VocabularyAudioConstants.VOICE_TYPE_UK)).isFalse();
    }

    @Test
    @DisplayName("词典发音过滤应正确识别单字与二元短语，过滤 3 词及以上长短语、从句与前后缀片段")
    void shouldFilterIneligibleDictTerms() {
        // 合法单字与二元短语
        assertThat(audioService.isDownloadableDictTerm("technique")).isTrue();
        assertThat(audioService.isDownloadableDictTerm("apple")).isTrue();
        assertThat(audioService.isDownloadableDictTerm("ice cream")).isTrue();
        assertThat(audioService.isDownloadableDictTerm("look after")).isTrue();
        assertThat(audioService.isDownloadableDictTerm("state-of-the-art")).isTrue();
        assertThat(audioService.isDownloadableDictTerm("a")).isTrue();
        assertThat(audioService.isDownloadableDictTerm("I")).isTrue();

        // 3 词及以上长短语 / 句式（应过滤）
        assertThat(audioService.isDownloadableDictTerm("god forbid that")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("heaven forbid that")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("lord forbid that")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("accept  at face value")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("as well as")).isFalse();

        // 从句短语（以 that 等结尾）
        assertThat(audioService.isDownloadableDictTerm("forbid that")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("given that")).isFalse();

        // 词缀与特殊符号片段（应过滤）
        assertThat(audioService.isDownloadableDictTerm("-able")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("-sible")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("post-")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("'s")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("123")).isFalse();
        assertThat(audioService.isDownloadableDictTerm("")).isFalse();
        assertThat(audioService.isDownloadableDictTerm(null)).isFalse();
    }

    @Test
    @DisplayName("非词典词汇在本地无缓存时不应发起远程下载，直接返回 null")
    void shouldNotDownloadIneligibleTerms() {
        Resource res = audioService.resolveAudioResource("god forbid that", VocabularyAudioConstants.VOICE_TYPE_US);
        assertThat(res).isNull();

        int synced = audioService.syncEnsureAudio("accept  at face value");
        assertThat(synced).isEqualTo(0);
    }
}
