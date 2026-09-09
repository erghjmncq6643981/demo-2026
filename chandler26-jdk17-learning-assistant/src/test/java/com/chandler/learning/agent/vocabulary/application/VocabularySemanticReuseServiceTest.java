package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.vocabulary.domain.entity.*;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularySemanticGenerationLock;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularySemanticAssetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VocabularySemanticReuseServiceTest {
    private final Map<String, VocabularySemanticAsset> storage = new HashMap<>();
    private final VocabularySemanticAssetMapper mapper = mock(VocabularySemanticAssetMapper.class);
    private final VocabularySemanticGenerationLock lock = mock(VocabularySemanticGenerationLock.class);
    private final VocabularySemanticReuseService service = new VocabularySemanticReuseService(mapper, lock, new ObjectMapper());

    @BeforeEach
    void setup() {
        when(mapper.findByTerms(anyList())).thenAnswer(call -> {
            List<String> terms = call.getArgument(0);
            return terms.stream().map(storage::get).filter(Objects::nonNull).toList();
        });
        when(mapper.upsertBatch(anyList())).thenAnswer(call -> {
            List<VocabularySemanticAsset> values = call.getArgument(0);
            values.forEach(value -> storage.put(value.getNormalizedTerm(), value));
            return values.size();
        });
        when(lock.execute(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
    }

    @Test
    void reusesAcrossBooksAndRemapsRelatedIdsWithoutAnotherCall() {
        var first = entry(1, "bank");
        var related = entry(2, "money");
        service.resolve(job(10), List.of(first), Map.of(1L, first, 2L, related), false,
                cold -> List.of(analysis(1, "[2]")));
        var next = entry(101, "bank");
        var nextRelated = entry(102, "money");
        var result = service.resolve(job(20), List.of(next), Map.of(101L, next, 102L, nextRelated), false,
                cold -> { throw new AssertionError("已有词不应调用 AI"); });
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo("inherited");
        assertThat(result.get(0).getCatalogEntryId()).isEqualTo(101L);
        assertThat(result.get(0).getRelatedEntryIdsJson()).isEqualTo("[102]");
        assertThat(result.get(0).getDomainCode()).isEqualTo("finance");
        verify(mapper, times(1)).upsertBatch(anyList());
    }

    @Test
    void deduplicatesNormalizedTermsAndRetriesOnlyMissingResults() {
        var first = entry(1, " BANK ");
        var duplicate = entry(2, "bank");
        var missing = entry(3, "money");
        var entries = List.of(first, duplicate, missing);
        var dictionary = Map.of(1L, first, 2L, duplicate, 3L, missing);
        var partial = service.resolve(job(10), entries, dictionary, false, cold -> {
            assertThat(cold).extracting(VocabularyCatalogEntry::getId).containsExactly(1L, 3L);
            return List.of(analysis(1, "[]"));
        });
        assertThat(partial).extracting(VocabularyCatalogEntryAnalysis::getCatalogEntryId).containsExactly(1L, 2L);
        var retry = service.resolve(job(10), entries, dictionary, false, cold -> {
            assertThat(cold).extracting(VocabularyCatalogEntry::getId).containsExactly(3L);
            return List.of(analysis(3, "[]"));
        });
        assertThat(retry).hasSize(3);
    }

    @Test
    void rechecksAssetsAfterAcquiringLock() {
        var entry = entry(1, "bank");
        service.resolve(job(10), List.of(entry), Map.of(1L, entry), false, cold -> List.of(analysis(1, "[]")));
        var cached = storage.remove("bank");
        doAnswer(call -> {
            storage.put("bank", cached);
            return ((Supplier<?>) call.getArgument(0)).get();
        }).when(lock).execute(any());
        var result = service.resolve(job(20), List.of(entry), Map.of(1L, entry), false,
                cold -> { throw new AssertionError("等待锁期间已完成，不应重复调用"); });
        assertThat(result.get(0).getSource()).isEqualTo("inherited");
    }

    @Test
    void forceRefreshesOncePerJobAndPreservesAssetIdentityOnRetry() {
        var entry = entry(1, "bank");
        var calls = new AtomicInteger();
        java.util.function.Function<List<VocabularyCatalogEntry>, List<VocabularyCatalogEntryAnalysis>> generate = cold -> {
            calls.incrementAndGet();
            return List.of(analysis(1, "[]"));
        };
        service.resolve(job(10), List.of(entry), Map.of(1L, entry), false, generate);
        Long assetId = storage.get("bank").getId();
        service.resolve(job(20), List.of(entry), Map.of(1L, entry), true, generate);
        service.resolve(job(20), List.of(entry), Map.of(1L, entry), true, generate);
        assertThat(calls).hasValue(2);
        assertThat(storage.get("bank").getId()).isEqualTo(assetId);
    }

    @Test
    void failedForceDoesNotPretendOldResultIsNewSuccess() {
        var entry = entry(1, "bank");
        service.resolve(job(10), List.of(entry), Map.of(1L, entry), false, cold -> List.of(analysis(1, "[]")));
        assertThat(service.resolve(job(20), List.of(entry), Map.of(1L, entry), true, cold -> List.of())).isEmpty();
        assertThat(storage.get("bank").getSourceJobId()).isEqualTo(10L);
    }

    @Test
    void generatorFailureLeavesNoSuccessfulAsset() {
        var entry = entry(1, "bank");
        assertThatThrownBy(() -> service.resolve(job(10), List.of(entry), Map.of(1L, entry), false,
                cold -> { throw new IllegalStateException("provider unavailable"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage).isEmpty();
    }

    static VocabularyCatalogEntry entry(long id, String term) {
        var entry = new VocabularyCatalogEntry();
        entry.setId(id);
        entry.setNormalizedTerm(term);
        return entry;
    }

    static VocabularyCatalogAnalysisJob job(long id) {
        var job = new VocabularyCatalogAnalysisJob();
        job.setId(id);
        job.setUserId(1L);
        job.setCatalogId(id);
        job.setCatalogVersionId(id);
        job.setAnalysisVersion(1);
        return job;
    }

    static VocabularyCatalogEntryAnalysis analysis(long entryId, String relatedIds) {
        var result = new VocabularyCatalogEntryAnalysis();
        result.setCatalogEntryId(entryId);
        result.setPrimaryGroupCode("finance");
        result.setPrimaryGroupName("金融");
        result.setDomainCode("finance");
        result.setSubTopicCode("banking");
        result.setDifficultyLevel("easy");
        result.setConfidence(0.9);
        result.setStatus("ready");
        result.setTagsJson("[\"money\"]");
        result.setRelatedEntryIdsJson(relatedIds);
        return result;
    }
}
