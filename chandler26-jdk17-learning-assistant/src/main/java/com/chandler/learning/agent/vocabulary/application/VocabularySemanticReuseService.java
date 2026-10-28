package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCatalogAnalysisConstants;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogAnalysisJob;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntryAnalysis;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularySemanticAsset;
import com.chandler.learning.agent.vocabulary.infrastructure.VocabularySemanticGenerationLock;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularySemanticAssetMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 全局词汇语义复用：优先从语义资产与已有词汇学习卡片复用，只为缺失的标准词调用大模型生成器。
 */
@Service
@Slf4j
public class VocabularySemanticReuseService {

    private final VocabularySemanticAssetMapper assetMapper;
    private final VocabularySemanticGenerationLock generationLock;
    private final ObjectMapper objectMapper;
    private final EnglishVocabularyStudyRecordMapper studyRecordMapper;

    @Autowired
    public VocabularySemanticReuseService(VocabularySemanticAssetMapper assetMapper,
                                          VocabularySemanticGenerationLock generationLock,
                                          ObjectMapper objectMapper,
                                          EnglishVocabularyStudyRecordMapper studyRecordMapper) {
        this.assetMapper = assetMapper;
        this.generationLock = generationLock;
        this.objectMapper = objectMapper;
        this.studyRecordMapper = studyRecordMapper;
    }

    public VocabularySemanticReuseService(VocabularySemanticAssetMapper assetMapper,
                                          VocabularySemanticGenerationLock generationLock,
                                          ObjectMapper objectMapper) {
        this(assetMapper, generationLock, objectMapper, null);
    }

