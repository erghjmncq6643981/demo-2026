package com.chandler.learning.agent.reading.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationProbeResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationProbeMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationSentencePracticeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识反刍材料与探针幂等落库服务。
 * <p>集中负责事务内材料/探针写入（支持新材料插入与指定源材料覆盖更新）、按提交时词袋快照做增量合并扣除，以及 DTO 响应安全映射。</p>
 */
@Slf4j
@Service
public class RuminationMaterialPersistenceService {

    private final LearningRuminationMaterialMapper materialMapper;
    private final LearningRuminationProbeMapper probeMapper;
    private final LearningRuminationGraphMapper graphMapper;
    private final LearningRuminationSentencePracticeMapper sentencePracticeMapper;
    private final ObjectMapper objectMapper;

    /** 兼容旧 4 参构造（供未注入句型跟敲 Mapper 的单元测试使用）。 */
    public RuminationMaterialPersistenceService(
            LearningRuminationMaterialMapper materialMapper,
            LearningRuminationProbeMapper probeMapper,
            LearningRuminationGraphMapper graphMapper,
            ObjectMapper objectMapper) {
        this(materialMapper, probeMapper, graphMapper, null, objectMapper);
    }

    /** Spring 主构造函数：注入材料、探针、图谱与句型跟敲 Mapper。 */
    @Autowired
    public RuminationMaterialPersistenceService(
            LearningRuminationMaterialMapper materialMapper,
            LearningRuminationProbeMapper probeMapper,
            LearningRuminationGraphMapper graphMapper,
            LearningRuminationSentencePracticeMapper sentencePracticeMapper,
            ObjectMapper objectMapper) {
        this.materialMapper = materialMapper;
        this.probeMapper = probeMapper;
        this.graphMapper = graphMapper;
        this.sentencePracticeMapper = sentencePracticeMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 在短事务内幂等保存场景材料、微环境探针，并按提交快照扣除词袋词汇。
     * <p>生成等待期间用户新加入词袋的词会被完整保留。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationMaterialResponse persistGeneratedMaterial(
            Long userId,
            Long wordbookId,
            String scenarioCode,
            List<String> snapshotTargetWords,
            String generationSource,
            Long taskId,
            RuminationAiGenerator.GeneratedMaterialResult generated) {
        return persistGeneratedMaterial(
                userId, wordbookId, scenarioCode, snapshotTargetWords, generationSource, taskId, generated, true, null);
    }

    /**
     * 在短事务内幂等保存场景材料与微环境探针，并按需决定是否从当前词袋中扣除本次目标词汇。
     *
     * @param consumeBag {@code true} 表示扣除词袋词汇；{@code false} 表示重新生成并完整保留当前词袋
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationMaterialResponse persistGeneratedMaterial(
            Long userId,
            Long wordbookId,
            String scenarioCode,
            List<String> snapshotTargetWords,
            String generationSource,
            Long taskId,
            RuminationAiGenerator.GeneratedMaterialResult generated,
            boolean consumeBag) {
        return persistGeneratedMaterial(
                userId, wordbookId, scenarioCode, snapshotTargetWords, generationSource, taskId, generated, consumeBag, null);
    }

    /**
     * 在短事务内幂等保存或覆盖指定场景材料与微环境探针，并按需从当前词袋扣除目标词汇。
     *
     * @param consumeBag       {@code true} 表示扣除词袋词汇；{@code false} 表示保留当前词袋
     * @param sourceMaterialId 非空时覆盖该已有场景材料并清理其旧探针、旧跟敲与过期音频状态；为空时新增材料
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationMaterialResponse persistGeneratedMaterial(
            Long userId,
            Long wordbookId,
            String scenarioCode,
            List<String> snapshotTargetWords,
            String generationSource,
            Long taskId,
            RuminationAiGenerator.GeneratedMaterialResult generated,
            boolean consumeBag,
            Long sourceMaterialId) {
        if (taskId != null) {
            LearningRuminationMaterial existing = findExistingMaterialByTaskId(userId, taskId);
            if (existing != null) {
                log.info("知识反刍材料任务落库命中幂等记录 taskId={} materialId={}", taskId, existing.getId());
                RuminationMaterialResponse response = mapMaterialToResponse(existing);
                response.setBagConsumed(consumeBag);
                return response;
            }
        }

        List<String> normalizedSnapshot = normalizeDistinctWords(snapshotTargetWords);
        String resolvedSource = resolveSource(generationSource, generated.aiPowered());

        LearningRuminationMaterial material;
        if (sourceMaterialId != null) {
            material = overwriteExistingMaterial(
                    userId, wordbookId, scenarioCode, sourceMaterialId,
                    normalizedSnapshot, resolvedSource, taskId, generated);
        } else {
            material = buildNewMaterial(
                    userId, wordbookId, scenarioCode,
                    normalizedSnapshot, resolvedSource, taskId, generated);
            try {
                materialMapper.insert(material);
            } catch (DuplicateKeyException ex) {
                if (taskId != null) {
                    LearningRuminationMaterial concurrentExisting = findExistingMaterialByTaskId(userId, taskId);
                    if (concurrentExisting != null) {
                        log.info("知识反刍材料并发落库幂等复用 taskId={} materialId={}",
                                taskId, concurrentExisting.getId());
                        RuminationMaterialResponse response = mapMaterialToResponse(concurrentExisting);
                        response.setBagConsumed(consumeBag);
                        return response;
                    }
                }
                throw ex;
            }
        }

        List<LearningRuminationProbe> persistedProbes = persistProbes(
                userId, material.getId(), resolvedSource, generated.probes());

        Integer updatedGraphVersion = consumeBag
                ? subtractConsumedWordsFromBag(userId, wordbookId, scenarioCode, normalizedSnapshot)
                : null;

        RuminationMaterialResponse response = mapMaterialToResponse(material, persistedProbes);
        response.setGraphVersion(updatedGraphVersion);
        response.setBagConsumed(consumeBag);
        log.info("用户「{}」场景「{}」完成材料落库(source={} taskId={} consumeBag={} overwriteMaterialId={})，本次目标词 {} 词，生成 {} 道学习检查题，图谱版本={}",
                userId, scenarioCode, resolvedSource, taskId, consumeBag, sourceMaterialId,
                normalizedSnapshot.size(), persistedProbes.size(), updatedGraphVersion);
        return response;
    }

    private LearningRuminationMaterial buildNewMaterial(
            Long userId,
            Long wordbookId,
            String scenarioCode,
            List<String> normalizedSnapshot,
            String resolvedSource,
            Long taskId,
            RuminationAiGenerator.GeneratedMaterialResult generated) {
        LearningRuminationMaterial material = new LearningRuminationMaterial();
        material.setUserId(userId);
        material.setCreateBy(userId);
        applyMaterialContent(material, userId, wordbookId, scenarioCode,
                normalizedSnapshot, resolvedSource, taskId, generated);
        return material;
    }

    private LearningRuminationMaterial overwriteExistingMaterial(
            Long userId,
            Long wordbookId,
            String scenarioCode,
            Long sourceMaterialId,
            List<String> normalizedSnapshot,
            String resolvedSource,
            Long taskId,
            RuminationAiGenerator.GeneratedMaterialResult generated) {
        LearningRuminationMaterial target = materialMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationMaterial>()
                        .eq(LearningRuminationMaterial::getId, sourceMaterialId)
                        .eq(LearningRuminationMaterial::getUserId, userId)
                        .eq(LearningRuminationMaterial::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (target == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND,
                    "原场景材料不存在或已删除，无法覆盖重新生成");
        }

        // 先逻辑删除旧学习检查题，并物理清理旧句型跟敲（避开 uk_rumination_sentence_practice 唯一索引冲突）
        probeMapper.delete(
                new LambdaQueryWrapper<LearningRuminationProbe>()
                        .eq(LearningRuminationProbe::getMaterialId, target.getId())
                        .eq(LearningRuminationProbe::getUserId, userId)
        );
        if (sentencePracticeMapper != null) {
            sentencePracticeMapper.deleteByMaterialId(userId, target.getId());
        }

        applyMaterialContent(target, userId, wordbookId, scenarioCode,
                normalizedSnapshot, resolvedSource, taskId, generated);
        target.setAudioStatus("none");
        target.setAudioPath(null);
        materialMapper.updateById(target);
        materialMapper.update(null, new UpdateWrapper<LearningRuminationMaterial>()
                .eq("id", target.getId())
                .eq("user_id", userId)
                .set("audio_path", null)
                .set("audio_status", "none"));
        return target;
    }

    private void applyMaterialContent(
            LearningRuminationMaterial material,
            Long userId,
            Long wordbookId,
            String scenarioCode,
            List<String> normalizedSnapshot,
            String resolvedSource,
            Long taskId,
            RuminationAiGenerator.GeneratedMaterialResult generated) {
        material.setWordbookId(wordbookId);
        material.setScenarioCode(scenarioCode);
        material.setTitle(generated.title());
        material.setScenarioSummary(generated.summary());
        material.setArticleContent(generated.article());
        material.setTranslationContent(generated.translation());
        material.setTargetWordsJson(writeJsonArray(normalizedSnapshot));
        material.setWordCount(StringUtils.hasText(generated.article())
                ? generated.article().trim().split("\\s+").length : 0);
        material.setGenerationSource(resolvedSource);
        material.setTaskId(taskId);
        material.setUpdateBy(userId);
    }

    private List<LearningRuminationProbe> persistProbes(
            Long userId,
            Long materialId,
            String resolvedSource,
            List<RuminationAiGenerator.ProbeDraft> drafts) {
        List<LearningRuminationProbe> persistedProbes = new ArrayList<>();
        if (drafts == null || drafts.isEmpty()) {
            return persistedProbes;
        }
        for (RuminationAiGenerator.ProbeDraft draft : drafts) {
            LearningRuminationProbe probe = new LearningRuminationProbe();
            probe.setMaterialId(materialId);
            probe.setUserId(userId);
            probe.setTargetWord(draft.targetWord());
            probe.setProbeType(draft.probeType());
            probe.setQuestion(draft.question());
            probe.setOptionsJson(writeJsonArray(draft.options() == null ? List.of() : draft.options()));
            probe.setCorrectAnswer(draft.correctAnswer());
            probe.setHint(draft.hint());
            probe.setGenerationSource(!"fallback".equalsIgnoreCase(resolvedSource)
                    && StringUtils.hasText(draft.generationSource())
                    ? draft.generationSource()
                    : resolvedSource);
            probe.setScore(10);
            probe.setIlluminated(false);
            probe.setCreateBy(userId);
            probe.setUpdateBy(userId);
            probeMapper.insert(probe);
            persistedProbes.add(probe);
        }
        return persistedProbes;
    }

    /** 读取并规范化材料记录中存储的目标词列表。 */
    public List<String> readTargetWords(LearningRuminationMaterial material) {
        if (material == null) {
            return List.of();
        }
        return normalizeDistinctWords(readStringList(material.getTargetWordsJson()));
    }

    /** 根据异步任务 ID 查询已落库的材料响应，未落库时返回 null。 */
    public RuminationMaterialResponse findMaterialResponseByTaskId(Long userId, Long taskId) {
        if (userId == null || taskId == null) {
            return null;
        }
        LearningRuminationMaterial existing = findExistingMaterialByTaskId(userId, taskId);
        if (existing == null) {
            return null;
        }
        RuminationMaterialResponse response = mapMaterialToResponse(existing);
        LearningRuminationGraph currentGraph = graphMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, existing.getWordbookId())
                        .eq(LearningRuminationGraph::getScenarioCode, existing.getScenarioCode())
                        .eq(LearningRuminationGraph::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (currentGraph != null) {
            response.setGraphVersion(currentGraph.getVersion() == null ? 0 : currentGraph.getVersion());
        }
        return response;
    }

    /** 将持久化的场景材料转换为接口响应，包含生成来源和探针详情。 */
    public RuminationMaterialResponse mapMaterialToResponse(LearningRuminationMaterial material) {
        List<LearningRuminationProbe> probes = probeMapper.selectList(
                new LambdaQueryWrapper<LearningRuminationProbe>()
                        .eq(LearningRuminationProbe::getMaterialId, material.getId())
                        .eq(LearningRuminationProbe::getDeleted, 0)
        );
        return mapMaterialToResponse(material, probes);
    }

    /** 将单个微环境探针转换为接口响应；未作答题目严禁下发正确答案。 */
    public RuminationProbeResponse mapProbeToResponse(LearningRuminationProbe probe) {
        RuminationProbeResponse res = new RuminationProbeResponse();
        res.setId(String.valueOf(probe.getId()));
        res.setMaterialId(String.valueOf(probe.getMaterialId()));
        res.setTargetWord(probe.getTargetWord());
        res.setProbeType(probe.getProbeType());
        res.setProbeTypeName(resolveProbeTypeName(probe.getProbeType()));
        res.setQuestion(probe.getQuestion());
        res.setOptions(readStringList(probe.getOptionsJson()));
        boolean answered = probe.getAnsweredTime() != null && probe.getIsCorrect() != null;
        res.setCorrectAnswer(answered ? probe.getCorrectAnswer() : null);
        res.setHint(probe.getHint());
        res.setGenerationSource(StringUtils.hasText(probe.getGenerationSource()) ? probe.getGenerationSource() : "ai");
        res.setSelectedAnswer(probe.getSelectedAnswer());
        res.setIsCorrect(probe.getIsCorrect());
        res.setScore(probe.getScore());
        res.setIlluminated(probe.getIlluminated());
        res.setAnsweredTime(probe.getAnsweredTime());
        return res;
    }

    private LearningRuminationMaterial findExistingMaterialByTaskId(Long userId, Long taskId) {
        return materialMapper.selectOne(new LambdaQueryWrapper<LearningRuminationMaterial>()
                .eq(LearningRuminationMaterial::getUserId, userId)
                .eq(LearningRuminationMaterial::getTaskId, taskId)
                .eq(LearningRuminationMaterial::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private Integer subtractConsumedWordsFromBag(Long userId, Long wordbookId, String scenarioCode,
                                                 List<String> snapshotTargetWords) {
        LearningRuminationGraph graph = graphMapper.selectByScenarioForUpdate(userId, wordbookId, scenarioCode);
        if (graph == null) {
            return null;
        }
        int previousVersion = graph.getVersion() == null ? 0 : graph.getVersion();
        Set<String> consumedLower = snapshotTargetWords.stream()
                .filter(StringUtils::hasText)
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> currentBag = readStringList(graph.getBagTermsJson());
        List<String> remainingBag = currentBag.stream()
                .filter(term -> StringUtils.hasText(term)
                        && !consumedLower.contains(term.trim().toLowerCase(Locale.ROOT)))
                .toList();
        graph.setBagTermsJson(writeJsonArray(remainingBag));
        graph.setUpdateBy(userId);
        graphMapper.updateById(graph);
        int expectedNextVersion = previousVersion + 1;
        if (graph.getVersion() == null || graph.getVersion() < expectedNextVersion) {
            graph.setVersion(expectedNextVersion);
        }
        return graph.getVersion();
    }

    private RuminationMaterialResponse mapMaterialToResponse(LearningRuminationMaterial material,
                                                             List<LearningRuminationProbe> probes) {
        RuminationMaterialResponse res = new RuminationMaterialResponse();
        res.setId(String.valueOf(material.getId()));
        res.setWordbookId(String.valueOf(material.getWordbookId()));
        res.setScenarioCode(material.getScenarioCode());
        res.setTitle(material.getTitle());
        res.setScenarioSummary(material.getScenarioSummary());
        res.setArticleContent(material.getArticleContent());
        res.setTranslationContent(material.getTranslationContent());
        res.setWordCount(material.getWordCount());
        res.setGenerationSource(StringUtils.hasText(material.getGenerationSource())
                ? material.getGenerationSource() : "ai");
        res.setTaskId(material.getTaskId() == null ? null : String.valueOf(material.getTaskId()));
        res.setCreateTime(material.getCreateTime());
        String audioStatus = StringUtils.hasText(material.getAudioStatus()) ? material.getAudioStatus() : "none";
        res.setAudioStatus(audioStatus);
        res.setHasAudio("completed".equalsIgnoreCase(audioStatus) && StringUtils.hasText(material.getAudioPath()));
        res.setTargetWords(readStringList(material.getTargetWordsJson()));
        res.setProbes(probes == null ? List.of() : probes.stream().map(this::mapProbeToResponse).toList());
        RuminationSentenceExtractor.populatePracticeData(res, List.of());
        return res;
    }

    private String resolveSource(String explicitSource, boolean aiPowered) {
        if ("fallback".equalsIgnoreCase(explicitSource) || !aiPowered) {
            return "fallback";
        }
        return "ai";
    }

    private String resolveProbeTypeName(String type) {
        if ("context_match".equals(type)) return "情境辨析题";
        if ("collocation".equals(type)) return "动作搭配题";
        if ("causality".equals(type)) return "因果推理题";
        return "学习结果检查";
    }

    private List<String> normalizeDistinctWords(List<String> rawWords) {
        if (rawWords == null || rawWords.isEmpty()) {
            return List.of();
        }
        return rawWords.stream()
                .filter(StringUtils::hasText)
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}
