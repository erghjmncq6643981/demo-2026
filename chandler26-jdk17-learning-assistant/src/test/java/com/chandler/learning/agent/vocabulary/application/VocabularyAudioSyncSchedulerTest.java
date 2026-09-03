package com.chandler.learning.agent.vocabulary.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("词汇发音定时同步与补全测试")
class VocabularyAudioSyncSchedulerTest {

    @Mock
    private VocabularyAudioService vocabularyAudioService;

    @InjectMocks
    private VocabularyAudioSyncScheduler scheduler;

    @Test
    @DisplayName("定时任务应扫描词汇库并补全缺省发音")
    void shouldSyncMissingAudio() {
        ReflectionTestUtils.setField(scheduler, "throttleMs", 0L);
        when(vocabularyAudioService.collectAllVocabularyTerms())
                .thenReturn(Set.of("apple", "banana"));
        when(vocabularyAudioService.syncEnsureAudio(eq("apple"))).thenReturn(2);
        when(vocabularyAudioService.syncEnsureAudio(eq("banana"))).thenReturn(0);

        VocabularyAudioSyncScheduler.AudioSyncResult result = scheduler.syncMissingAudio();

        assertThat(result.totalTerms()).isEqualTo(2);
        assertThat(result.missingTerms()).isEqualTo(1);
        assertThat(result.downloadedFiles()).isEqualTo(2);

        verify(vocabularyAudioService).syncEnsureAudio("apple");
        verify(vocabularyAudioService).syncEnsureAudio("banana");
    }
}
