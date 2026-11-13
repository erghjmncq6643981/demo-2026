package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.ai.model.domain.entity.AiModelConfig;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识反刍 AI 协同生成组件。
 * <p>
 * 负责在数据库事务外调用系统当前默认启用的 AI 模型（由 {@link AiModelConfigService#getDefaultEnabled()} 解析），
 * 完成场景文章与微探针联合编织、自定义场景核心词与 4D 拓扑扩展，以及单个节点的 4D 衍生裂变。
 * 模型不可用或响应异常时，统一平滑降级至 {@link RuminationSemanticEvaluator}，保障业务链路韧性。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuminationAiGenerator {

    private static final int MAX_CANDIDATE_WORDS_FOR_PROMPT = 75;
    private static final Set<String> VALID_DIMENSIONS = Set.of("action", "downstream", "attribute", "peer");
    private static final Set<String> VALID_PROBE_TYPES = Set.of("context_match", "collocation", "causality");

    private final AiChatService aiChatService;
    private final AiModelConfigService aiModelConfigService;
    private final RuminationSemanticEvaluator semanticEvaluator;
    private final ObjectMapper objectMapper;

    /** 生成的探针草案（落库前尚未分配 materialId）。 */
    public record ProbeDraft(String targetWord, String probeType, String question, List<String> options,
                             String correctAnswer, String hint, String generationSource) {
        public ProbeDraft {
            generationSource = StringUtils.hasText(generationSource) ? generationSource : "ai";
        }

        public ProbeDraft(String targetWord, String probeType, String question, List<String> options,
                          String correctAnswer, String hint) {
            this(targetWord, probeType, question, options, correctAnswer, hint, "ai");
        }
    }

    /** 场景材料与微探针生成结果。 */
    public record GeneratedMaterialResult(String title, String summary, String article, String translation,
                                          List<ProbeDraft> probes, boolean aiPowered) {}

    /** 4D 衍生概念条目。 */
    public record DerivationDraft(String term, String definition, String dimension, String linkedCore) {}

    /** 自定义场景宇宙 AI 生成结果。 */
    public record GeneratedUniverseResult(Map<String, String> coreWordDefinitions,
                                          List<DerivationDraft> derivations, boolean aiPowered) {}

    /** 节点 4D 衍生裂变结果。 */
    public record FissionResult(List<DerivationDraft> expansions, boolean aiPowered) {}

    /**
     * 在事务外调用系统默认 AI 模型联合生成场景文章、对照翻译及微环境探针；失败时降级本地语义引擎。
     *
     * @param userId          当前用户 ID
     * @param scenarioName    场景名称
     * @param scenarioPrompt  场景语境说明
     * @param targetWords     词袋目标词汇列表
     * @param wordDefinitions 目标词中文释义映射
     * @return 生成的场景材料与微探针草案
     */
    public GeneratedMaterialResult generateMaterialAndProbes(Long userId, String scenarioName, String scenarioPrompt,
                                                             List<String> targetWords,
                                                             Map<String, String> wordDefinitions) {
        try {
            Long defaultModelConfigId = resolveDefaultModelConfigId();
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("scenario_name", scenarioName);
            variables.put("scenario_prompt", scenarioPrompt);
            variables.put("target_words", targetWords);
            variables.put("word_definitions", wordDefinitions);

            String prompt = buildMaterialPrompt(scenarioName, scenarioPrompt, targetWords, wordDefinitions);
            AgentChatResponse response = invokeDefaultModel(
                    userId,
                    defaultModelConfigId,
                    AiInvocationScene.RUMINATION_MATERIAL,
                    "rumination_material",
                    "知识反刍场景材料-" + scenarioName,
                    prompt,
                    variables
            );

            List<String> normalizedTargets = targetWords.stream()
                    .filter(StringUtils::hasText)
                    .map(w -> w.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();

            JsonNode root = response.requireStructuredRoot(AiInvocationScene.RUMINATION_MATERIAL);
            ValidatedMaterialDraft attempt1 = evaluateMaterialDraft(
                    root, scenarioName, normalizedTargets, userId, scenarioPrompt, wordDefinitions);
            ValidatedMaterialDraft selected = attempt1;

            if (attempt1.needsCorrection()) {
                log.warn("知识反刍 AI 首轮材料未完全通过严格校验，触发纠偏重生成: scenario={} missingWords={} rejectedProbes={}",
                        scenarioName, attempt1.missingWords(), attempt1.rejectedReasons().size());
                ValidatedMaterialDraft retry = attemptCorrectiveMaterialGeneration(
                        userId, defaultModelConfigId, scenarioName, scenarioPrompt,
                        normalizedTargets, wordDefinitions, variables, attempt1);
                if (retry != null && retry.isBetterThan(attempt1)) {
                    selected = retry;
                }
            }

            if (!selected.missingWords().isEmpty()) {
                log.warn("知识反刍 AI 纠偏后文章仍遗漏目标词 {}，降级使用本地语义引擎生成以保障完整覆盖",
                        selected.missingWords());
                return buildFallbackMaterialResult(userId, scenarioName, scenarioPrompt, normalizedTargets, wordDefinitions);
            }

            List<ProbeDraft> probes = new ArrayList<>(selected.validProbes());
            if (probes.size() < Math.min(3, normalizedTargets.size())) {
                topUpProbesWithFallback(probes, userId, scenarioName, scenarioPrompt, normalizedTargets, wordDefinitions);
            }
            log.info("知识反刍默认 AI 模型生成场景材料成功 provider={} model={} words={} probes={}",
                    response.getModelProvider(), response.getModelName(), normalizedTargets.size(), probes.size());
            return new GeneratedMaterialResult(
                    selected.title(), selected.summary(), selected.article(), selected.translation(), probes, true);
        } catch (Exception ex) {
            log.warn("知识反刍 AI 编织场景材料失败，平滑降级至本地语义引擎 scenario={} reason={}",
                    scenarioName, ex.getMessage());
            List<String> normalizedTargets = targetWords == null ? List.of() : targetWords.stream()
                    .filter(StringUtils::hasText)
                    .map(w -> w.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
            return buildFallbackMaterialResult(userId, scenarioName, scenarioPrompt, normalizedTargets, wordDefinitions);
        }
    }

    /**
     * 在事务外调用系统默认 AI 模型规划任意自定义主题的核心考纲词与 4D 衍生关系。
     *
     * @param userId             当前用户 ID
     * @param scenarioName       自定义场景名称
     * @param scenarioPrompt     场景描述提示词
     * @param allUserEntries     用户当前单词本全量词条
     * @param ruleMatchedEntries 本地语义评估已命中的高关联词条
     * @return AI 规划的核心词与 4D 衍生条目
     */
    public GeneratedUniverseResult generateCustomScenarioUniverse(Long userId, String scenarioName, String scenarioPrompt,
                                                                List<LearningWordbookEntry> allUserEntries,
                                                                List<LearningWordbookEntry> ruleMatchedEntries) {
        try {
            Long defaultModelConfigId = resolveDefaultModelConfigId();
            List<Map<String, String>> candidateWords = buildCandidateSample(allUserEntries, ruleMatchedEntries);

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("scenario_name", scenarioName);
            variables.put("scenario_prompt", scenarioPrompt);
            variables.put("candidate_words", candidateWords);

            String prompt = buildScenarioPrompt(scenarioName, scenarioPrompt, candidateWords);
            AgentChatResponse response = invokeDefaultModel(
                    userId,
                    defaultModelConfigId,
                    AiInvocationScene.RUMINATION_SCENARIO,
                    "rumination_scenario",
                    "知识反刍场景构建-" + scenarioName,
                    prompt,
                    variables
            );

            JsonNode root = response.requireStructuredRoot(AiInvocationScene.RUMINATION_SCENARIO);
            Map<String, String> coreDefinitions = new LinkedHashMap<>();
            JsonNode coreNode = root.path("core_words");
            if (coreNode.isArray()) {
                for (JsonNode item : coreNode) {
                    String term = item.isTextual() ? item.asText() : item.path("term").asText("");
                    String cleanTerm = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
                    if (StringUtils.hasText(cleanTerm)) {
                        String def = item.isObject() ? item.path("definition").asText(cleanTerm) : cleanTerm;
                        coreDefinitions.putIfAbsent(cleanTerm, StringUtils.hasText(def) ? def.trim() : cleanTerm);
                    }
                }
            }

            List<DerivationDraft> derivations = new ArrayList<>();
            Set<String> seenTerms = new LinkedHashSet<>();
            JsonNode derivNode = root.path("derivations");
            if (derivNode.isArray()) {
                for (JsonNode item : derivNode) {
                    String term = item.path("term").asText("").trim().toLowerCase(Locale.ROOT);
                    String def = item.path("definition").asText("").trim();
                    String dim = normalizeDimension(item.path("dimension").asText("action"));
                    String linkedCore = item.path("linked_core").asText(item.path("linkedCore").asText("")).trim().toLowerCase(Locale.ROOT);
                    if (StringUtils.hasText(term) && StringUtils.hasText(linkedCore) && seenTerms.add(term)) {
                        derivations.add(new DerivationDraft(term, StringUtils.hasText(def) ? def : term, dim, linkedCore));
                    }
                }
            }

            log.info("知识反刍默认 AI 模型生成自定义场景宇宙成功 scenario={} aiCoreWords={} aiDerivations={}",
                    scenarioName, coreDefinitions.size(), derivations.size());
            return new GeneratedUniverseResult(coreDefinitions, derivations, true);
        } catch (Exception ex) {
            log.warn("知识反刍自定义场景 AI 规划失败，降级本地领域模型 scenario={} reason={}",
                    scenarioName, ex.getMessage());
            return new GeneratedUniverseResult(Map.of(), List.of(), false);
        }
    }

    /**
     * 在事务外调用系统默认 AI 模型为单个词汇节点实时衍生 4D 认知拓展词汇。
     *
     * @param userId           当前用户 ID
     * @param scenarioName     当前场景名称
     * @param targetWord       待裂变英文词汇
     * @param targetDefinition 目标词汇中文释义
     * @param existingTerms    图谱中已存在词汇集合（小写）
     * @return 4D 衍生词列表与 AI 状态标记
     */
    public FissionResult generateFissionExpansions(Long userId, String scenarioName, String targetWord,
                                                   String targetDefinition, Set<String> existingTerms) {
        try {
            Long defaultModelConfigId = resolveDefaultModelConfigId();
            List<String> existingList = existingTerms == null ? List.of() : existingTerms.stream().limit(40).toList();

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("scenario_name", scenarioName);
            variables.put("target_word", targetWord);
            variables.put("target_definition", StringUtils.hasText(targetDefinition) ? targetDefinition : targetWord);
            variables.put("existing_terms", existingList);

            String prompt = buildFissionPrompt(scenarioName, targetWord, targetDefinition, existingList);
            AgentChatResponse response = invokeDefaultModel(
                    userId,
                    defaultModelConfigId,
                    AiInvocationScene.RUMINATION_FISSION,
                    "rumination_fission",
                    "知识反刍衍生裂变-" + targetWord,
                    prompt,
                    variables
            );

            JsonNode root = response.requireStructuredRoot(AiInvocationScene.RUMINATION_FISSION);
            List<DerivationDraft> items = new ArrayList<>();
            Set<String> dedup = new LinkedHashSet<>(existingTerms == null ? Set.of() : existingTerms);
            dedup.add(targetWord.trim().toLowerCase(Locale.ROOT));

            JsonNode expArray = root.path("expansions");
            if (expArray.isArray()) {
                for (JsonNode item : expArray) {
                    String term = item.path("term").asText("").trim().toLowerCase(Locale.ROOT);
                    String def = item.path("definition").asText("").trim();
                    String dim = normalizeDimension(item.path("dimension").asText("action"));
                    if (StringUtils.hasText(term) && term.length() <= 40 && dedup.add(term)) {
                        items.add(new DerivationDraft(term, StringUtils.hasText(def) ? def : term, dim, targetWord));
                    }
                    if (items.size() >= 4) {
                        break;
                    }
                }
            }

            log.info("知识反刍默认 AI 模型 4D 裂变成功 targetWord={} count={}", targetWord, items.size());
            return new FissionResult(items, true);
        } catch (Exception ex) {
            log.warn("知识反刍 4D 裂变 AI 调用失败，降级本地策略 targetWord={} reason={}",
                    targetWord, ex.getMessage());
            return new FissionResult(List.of(), false);
        }
    }

    /** 解析系统当前默认启用的 AI 模型配置 ID（无启用模型时返回 null）。 */
    public Long resolveDefaultModelConfigId() {
        AiModelConfig defaultModel = aiModelConfigService.getDefaultEnabled();
        return defaultModel != null ? defaultModel.getId() : null;
    }

    private AgentChatResponse invokeDefaultModel(Long userId, Long modelConfigId, AiInvocationScene scene,
                                                 String businessId, String title, String message,
                                                 Map<String, Object> variables) {
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setUserId(userId);
        chatRequest.setInvocationScene(scene);
        chatRequest.setAgentCode(AiScenarioConstants.ARTICLE_AGENT_CODE);
        chatRequest.setModelConfigId(modelConfigId);
        chatRequest.setTitle(title);
        chatRequest.setBusinessType(AiChatConstants.BUSINESS_TYPE_LEARNING);
        chatRequest.setBusinessId(businessId);
        chatRequest.setSceneCode(scene.getCode());
        chatRequest.setMessage(message);
        chatRequest.setVariables(variables);
        return aiChatService.chat(chatRequest);
    }

    private String buildMaterialPrompt(String scenarioName, String scenarioPrompt,
                                       List<String> targetWords, Map<String, String> wordDefinitions) {
        StringBuilder wordLines = new StringBuilder();
        for (String w : targetWords) {
            String def = wordDefinitions.getOrDefault(w.toLowerCase(Locale.ROOT), "");
            wordLines.append("- ").append(w);
            if (StringUtils.hasText(def)) {
                wordLines.append("（").append(def).append("）");
            }
            wordLines.append("\n");
        }
        return "你正在为“知识反刍（Knowledge Rumination）”系统生成深度语境材料。\n"
                + "场景名称：" + scenarioName + "\n"
                + "场景描述：" + scenarioPrompt + "\n"
                + "必须自然融入的词袋目标词汇：\n" + wordLines
                + "请仅输出单个合法 JSON 对象（不要输出 Markdown 代码块），格式如下：\n"
                + "{\n"
                + "  \"title\": \"简短地道的英文标题\",\n"
                + "  \"scenario_summary\": \"1-2句中文概括本材料如何串联目标词\",\n"
                + "  \"article\": \"自然连贯的英文情景短文（150-240词，分2-3段），必须使用上述每个目标词的原始拼写且贴合真实场景逻辑\",\n"
                + "  \"translation\": \"与英文短文逐段对应的流畅中文翻译\",\n"
                + "  \"probes\": [\n"
                + "    {\n"
                + "      \"target_word\": \"从目标词列表中选择1个考查词（原样拼写）\",\n"
                + "      \"probe_type\": \"context_match | collocation | causality 之一\",\n"
                + "      \"question\": \"Pure English comprehension question grounded in the passage; NEVER include any Chinese characters, and NEVER leak the Chinese meaning or directly spoil target_word unless testing collocation\",\n"
                + "      \"options\": [\"Pure English distractor 1\", \"Pure English correct option\", \"Pure English distractor 3\", \"Pure English distractor 4\"],\n"
                + "      \"correct_answer\": \"Exact verbatim string copied from one item in options (pure English)\",\n"
                + "      \"hint\": \"简短中文思维线索，帮助回忆场景功能（仅 hint 字段可用中文）\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "注意：probes 必须恰好包含 " + Math.min(3, targetWords.size())
                + " 道题目，覆盖不同 target_word 且题型依次包含 context_match、collocation、causality；所有探针的 question、options 和 correct_answer 必须为 100% 纯英文（严禁出现任何中文汉字或中英混杂），所有干扰项必须是逼真的英文表达，绝不能出现 unrelated term 等无效占位；正确选项也应使用英文同义释义或语境结果表述，尽量不要直接照抄 target_word 原词以防止蒙对。";
    }

    private String buildScenarioPrompt(String scenarioName, String scenarioPrompt,
                                       List<Map<String, String>> candidateWords) {
        String candidatesJson;
        try {
            candidatesJson = objectMapper.writeValueAsString(candidateWords);
        } catch (Exception e) {
            candidatesJson = "[]";
        }
        return "你正在为“知识反刍 2D 星系图谱”开辟新的主题语境宇宙。\n"
                + "新场景名称：" + scenarioName + "\n"
                + "场景提示描述：" + scenarioPrompt + "\n"
                + "用户词本部分候选词汇清单：" + candidatesJson + "\n"
                + "请仅输出单个合法 JSON 对象（不要输出 Markdown 代码块）：\n"
                + "{\n"
                + "  \"core_words\": [\n"
                + "    { \"term\": \"核心英文词或短语（优先从候选词汇与场景提示中挑选强相关的考纲核心词，结合主题自适应规划最贴合的核心词）\", \"definition\": \"简洁中文释义（2-6字）\" }\n"
                + "  ],\n"
                + "  \"derivations\": [\n"
                + "    { \"term\": \"4D衍生英文词汇\", \"definition\": \"简洁中文释义\", \"dimension\": \"action|downstream|attribute|peer\", \"linked_core\": \"所挂载的核心词term\" }\n"
                + "  ]\n"
                + "}\n"
                + "要求：1. 严格剔除候选词汇中与【" + scenarioName + "】主题无关的干扰词（如医学疾病或厨房蔬果词汇绝不能被强行编入航天/艺术等无关主题），若候选词与新场景无关可完全不用；2. dimension 只能取 action（动作动词）、downstream（下游加工/产物）、attribute（感官特征/工具）、peer（同类关联）四者之一；3. 自适应拓展衍生词，确保每个 linked_core 存在于 core_words 中，选词地道准确、绝不杜撰生造词。";
    }

    private String buildFissionPrompt(String scenarioName, String targetWord,
                                      String targetDefinition, List<String> existingTerms) {
        return "你正在为“知识反刍 2D 星系图谱”的节点执行【4D 衍生裂变】。\n"
                + "所在场景：" + scenarioName + "\n"
                + "目标父节点词汇：" + targetWord + "（释义：" + (StringUtils.hasText(targetDefinition) ? targetDefinition : targetWord) + "）\n"
                + "星系图谱已有词汇（请避开重复）：" + String.join(", ", existingTerms) + "\n"
                + "请仅输出单个合法 JSON 对象（不要输出 Markdown 代码块）：\n"
                + "{\n"
                + "  \"expansions\": [\n"
                + "    { \"term\": \"地道高频关联英文词或短语\", \"definition\": \"简洁中文释义（2-6字）\", \"dimension\": \"action|downstream|attribute|peer\" }\n"
                + "  ]\n"
                + "}\n"
                + "要求：生成 3-4 个最贴合当前场景的 4D 衍生词（动词搭配/下游结果/属性感官/同类词汇），不得与目标词及已有词汇重复。";
    }

    private List<Map<String, String>> buildCandidateSample(List<LearningWordbookEntry> allUserEntries,
                                                             List<LearningWordbookEntry> ruleMatchedEntries) {
        List<Map<String, String>> sample = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();

        for (LearningWordbookEntry e : ruleMatchedEntries) {
            if (sample.size() >= MAX_CANDIDATE_WORDS_FOR_PROMPT) break;
            String term = e.getNormalizedTerm().toLowerCase(Locale.ROOT);
            if (added.add(term)) {
                sample.add(Map.of("term", term, "definition", semanticEvaluator.extractEntryDefinition(e)));
            }
        }
        for (LearningWordbookEntry e : allUserEntries) {
            if (sample.size() >= MAX_CANDIDATE_WORDS_FOR_PROMPT) break;
            String term = e.getNormalizedTerm().toLowerCase(Locale.ROOT);
            if (added.add(term)) {
                sample.add(Map.of("term", term, "definition", semanticEvaluator.extractEntryDefinition(e)));
            }
        }
        return sample;
    }

    private record ProbeParseOutcome(List<ProbeDraft> validProbes, List<String> rejectionReasons) {}

    private record ValidatedMaterialDraft(String title, String summary, String article, String translation,
                                          List<String> missingWords, List<ProbeDraft> validProbes,
                                          List<String> rejectedReasons) {
        boolean needsCorrection() {
            return !missingWords.isEmpty() || !rejectedReasons.isEmpty();
        }

        boolean isBetterThan(ValidatedMaterialDraft baseline) {
            if (this.missingWords.size() != baseline.missingWords.size()) {
                return this.missingWords.size() < baseline.missingWords.size();
            }
            if (this.rejectedReasons.size() != baseline.rejectedReasons.size()) {
                return this.rejectedReasons.size() < baseline.rejectedReasons.size();
            }
            return this.validProbes.size() >= baseline.validProbes.size();
        }
    }

    private ValidatedMaterialDraft evaluateMaterialDraft(
            JsonNode root, String scenarioName, List<String> targetWords,
            Long userId, String scenarioPrompt, Map<String, String> wordDefinitions) {
        String title = nonBlank(root.path("title").asText(), "Living in Context: " + scenarioName + " Vignette");
        String summary = nonBlank(root.path("scenario_summary").asText(root.path("summary").asText()),
                scenarioName + "深度反刍语境，自然贯通了 " + String.join("、", targetWords) + " 等重点词汇。");
        String article = root.path("article").asText("").trim();
        String translation = root.path("translation").asText("").trim();
        if (!StringUtils.hasText(article) || !StringUtils.hasText(translation)) {
            throw new IllegalStateException("AI 返回文章或译文为空");
        }
        List<String> missingWords = findMissingWordsInArticle(article, targetWords);
        ProbeParseOutcome outcome = parseAndValidateProbes(root.path("probes"), targetWords);
        return new ValidatedMaterialDraft(
                title, summary, article, translation, missingWords, outcome.validProbes(), outcome.rejectionReasons());
    }

    private ValidatedMaterialDraft attemptCorrectiveMaterialGeneration(
            Long userId, Long defaultModelConfigId, String scenarioName, String scenarioPrompt,
            List<String> targetWords, Map<String, String> wordDefinitions,
            Map<String, Object> variables, ValidatedMaterialDraft firstAttempt) {
        try {
            StringBuilder correctiveBuilder = new StringBuilder();
            correctiveBuilder.append(buildMaterialPrompt(scenarioName, scenarioPrompt, targetWords, wordDefinitions));
            correctiveBuilder.append("\n\n【上次生成纠偏要求】上一次响应存在以下严格校验未通过的问题，请修正后重新输出完整合法 JSON：\n");
            if (!firstAttempt.missingWords().isEmpty()) {
                correctiveBuilder.append("- 英文正文 `article` 遗漏了目标词（必须全部自然写入正文，可包含合理词形变化）：")
                        .append(String.join(", ", firstAttempt.missingWords())).append("\n");
            }
            if (!firstAttempt.rejectedReasons().isEmpty()) {
                correctiveBuilder.append("- 部分 `probes` 未通过严格校验并被拒收（严禁返回不在目标词列表中的 target_word、答案不在 options 中的题目，或 question/options 含有中文字符的题目）：")
                        .append(String.join("；", firstAttempt.rejectedReasons())).append("\n");
            }
            correctiveBuilder.append("请务必保证：1) 所有目标词 100% 出现在 `article` 正文中；2) 每道探针的 `target_word` 严格等于目标词列表成员；3) `question`、`options` 与 `correct_answer` 必须为 100% 纯英文（不得包含任何中文）；4) `options` 为 4 个互不相同选项且 `correct_answer` 完全命中其中一项。");

            AgentChatResponse retryResponse = invokeDefaultModel(
                    userId,
                    defaultModelConfigId,
                    AiInvocationScene.RUMINATION_MATERIAL,
                    "rumination_material",
                    "知识反刍场景材料-" + scenarioName,
                    correctiveBuilder.toString(),
                    variables
            );
            JsonNode retryRoot = retryResponse.requireStructuredRoot(AiInvocationScene.RUMINATION_MATERIAL);
            return evaluateMaterialDraft(
                    retryRoot, scenarioName, targetWords, userId, scenarioPrompt, wordDefinitions);
        } catch (Exception ex) {
            log.warn("知识反刍 AI 纠偏重生成失败，将保留首轮可用部分或降级处理: scenario={} reason={}",
                    scenarioName, ex.getMessage());
            return null;
        }
    }

    private GeneratedMaterialResult buildFallbackMaterialResult(
            Long userId, String scenarioName, String scenarioPrompt,
            List<String> targetWords, Map<String, String> wordDefinitions) {
        String title = "Living in Context: " + scenarioName + " Vignette";
        String summary = scenarioName + "深度反刍语境，自然贯通了 " + String.join("、", targetWords) + " 等重点词汇。";
        String article = semanticEvaluator.buildScenarioArticle(scenarioName, scenarioPrompt, targetWords);
        String translation = semanticEvaluator.buildScenarioTranslation(scenarioName, scenarioPrompt, targetWords);
        List<ProbeDraft> fallbackProbes = convertFallbackProbes(userId, scenarioName, scenarioPrompt, targetWords, wordDefinitions);
        return new GeneratedMaterialResult(title, summary, article, translation, fallbackProbes, false);
    }

    /** 校验 AI 生成的英文正文是否实际覆盖本次全部目标词汇（支持常见词形变化）。 */
    List<String> findMissingWordsInArticle(String article, List<String> targetWords) {
        if (!StringUtils.hasText(article) || targetWords == null || targetWords.isEmpty()) {
            return targetWords == null ? List.of() : List.copyOf(targetWords);
        }
        String lowerArticle = article.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        for (String rawWord : targetWords) {
            if (!StringUtils.hasText(rawWord)) {
                continue;
            }
            String normalized = rawWord.trim().toLowerCase(Locale.ROOT);
            if (!RuminationWordPatternSupport.buildWordCoveragePattern(normalized).matcher(lowerArticle).find()) {
                missing.add(normalized);
            }
        }
        return List.copyOf(missing);
    }

    private ProbeParseOutcome parseAndValidateProbes(JsonNode probesNode, List<String> targetWords) {
        List<ProbeDraft> result = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();
        if (probesNode == null || !probesNode.isArray() || targetWords.isEmpty()) {
            rejectionReasons.add("probes 数组缺失或格式非法");
            return new ProbeParseOutcome(result, rejectionReasons);
        }
        Set<String> targetSet = targetWords.stream()
                .map(w -> w.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        int index = 0;
        for (JsonNode item : probesNode) {
            index++;
            String rawTarget = item.path("target_word").asText(item.path("targetWord").asText("")).trim().toLowerCase(Locale.ROOT);
            // 严格拒收不属于本次目标词列表的探针，绝不改挂给其他词汇，防止答 A 结算 B 的错位判分
            if (!StringUtils.hasText(rawTarget) || !targetSet.contains(rawTarget)) {
                rejectionReasons.add("第" + index + "题 target_word=" + (rawTarget.isEmpty() ? "<空>" : rawTarget) + " 不属于目标词列表");
                continue;
            }

            String rawType = item.path("probe_type").asText(item.path("probeType").asText("")).trim().toLowerCase(Locale.ROOT);
            String defaultType = result.size() == 0 ? "context_match" : result.size() == 1 ? "collocation" : "causality";
            String probeType = VALID_PROBE_TYPES.contains(rawType) ? rawType : defaultType;

            String question = item.path("question").asText("").trim();
            if (!StringUtils.hasText(question) || containsCjk(question)) {
                rejectionReasons.add("第" + index + "题(" + rawTarget + ") 题干为空或包含非纯英文字符");
                continue;
            }

            List<String> options = new ArrayList<>();
            Set<String> seenLower = new HashSet<>();
            boolean hasCjkOption = false;
            JsonNode optsNode = item.path("options");
            if (optsNode.isArray()) {
                for (JsonNode opt : optsNode) {
                    String optText = opt.asText("").trim();
                    if (containsCjk(optText)) {
                        hasCjkOption = true;
                        continue;
                    }
                    if (StringUtils.hasText(optText) && !isPlaceholderOption(optText)
                            && seenLower.add(optText.toLowerCase(Locale.ROOT))) {
                        options.add(optText);
                    }
                }
            }
            if (options.size() < 4) {
                rejectionReasons.add("第" + index + "题(" + rawTarget + ") "
                        + (hasCjkOption ? "选项包含中文且有效纯英文选项不足4个" : "去重后有效选项不足4个"));
                continue;
            }

            List<String> trimmedOptions = List.copyOf(options.subList(0, 4));
            String rawCorrect = item.path("correct_answer").asText(item.path("correctAnswer").asText("")).trim();
            String correctAnswer = resolveCorrectAnswerStrict(rawCorrect, trimmedOptions);
            if (!StringUtils.hasText(correctAnswer) || containsCjk(correctAnswer)) {
                rejectionReasons.add("第" + index + "题(" + rawTarget + ") correct_answer 未能严格命中纯英文选项");
                continue;
            }

            String hint = item.path("hint").asText("结合文章语境及词义功能仔细推敲。").trim();
            result.add(new ProbeDraft(rawTarget, probeType, question, trimmedOptions, correctAnswer, hint, "ai"));
            if (result.size() >= 3) {
                break;
            }
        }
        return new ProbeParseOutcome(result, rejectionReasons);
    }

    private static boolean containsCjk(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= '\u4e00' && ch <= '\u9fff')
                    || (ch >= '\u3400' && ch <= '\u4dbf')
                    || (ch >= '\u3000' && ch <= '\u303f')
                    || (ch >= '\uff00' && ch <= '\uffef')) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlaceholderOption(String val) {
        String lower = val.toLowerCase(Locale.ROOT);
        return lower.startsWith("干扰选项") || "option a".equals(lower) || "option b".equals(lower)
                || "option c".equals(lower) || "option d".equals(lower) || "n/a".equals(lower);
    }

    private String resolveCorrectAnswerStrict(String rawCorrect, List<String> options) {
        if (!StringUtils.hasText(rawCorrect) || options == null || options.size() < 4) {
            return "";
        }
        for (String opt : options) {
            if (opt.equals(rawCorrect) || opt.equalsIgnoreCase(rawCorrect)) {
                return opt;
            }
        }
        String trimmedUpper = rawCorrect.trim().toUpperCase(Locale.ROOT);
        if ((trimmedUpper.length() == 1 || (trimmedUpper.length() == 2 && (trimmedUpper.charAt(1) == '.' || trimmedUpper.charAt(1) == ')')))
                && trimmedUpper.charAt(0) >= 'A' && trimmedUpper.charAt(0) <= 'D') {
            int letterIdx = trimmedUpper.charAt(0) - 'A';
            if (letterIdx < options.size()) {
                return options.get(letterIdx);
            }
        }
        String strippedRaw = rawCorrect.replaceFirst("^[A-Da-d][\\.\\)\\s]+", "").trim();
        for (String opt : options) {
            String strippedOpt = opt.replaceFirst("^[A-Da-d][\\.\\)\\s]+", "").trim();
            if (strippedOpt.equalsIgnoreCase(strippedRaw) || strippedOpt.equalsIgnoreCase(rawCorrect)) {
                return opt;
            }
        }
        return "";
    }

    private void topUpProbesWithFallback(List<ProbeDraft> probes, Long userId, String scenarioName,
                                         String scenarioPrompt, List<String> targetWords,
                                         Map<String, String> wordDefinitions) {
        List<ProbeDraft> fallback = convertFallbackProbes(userId, scenarioName, scenarioPrompt, targetWords, wordDefinitions);
        Set<String> coveredWords = probes.stream()
                .map(p -> p.targetWord().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> coveredTypes = probes.stream()
                .map(ProbeDraft::probeType)
                .collect(Collectors.toSet());
        int needed = Math.min(3, targetWords.size());
        // 优先补齐尚未被 AI 题目覆盖的目标词与题型，确保补题标注为 fallback
        for (ProbeDraft candidate : fallback) {
            if (probes.size() >= needed) {
                break;
            }
            String wordKey = candidate.targetWord().toLowerCase(Locale.ROOT);
            if (!coveredWords.contains(wordKey) && !coveredTypes.contains(candidate.probeType())) {
                probes.add(candidate);
                coveredWords.add(wordKey);
                coveredTypes.add(candidate.probeType());
            }
        }
        for (ProbeDraft candidate : fallback) {
            if (probes.size() >= needed) {
                break;
            }
            if (!coveredTypes.contains(candidate.probeType())) {
                probes.add(candidate);
                coveredTypes.add(candidate.probeType());
            }
        }
        for (ProbeDraft candidate : fallback) {
            if (probes.size() >= needed) {
                break;
            }
            if (probes.stream().noneMatch(p -> p.question().equals(candidate.question()))) {
                probes.add(candidate);
            }
        }
    }

    private List<ProbeDraft> convertFallbackProbes(Long userId, String scenarioName, String scenarioPrompt,
                                                   List<String> targetWords, Map<String, String> wordDefinitions) {
        List<LearningRuminationProbe> entities = semanticEvaluator.generateContextualProbes(
                userId, 0L, scenarioName, scenarioPrompt, targetWords, wordDefinitions);
        List<ProbeDraft> drafts = new ArrayList<>();
        for (LearningRuminationProbe p : entities) {
            List<String> opts = List.of();
            try {
                opts = objectMapper.readValue(p.getOptionsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception ignored) {
            }
            drafts.add(new ProbeDraft(p.getTargetWord(), p.getProbeType(), p.getQuestion(), opts, p.getCorrectAnswer(), p.getHint(), "fallback"));
        }
        return drafts;
    }

    private String normalizeDimension(String dim) {
        if (!StringUtils.hasText(dim)) {
            return "action";
        }
        String lower = dim.trim().toLowerCase(Locale.ROOT);
        return VALID_DIMENSIONS.contains(lower) ? lower : "action";
    }

    private String nonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
