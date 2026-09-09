package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCatalogAnalysisConstants;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogAnalysisJob;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntryAnalysis;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularySemanticAsset;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularySemanticGenerationLock;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularySemanticAssetMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 全局词汇语义复用：只为缺失的标准词调用生成器，词本结果保留独立历史快照。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VocabularySemanticReuseService {
    private final VocabularySemanticAssetMapper assetMapper;
    private final VocabularySemanticGenerationLock generationLock;
    private final ObjectMapper objectMapper;

    /** 批量查重，冷词在数据库互斥内二次查重，部分成功先沉淀资产再返回词本快照。 */
    public List<VocabularyCatalogEntryAnalysis> resolve(
            VocabularyCatalogAnalysisJob job, List<VocabularyCatalogEntry> entries,
            Map<Long, VocabularyCatalogEntry> catalogEntries, boolean force,
            Function<List<VocabularyCatalogEntry>, List<VocabularyCatalogEntryAnalysis>> generator) {
        if (entries.isEmpty()) return List.of();
        Map<String, VocabularyCatalogEntry> unique = entries.stream().collect(Collectors.toMap(
                this::term, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        Map<String, VocabularySemanticAsset> assets = load(unique.keySet().stream().toList());
        if (missing(unique, assets, job.getId(), force).isEmpty()) {
            return snapshots(job, entries, catalogEntries, assets, List.of());
        }
        return generationLock.execute(() -> {
            // 另一个词本可能在等待锁期间完成分析，因此持锁后必须重新批量读取。
            Map<String, VocabularySemanticAsset> latest = load(unique.keySet().stream().toList());
            List<VocabularyCatalogEntry> cold = missing(unique, latest, job.getId(), force);
            List<VocabularySemanticAsset> generated = new ArrayList<>();
            if (!cold.isEmpty()) {
                Map<Long, VocabularyCatalogEntry> coldById = cold.stream().collect(
                        Collectors.toMap(VocabularyCatalogEntry::getId, Function.identity()));
                for (VocabularyCatalogEntryAnalysis analysis : generator.apply(cold)) {
                    VocabularyCatalogEntry entry = coldById.get(analysis.getCatalogEntryId());
                    if (entry != null) {
                        VocabularySemanticAsset asset = toAsset(job, entry, analysis, catalogEntries);
                        VocabularySemanticAsset previous = latest.get(term(entry));
                        if (previous != null) asset.setId(previous.getId());
                        generated.add(asset);
                    }
                }
                if (!generated.isEmpty()) assetMapper.upsertBatch(generated);
                // 强制分析未返回的词不能用旧资产伪装为本次成功，保留未完成状态供重试。
                cold.forEach(entry -> latest.remove(term(entry)));
                generated.forEach(asset -> latest.put(asset.getNormalizedTerm(), asset));
            }
            return snapshots(job, entries, catalogEntries, latest,
                    generated.stream().map(VocabularySemanticAsset::getNormalizedTerm).toList());
        });
    }

    private Map<String, VocabularySemanticAsset> load(List<String> terms) {
        return assetMapper.findByTerms(terms).stream().collect(Collectors.toMap(
                VocabularySemanticAsset::getNormalizedTerm, Function.identity(),
                (first, ignored) -> first, LinkedHashMap::new));
    }

    private List<VocabularyCatalogEntry> missing(Map<String, VocabularyCatalogEntry> unique,
                                                Map<String, VocabularySemanticAsset> assets,
                                                Long jobId, boolean force) {
        return unique.entrySet().stream().filter(item -> {
            VocabularySemanticAsset asset = assets.get(item.getKey());
            return asset == null || (force && !Objects.equals(asset.getSourceJobId(), jobId));
        }).map(Map.Entry::getValue).toList();
    }

    private VocabularySemanticAsset toAsset(VocabularyCatalogAnalysisJob job, VocabularyCatalogEntry entry,
                                            VocabularyCatalogEntryAnalysis analysis,
                                            Map<Long, VocabularyCatalogEntry> catalogEntries) {
        ObjectNode semantic = objectMapper.createObjectNode();
        semantic.put("primaryGroupCode", analysis.getPrimaryGroupCode());
        semantic.put("primaryGroupName", analysis.getPrimaryGroupName());
        semantic.put("domainCode", analysis.getDomainCode());
        semantic.put("subTopicCode", analysis.getSubTopicCode());
        semantic.put("tagsJson", analysis.getTagsJson());
        semantic.put("difficultyLevel", analysis.getDifficultyLevel());
        semantic.put("confidence", analysis.getConfidence());
        semantic.put("status", analysis.getStatus());
        List<String> related = new ArrayList<>();
        try {
            for (var id : objectMapper.readTree(analysis.getRelatedEntryIdsJson())) {
                VocabularyCatalogEntry relatedEntry = catalogEntries.get(Long.valueOf(id.asText()));
                if (relatedEntry != null) related.add(term(relatedEntry));
            }
            VocabularySemanticAsset asset = new VocabularySemanticAsset();
            asset.setId(IdWorker.getId());
            asset.setLanguage("en");
            asset.setNormalizedTerm(term(entry));
            asset.setSemanticJson(objectMapper.writeValueAsString(semantic));
            asset.setRelatedTermsJson(objectMapper.writeValueAsString(related.stream().distinct().toList()));
            asset.setSourceJobId(job.getId());
            asset.setCreateBy(job.getUserId());
            asset.setUpdateBy(job.getUserId());
            return asset;
        } catch (JsonProcessingException | NumberFormatException ex) {
            throw LearningAssistantException.badRequest(LearningErrorCode.JSON_SERIALIZE_FAILED);
        }
    }

    private List<VocabularyCatalogEntryAnalysis> snapshots(
            VocabularyCatalogAnalysisJob job, List<VocabularyCatalogEntry> entries,
            Map<Long, VocabularyCatalogEntry> catalogEntries, Map<String, VocabularySemanticAsset> assets,
            List<String> generatedTerms) {
        Map<String, Long> idsByTerm = catalogEntries.values().stream().collect(Collectors.toMap(
                this::term, VocabularyCatalogEntry::getId, (first, ignored) -> first));
        List<VocabularyCatalogEntryAnalysis> result = new ArrayList<>();
        for (VocabularyCatalogEntry entry : entries) {
            VocabularySemanticAsset asset = assets.get(term(entry));
            if (asset == null) continue;
            try {
                VocabularyCatalogEntryAnalysis analysis = objectMapper.readValue(
                        asset.getSemanticJson(), VocabularyCatalogEntryAnalysis.class);
                List<Long> relatedIds = new ArrayList<>();
                for (var related : objectMapper.readTree(asset.getRelatedTermsJson())) {
                    Long id = idsByTerm.get(related.asText());
                    if (id != null && !id.equals(entry.getId())) relatedIds.add(id);
                }
                analysis.setId(IdWorker.getId());
                analysis.setJobId(job.getId());
                analysis.setCatalogId(job.getCatalogId());
                analysis.setCatalogVersionId(job.getCatalogVersionId());
                analysis.setCatalogEntryId(entry.getId());
                analysis.setRelatedEntryIdsJson(objectMapper.writeValueAsString(relatedIds.stream().distinct().toList()));
                analysis.setSource(generatedTerms.contains(term(entry))
                        ? VocabularyCatalogAnalysisConstants.SOURCE_AI
                        : VocabularyCatalogAnalysisConstants.SOURCE_INHERITED);
                analysis.setAnalysisVersion(job.getAnalysisVersion());
                analysis.setRawResultJson(objectMapper.writeValueAsString(Map.of(
                        "semantic_asset_id", asset.getId().toString(),
                        "source_job_id", asset.getSourceJobId().toString())));
                analysis.setCreateBy(job.getUserId());
                analysis.setUpdateBy(job.getUserId());
                analysis.setCreateTime(LocalDateTime.now());
                analysis.setUpdateTime(LocalDateTime.now());
                analysis.setDeleted(false);
                analysis.setVersion(0);
                result.add(analysis);
            } catch (JsonProcessingException ex) {
                throw LearningAssistantException.badRequest(LearningErrorCode.JSON_PARSE_FAILED);
            }
        }
        long inherited = result.stream().filter(item ->
                VocabularyCatalogAnalysisConstants.SOURCE_INHERITED.equals(item.getSource())).count();
        log.info("用户词本语义分析批次完成 userId={} jobId={} 词条数={} 复用数={} 新分析标准词数={} 未完成数={}",
                job.getUserId(), job.getId(), entries.size(), inherited, generatedTerms.size(),
                entries.size() - result.size());
        return result;
    }

    private String term(VocabularyCatalogEntry entry) {
        String value = entry.getNormalizedTerm();
        if (value == null || value.isBlank()) value = entry.effectiveTerm();
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
