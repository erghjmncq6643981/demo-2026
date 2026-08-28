package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.vocabulary.domain.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.infrastructure.LearningWordProgressMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearningWordProgressServiceTest {

    @Test
    void insertsMissingProgressInBoundedBatchChunks() {
        LearningWordProgressMapper mapper = mock(LearningWordProgressMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        List<Integer> batchSizes = new ArrayList<>();
        when(mapper.insertBatch(any())).thenAnswer(invocation -> {
            batchSizes.add(invocation.<List<LearningWordProgress>>getArgument(0).size());
            return batchSizes.get(batchSizes.size() - 1);
        });

        LearningWordProgressService service = new LearningWordProgressService(mapper);
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < 450; i++) {
            terms.add("term-" + i);
        }

        var result = service.getOrCreateAll(900L, terms);

        assertThat(result).hasSize(450);
        assertThat(batchSizes).containsExactly(200, 200, 50);
        verify(mapper, times(3)).insertBatch(any());
    }

    @Test
    void recordsArticleExposureWithOneBatchUpdate() {
        LearningWordProgressMapper mapper = mock(LearningWordProgressMapper.class);
        LearningWordProgress first = progress(1L, "abandon");
        LearningWordProgress second = progress(2L, "airport");
        when(mapper.selectList(any())).thenReturn(List.of(first, second));

        LearningWordProgressService service = new LearningWordProgressService(mapper);
        service.recordArticleExposures(900L, List.of("abandon", "airport"));

        verify(mapper, never()).insertBatch(any());
        verify(mapper, times(1)).updateBatch(any());
        assertThat(first.getExposureCount()).isEqualTo(1);
        assertThat(second.getExposureCount()).isEqualTo(1);
    }

    @Test
    void updatesArticleExposuresInBoundedBatchChunks() {
        LearningWordProgressMapper mapper = mock(LearningWordProgressMapper.class);
        List<LearningWordProgress> progresses = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < 450; i++) {
            String term = "term-" + i;
            progresses.add(progress((long) i + 1, term));
            terms.add(term);
        }
        when(mapper.selectList(any())).thenReturn(progresses);
        List<Integer> batchSizes = new ArrayList<>();
        when(mapper.updateBatch(any())).thenAnswer(invocation -> {
            int size = invocation.<List<LearningWordProgress>>getArgument(0).size();
            batchSizes.add(size);
            return size;
        });

        LearningWordProgressService service = new LearningWordProgressService(mapper);
        service.recordArticleExposures(900L, terms);

        assertThat(batchSizes).containsExactly(200, 200, 50);
        verify(mapper, times(3)).updateBatch(any());
    }

    @Test
    void ignoresBlankArticleExposureWithoutIssuingSql() {
        LearningWordProgressMapper mapper = mock(LearningWordProgressMapper.class);
        LearningWordProgressService service = new LearningWordProgressService(mapper);

        service.recordArticleExposures(900L, List.of(" ", "\t"));

        verifyNoInteractions(mapper);
    }

    private LearningWordProgress progress(Long id, String term) {
        LearningWordProgress progress = new LearningWordProgress();
        progress.setId(id);
        progress.setUserId(900L);
        progress.setTerm(term);
        progress.setNormalizedTerm(term);
        progress.setLearningState("unseen");
        progress.setExposureCount(0);
        return progress;
    }
}
