package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.config.speech.AliyunNlsProperties;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("场景文章音频与分块切片服务测试")
class SceneArticleAudioServiceTest {

    private SceneArticleAudioService audioService;
    private LearningPlanUnitMapper unitMapper;
    private LearningSceneMaterialMapper materialMapper;
    private AliyunNlsProperties nlsProperties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        unitMapper = Mockito.mock(LearningPlanUnitMapper.class);
        materialMapper = Mockito.mock(LearningSceneMaterialMapper.class);
        nlsProperties = new AliyunNlsProperties();
        nlsProperties.setMaxChunkLength(200);

        audioService = new SceneArticleAudioService(unitMapper, materialMapper, nlsProperties);
        ReflectionTestUtils.setField(audioService, "storagePath", tempDir.toString());
    }

    @Test
    @DisplayName("文本清洗应彻底去除 Markdown 语法、标签与列表符号")
    void shouldCleanMarkdownSyntax() {
        String raw = "# Chapter 1: The Dining Hall\n\n" +
                "The **cafeteria** at our university offers a `wide` variety of cuisines. " +
                "*Students* can choose from **authentic** local dishes.\n" +
                "- Option A: Noodles\n" +
                "- Option B: Rice<br>&nbsp;\n";

        String cleaned = audioService.cleanTtsText(raw);
        assertThat(cleaned).doesNotContain("#", "**", "*", "`", "<br>", "&nbsp;", "- Option");
        assertThat(cleaned).contains("The cafeteria at our university offers a wide variety of cuisines.");
    }

    @Test
    @DisplayName("智能分句分块切片应按标点断句且各块不超过字符上限")
    void shouldSplitIntoChunksUnderLimit() {
        String article = "In a bustling university dining hall, students gather every noon to share hearty meals and exchange daily stories. " +
                "The rich aromas of freshly baked bread, savory braised dishes, and steaming vegetable soups fill the welcoming atmosphere. " +
                "Friends often sit together by the sunlit windows, discussing upcoming coursework, weekend plans, and campus adventures. " +
                "The diverse culinary options cater to everyone, creating a vibrant hub of campus social life.";

        List<String> chunks = audioService.splitIntoChunks(article, 180);
        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(180);
        }
        String combined = String.join(" ", chunks);
        assertThat(combined).contains("In a bustling university dining hall");
        assertThat(combined).contains("campus social life.");
    }

    @Test
    @DisplayName("本地已生成音频文件时应直接读取返回")
    void shouldReturnExistingAudioFile() throws IOException {
        Path sceneDir = tempDir.resolve("scene");
        Files.createDirectories(sceneDir);
        Path target = sceneDir.resolve("5001.mp3");
        Files.write(target, new byte[512]);

        Resource resource = audioService.getExistingSceneAudio(5001L);
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(512);
    }
}