    /**
     * 批量查重并返回分析快照。
     * <p>
     * 1. 优先查已有词本语义资产表；
     * 2. 若未命中，优先查库中已有词汇学习卡片（0 Token 本地合成语义资产）；
     * 3. 只有两者皆缺失的冷词才进入互斥并调用大模型生成。
     */
    public List<VocabularyCatalogEntryAnalysis> resolve(
            VocabularyCatalogAnalysisJob job, List<VocabularyCatalogEntry> entries,
            Map<Long, VocabularyCatalogEntry> catalogEntries, boolean force,
            Function<List<VocabularyCatalogEntry>, List<VocabularyCatalogEntryAnalysis>> generator) {
        if (entries.isEmpty()) return List.of();
        Map<String, VocabularyCatalogEntry> unique = entries.stream().collect(Collectors.toMap(
                this::term, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        Map<String, VocabularySemanticAsset> assets = load(unique.keySet().stream().toList());

        // 优先从已有词卡库中复用（免调 AI 接口）
        if (!force && studyRecordMapper != null) {
            List<VocabularyCatalogEntry> missing = missing(unique, assets, job.getId(), false);
            if (!missing.isEmpty()) {
                Map<String, VocabularySemanticAsset> fromCards = loadFromStudyRecords(job, missing);
                if (!fromCards.isEmpty()) {
                    assetMapper.upsertBatch(new ArrayList<>(fromCards.values()));
                    assets.putAll(fromCards);
                    log.info("从现有词卡库中复用语义资产 userId={} jobId={} 命中词数={}",
                            job.getUserId(), job.getId(), fromCards.size());
                }
            }
        }

        if (missing(unique, assets, job.getId(), force).isEmpty()) {
            return snapshots(job, entries, catalogEntries, assets, List.of());
        }

        return generationLock.execute(() -> {
            // 另一个词本可能在等待锁期间完成分析，因此持锁后必须重新批量读取。
            Map<String, VocabularySemanticAsset> latest = load(unique.keySet().stream().toList());
            if (!force && studyRecordMapper != null) {
                List<VocabularyCatalogEntry> stillMissing = missing(unique, latest, job.getId(), false);
                if (!stillMissing.isEmpty()) {
                    Map<String, VocabularySemanticAsset> fromCards = loadFromStudyRecords(job, stillMissing);
                    if (!fromCards.isEmpty()) {
                        assetMapper.upsertBatch(new ArrayList<>(fromCards.values()));
                        latest.putAll(fromCards);
                    }
                }
            }

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

    /**
     * 从已有词汇深度学习卡片构建语义资产，实现免调大模型零成本复用。
     */
    private Map<String, VocabularySemanticAsset> loadFromStudyRecords(
            VocabularyCatalogAnalysisJob job, List<VocabularyCatalogEntry> missingEntries) {
        if (studyRecordMapper == null || missingEntries == null || missingEntries.isEmpty()) {
            return Map.of();
        }
        List<String> terms = missingEntries.stream()
                .map(this::term)
                .distinct()
                .toList();

        List<EnglishVocabularyStudyRecord> records = studyRecordMapper.selectList(
                new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                        .in(EnglishVocabularyStudyRecord::getNormalizedTerm, terms)
                        .eq(EnglishVocabularyStudyRecord::getDeleted, false));

        if (records == null || records.isEmpty()) {
            return Map.of();
        }

        Map<String, VocabularySemanticAsset> assets = new LinkedHashMap<>();
        for (EnglishVocabularyStudyRecord record : records) {
            VocabularySemanticAsset asset = toAssetFromStudyRecord(job, record);
            if (asset != null) {
                assets.put(asset.getNormalizedTerm(), asset);
            }
        }
        return assets;
    }

    /**
     * 将已有词卡转换为标准语义资产。
     */
    private VocabularySemanticAsset toAssetFromStudyRecord(
            VocabularyCatalogAnalysisJob job, EnglishVocabularyStudyRecord record) {
        if (!StringUtils.hasText(record.getParsedJson())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(record.getParsedJson());
            if (root == null || !root.isObject()) {
                return null;
            }
            String term = record.getNormalizedTerm();
            if (!StringUtils.hasText(term)) {
                return null;
            }

            // 1. primaryGroupCode & primaryGroupName
            String lemma = firstNonBlank(
                    text(root, "lemma"),
                    text(root, "term"),
                    record.getNormalizedTerm()
            );
            String groupCode = normalizeGroupCode(lemma);
            String groupName = lemma.toLowerCase(Locale.ROOT);

            // 2. domainCode & subTopicCode
            String domain = firstNonBlank(
                    text(root.path("cognitive_image"), "domain", "category"),
                    text(root, "domain", "category"),
                    "general"
            );
            String subTopic = firstNonBlank(
                    text(root.path("cognitive_image"), "title"),
                    text(root, "sub_topic", "topic"),
                    term
            );

            // 3. tags
            List<String> tags = extractTagsFromCard(root);

            // 4. difficulty
            String difficulty = firstNonBlank(
                    text(root, "difficulty", "difficulty_level", "level", "cefr"),
                    inferDifficulty(root, term)
            );

            // 5. related terms (synonyms, antonyms, word_family)
            Set<String> relatedTerms = extractRelatedTermsFromCard(root, term);

            // 6. 构造资产语义 JSON
            ObjectNode semantic = objectMapper.createObjectNode();
            semantic.put("primaryGroupCode", groupCode);
            semantic.put("primaryGroupName", groupName);
            semantic.put("domainCode", domain.toLowerCase(Locale.ROOT));
            semantic.put("subTopicCode", subTopic.toLowerCase(Locale.ROOT));
            semantic.put("tagsJson", objectMapper.writeValueAsString(tags));
            semantic.put("difficultyLevel", difficulty);
            semantic.put("confidence", 1.0);
            semantic.put("status", "ready");

            VocabularySemanticAsset asset = new VocabularySemanticAsset();
            asset.setId(IdWorker.getId());
            asset.setLanguage("en");
            asset.setNormalizedTerm(term);
            asset.setSemanticJson(objectMapper.writeValueAsString(semantic));
            asset.setRelatedTermsJson(objectMapper.writeValueAsString(new ArrayList<>(relatedTerms)));
            asset.setSourceJobId(job.getId());
            asset.setCreateBy(job.getUserId());
            asset.setUpdateBy(job.getUserId());
            return asset;
        } catch (Exception ex) {
            log.warn("从已有词卡解析语义资产失败 term={}", record.getNormalizedTerm(), ex);
            return null;
        }
    }

    private String normalizeGroupCode(String lemma) {
        if (!StringUtils.hasText(lemma)) {
            return "GENERAL";
        }
        String clean = lemma.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        clean = clean.replaceAll("^_+|_+$", "");
        return clean.isEmpty() ? "GENERAL" : clean;
    }

    private List<String> extractTagsFromCard(JsonNode root) {
        Set<String> tags = new LinkedHashSet<>();
        if (root.has("tags") && root.get("tags").isArray()) {
            for (JsonNode t : root.get("tags")) {
                String s = t.asText();
                if (StringUtils.hasText(s)) tags.add(s.trim());
            }
        }
        JsonNode defs = root.path("definitions");
        if (defs.isArray()) {
            for (JsonNode def : defs) {
                String pos = firstNonBlank(text(def, "part_of_speech", "pos", "type"));
                if (StringUtils.hasText(pos)) tags.add(pos.trim());
                String meaning = firstNonBlank(text(def, "meaning", "meaning_cn", "translation"));
                if (StringUtils.hasText(meaning)) {
                    for (String part : meaning.split("[；;，,、/\\s]+")) {
                        String clean = cleanTag(part);
                        if (StringUtils.hasText(clean) && clean.length() <= 8) {
                            tags.add(clean);
                            break;
                        }
                    }
                }
            }
        }
        String domain = text(root.path("cognitive_image"), "category", "domain");
        if (StringUtils.hasText(domain) && domain.length() <= 8) {
            tags.add(cleanTag(domain));
        }
        return tags.stream().filter(StringUtils::hasText).limit(6).toList();
    }

    private String cleanTag(String s) {
        if (s == null) return "";
        return s.replaceAll("[；;,，.。\\[\\]()（）]", "").trim();
    }

    private Set<String> extractRelatedTermsFromCard(JsonNode root, String currentTerm) {
        Set<String> related = new LinkedHashSet<>();
        List<String> fields = List.of("synonyms", "antonyms", "word_family", "wordFamily", "related_words");
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String raw = item.isTextual() ? item.asText() : text(item, "term", "word", "text");
                    String cleaned = cleanRelationWord(raw);
                    if (StringUtils.hasText(cleaned) && !cleaned.equalsIgnoreCase(currentTerm)) {
                        related.add(cleaned.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        JsonNode etymologyFamily = root.path("etymology").path("word_family");
        if (etymologyFamily.isArray()) {
            for (JsonNode item : etymologyFamily) {
                String raw = item.isTextual() ? item.asText() : text(item, "term", "word", "text");
                String cleaned = cleanRelationWord(raw);
                if (StringUtils.hasText(cleaned) && !cleaned.equalsIgnoreCase(currentTerm)) {
                    related.add(cleaned.toLowerCase(Locale.ROOT));
                }
            }
        }
        return related;
    }

    private String cleanRelationWord(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\[[^]]*]", "")
                .replaceAll("（[^）]*）", "")
                .replaceAll("【[^】]*】", "")
                .replaceAll("[/\\\\].*$", "")
                .trim();
    }

    private String inferDifficulty(JsonNode root, String term) {
        JsonNode defs = root.path("definitions");
        int defCount = defs.isArray() ? defs.size() : 1;
        int len = term == null ? 0 : term.length();
        if (defCount >= 4 || len >= 10) {
            return "hard";
        }
        if (defCount >= 2 || len >= 7) {
            return "medium";
        }
        return "easy";
    }

    private String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String key : keys) {
            JsonNode val = node.get(key);
            if (val != null && !val.isNull()) {
                String s = val.asText();
                if (StringUtils.hasText(s)) {
                    return s.trim();
                }
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
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
