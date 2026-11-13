package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.ai.chat.application.codec.AiSceneResponse;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.ai.model.domain.entity.AiModelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuminationAiGeneratorTest {

    @Mock
    private AiChatService aiChatService;
    @Mock
    private AiModelConfigService aiModelConfigService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Spy
    private RuminationSemanticEvaluator semanticEvaluator = new RuminationSemanticEvaluator(new ObjectMapper());

    @InjectMocks
    private RuminationAiGenerator aiGenerator;

    @Test
    @DisplayName("默认 AI 模型生成场景材料与探针时绑定系统默认 modelConfigId 并解析字母答案")
    void generateMaterialAndProbes_usesDefaultModelAndNormalizesProbes() throws Exception {
        AiModelConfig defaultConfig = new AiModelConfig();
        defaultConfig.setId(2056646642698702850L);
        defaultConfig.setModelName("deepseek-v4-flash");
        when(aiModelConfigService.getDefaultEnabled()).thenReturn(defaultConfig);

        String json = """
                {
                  "title": "Chef's Fresh Morning Table",
                  "scenario_summary": "串联厨房食材处理高频词",
                  "article": "At dawn, the chef inspected every potato, onion, and tomato before beginning to peel and boil the fresh produce.",
                  "translation": "清晨，大厨仔细检查了每个土豆、洋葱和番茄，然后开始为新鲜食材削皮并水煮。",
                  "probes": [
                    {
                      "target_word": "peel",
                      "probe_type": "context_match",
                      "question": "Which culinary preparation step removes the outer layer of the root vegetable?",
                      "options": ["remove the skin", "simmer gently in oil", "chill in ice water", "slice thinly"],
                      "correct_answer": "A",
                      "hint": "思考处理土豆或胡萝卜表皮的动作"
                    }
                  ]
                }
                """;
        AgentChatResponse chatResponse = new AgentChatResponse();
        chatResponse.setModelProvider("deepseek");
        chatResponse.setModelName("deepseek-v4-flash");
        chatResponse.setContent(json);
        chatResponse.setStructuredResponse(new AiSceneResponse(
                AiInvocationScene.RUMINATION_MATERIAL,
                objectMapper.readTree(json),
                json,
                "deepseek_json",
                "strict",
                List.of()
        ));
        when(aiChatService.chat(any(AgentChatRequest.class))).thenReturn(chatResponse);

        RuminationAiGenerator.GeneratedMaterialResult result = aiGenerator.generateMaterialAndProbes(
                1001L,
                "生鲜采购与厨房料理",
                "日常料理场景",
                List.of("potato", "onion", "tomato", "peel", "boil"),
                Map.of("peel", "削皮")
        );

        ArgumentCaptor<AgentChatRequest> reqCaptor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(aiChatService).chat(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getModelConfigId()).isEqualTo(2056646642698702850L);
        assertThat(reqCaptor.getValue().getInvocationScene()).isEqualTo(AiInvocationScene.RUMINATION_MATERIAL);

        assertThat(result.aiPowered()).isTrue();
        assertThat(result.title()).isEqualTo("Chef's Fresh Morning Table");
        // AI 仅给出 1 题时应自动补齐至 3 题，字母 "A" 已映射为完整选项文本，且区分 ai / fallback 来源
        assertThat(result.probes()).hasSize(3);
        assertThat(result.probes().get(0).correctAnswer()).isEqualTo("remove the skin");
        assertThat(result.probes().get(0).generationSource()).isEqualTo("ai");
        assertThat(result.probes().get(1).generationSource()).isEqualTo("fallback");
        assertThat(result.probes().get(2).generationSource()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("默认 AI 模型异常时自动优雅降级本地语义引擎")
    void generateMaterialAndProbes_aiFails_fallsBackGracefully() {
        when(aiModelConfigService.getDefaultEnabled()).thenReturn(null);
        when(aiChatService.chat(any(AgentChatRequest.class))).thenThrow(new RuntimeException("Provider timeout"));

        RuminationAiGenerator.GeneratedMaterialResult result = aiGenerator.generateMaterialAndProbes(
                1001L,
                "医院就医",
                "急诊问诊",
                List.of("fever", "cough", "headache", "pill", "doctor"),
                Map.of("fever", "发烧", "cough", "咳嗽", "headache", "头痛", "pill", "药片", "doctor", "医生")
        );

        assertThat(result.aiPowered()).isFalse();
        assertThat(result.article()).contains("fever");
        assertThat(result.probes()).hasSize(3);
        assertThat(result.probes()).allSatisfy(p -> {
            assertThat(p.generationSource()).isEqualTo("fallback");
            assertThat(p.question()).doesNotContainPattern("[\\u4e00-\\u9fff]");
            assertThat(p.correctAnswer()).doesNotContainPattern("[\\u4e00-\\u9fff]");
            assertThat(p.options()).hasSize(4).allSatisfy(opt ->
                    assertThat(opt).doesNotContainPattern("[\\u4e00-\\u9fff]"));
        });
    }

    @Test
    @DisplayName("探针返回不在目标词列表的 target_word 时严格拒收不改挂，并触发一次纠偏重生成")
    void generateMaterialAndProbes_rejectsOffListProbeTargetAndRetriesOnce() throws Exception {
        AiModelConfig defaultConfig = new AiModelConfig();
        defaultConfig.setId(2056646642698702850L);
        when(aiModelConfigService.getDefaultEnabled()).thenReturn(defaultConfig);

        String attempt1Json = """
                {
                  "title": "Morning Kitchen Routine",
                  "scenario_summary": "summary",
                  "article": "We wash potato, slice onion, dice tomato, peel garlic, and boil fresh herbs.",
                  "translation": "我们洗土豆、切洋葱、切番茄、剥蒜并水煮新鲜香草。",
                  "probes": [
                    {
                      "target_word": "steak",
                      "probe_type": "context_match",
                      "question": "Which step grills steak fast?",
                      "options": ["sear on high heat", "freeze it overnight", "boil for soup", "chop into salad"],
                      "correct_answer": "sear on high heat",
                      "hint": "烹饪牛排"
                    }
                  ]
                }
                """;
        String attempt2Json = """
                {
                  "title": "Morning Kitchen Routine",
                  "scenario_summary": "summary",
                  "article": "We wash potato, slice onion, dice tomato, peel garlic, and boil fresh herbs.",
                  "translation": "我们洗土豆、切洋葱、切番茄、剥蒜并水煮新鲜香草。",
                  "probes": [
                    {
                      "target_word": "peel",
                      "probe_type": "context_match",
                      "question": "Which step strips the outer skin cleanly?",
                      "options": ["remove the skin", "boil water", "bake bread", "chill broth"],
                      "correct_answer": "remove the skin",
                      "hint": "去皮操作"
                    },
                    {
                      "target_word": "boil",
                      "probe_type": "collocation",
                      "question": "How do herbs soften in hot water?",
                      "options": ["boil briefly in pot", "freeze solid", "pack dry leaves", "store cold"],
                      "correct_answer": "boil briefly in pot",
                      "hint": "沸水加热"
                    }
                  ]
                }
                """;
        AgentChatResponse response1 = buildStructuredChatResponse(attempt1Json);
        AgentChatResponse response2 = buildStructuredChatResponse(attempt2Json);
        when(aiChatService.chat(any(AgentChatRequest.class)))
                .thenReturn(response1, response2);

        List<String> targets = List.of("potato", "onion", "tomato", "peel", "boil");
        RuminationAiGenerator.GeneratedMaterialResult result = aiGenerator.generateMaterialAndProbes(
                1001L, "生鲜采购与厨房料理", "日常料理场景", targets, Map.of("peel", "削皮", "boil", "水煮"));

        ArgumentCaptor<AgentChatRequest> reqCaptor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(aiChatService, times(2)).chat(reqCaptor.capture());
        assertThat(reqCaptor.getAllValues().get(1).getMessage()).contains("target_word=steak");

        assertThat(result.aiPowered()).isTrue();
        assertThat(result.probes()).hasSize(3);
        assertThat(result.probes().stream().map(RuminationAiGenerator.ProbeDraft::targetWord))
                .doesNotContain("steak")
                .allSatisfy(w -> assertThat(targets).contains(w));
        assertThat(result.probes().get(0).generationSource()).isEqualTo("ai");
        assertThat(result.probes().get(1).generationSource()).isEqualTo("ai");
        assertThat(result.probes().get(2).generationSource()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("文章纠偏重生成后仍漏目标词时整篇降级本地语义引擎保障覆盖率")
    void generateMaterialAndProbes_missingTargetWordAfterRetry_fallsBackToSemanticEngine() throws Exception {
        AiModelConfig defaultConfig = new AiModelConfig();
        defaultConfig.setId(2056646642698702850L);
        when(aiModelConfigService.getDefaultEnabled()).thenReturn(defaultConfig);

        // 两次均遗漏了 boil 与 tomato
        String incompleteJson = """
                {
                  "title": "Incomplete Story",
                  "scenario_summary": "summary",
                  "article": "The cook washed potato and chopped onion quickly.",
                  "translation": "厨师洗了土豆并快速切了洋葱。",
                  "probes": [
                    {
                      "target_word": "potato",
                      "probe_type": "context_match",
                      "question": "Which root item was washed first?",
                      "options": ["starchy tuber", "sweet berry", "green leaf", "dairy milk"],
                      "correct_answer": "starchy tuber",
                      "hint": "根茎食材"
                    }
                  ]
                }
                """;
        AgentChatResponse incompleteResp1 = buildStructuredChatResponse(incompleteJson);
        AgentChatResponse incompleteResp2 = buildStructuredChatResponse(incompleteJson);
        when(aiChatService.chat(any(AgentChatRequest.class)))
                .thenReturn(incompleteResp1, incompleteResp2);

        List<String> targets = List.of("potato", "onion", "tomato", "peel", "boil");
        RuminationAiGenerator.GeneratedMaterialResult result = aiGenerator.generateMaterialAndProbes(
                1001L, "生鲜采购与厨房料理", "日常料理场景", targets,
                Map.of("potato", "土豆", "onion", "洋葱", "tomato", "番茄", "peel", "削皮", "boil", "水煮"));

        ArgumentCaptor<AgentChatRequest> reqCaptor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(aiChatService, times(2)).chat(reqCaptor.capture());
        assertThat(reqCaptor.getAllValues().get(1).getMessage()).contains("tomato", "peel", "boil");

        assertThat(result.aiPowered()).isFalse();
        assertThat(result.article().toLowerCase()).contains("potato", "onion", "tomato", "peel", "boil");
        assertThat(result.probes()).allSatisfy(p -> assertThat(p.generationSource()).isEqualTo("fallback"));
    }

    @Test
    @DisplayName("正确答案不在 options 列表中时严格拒收该错题并使用本地补题")
    void generateMaterialAndProbes_invalidAnswerOption_rejectedAndToppedUpAsFallback() throws Exception {
        AiModelConfig defaultConfig = new AiModelConfig();
        defaultConfig.setId(2056646642698702850L);
        when(aiModelConfigService.getDefaultEnabled()).thenReturn(defaultConfig);

        String invalidAnswerJson = """
                {
                  "title": "Complete Story",
                  "scenario_summary": "summary",
                  "article": "We peeled potatoes, sliced onions, diced tomatoes, and started to boil soup.",
                  "translation": "我们削了土豆皮、切了洋葱、切了番茄丁并开始煮汤。",
                  "probes": [
                    {
                      "target_word": "peel",
                      "probe_type": "context_match",
                      "question": "Which step removes potato skin?",
                      "options": ["remove outer skin", "chill overnight", "grill dry", "freeze hard"],
                      "correct_answer": "totally unrelated text",
                      "hint": "去皮"
                    }
                  ]
                }
                """;
        AgentChatResponse invalidResp1 = buildStructuredChatResponse(invalidAnswerJson);
        AgentChatResponse invalidResp2 = buildStructuredChatResponse(invalidAnswerJson);
        when(aiChatService.chat(any(AgentChatRequest.class)))
                .thenReturn(invalidResp1, invalidResp2);

        RuminationAiGenerator.GeneratedMaterialResult result = aiGenerator.generateMaterialAndProbes(
                1001L, "生鲜采购与厨房料理", "日常料理场景",
                List.of("potato", "onion", "tomato", "peel", "boil"),
                Map.of("potato", "土豆", "onion", "洋葱", "tomato", "番茄", "peel", "削皮", "boil", "水煮"));

        assertThat(result.aiPowered()).isTrue();
        assertThat(result.probes()).hasSize(3);
        assertThat(result.probes()).noneMatch(p -> "totally unrelated text".equals(p.correctAnswer()));
        assertThat(result.probes()).allSatisfy(p -> assertThat(p.generationSource()).isEqualTo("fallback"));
    }

    @Test
    @DisplayName("探针题干或选项包含中文字符时严格拒收并触发纯英文纠偏重生成")
    void generateMaterialAndProbes_rejectsChineseInProbeQuestionOrOptions() throws Exception {
        AiModelConfig defaultConfig = new AiModelConfig();
        defaultConfig.setId(2056646642698702850L);
        when(aiModelConfigService.getDefaultEnabled()).thenReturn(defaultConfig);

        String mixedLangJson = """
                {
                  "title": "Market Morning",
                  "scenario_summary": "summary",
                  "article": "We peeled potatoes, sliced onions, diced tomatoes, and started to boil soup.",
                  "translation": "我们削了土豆皮、切了洋葱、切了番茄丁并开始煮汤。",
                  "probes": [
                    {
                      "target_word": "peel",
                      "probe_type": "context_match",
                      "question": "在生鲜采购中 peel 的作用是什么？",
                      "options": ["remove outer skin", "削皮", "grill dry", "freeze hard"],
                      "correct_answer": "remove outer skin",
                      "hint": "去皮操作"
                    }
                  ]
                }
                """;
        String pureEnglishJson = """
                {
                  "title": "Market Morning",
                  "scenario_summary": "summary",
                  "article": "We peeled potatoes, sliced onions, diced tomatoes, and started to boil soup.",
                  "translation": "我们削了土豆皮、切了洋葱、切了番茄丁并开始煮汤。",
                  "probes": [
                    {
                      "target_word": "peel",
                      "probe_type": "context_match",
                      "question": "Which preparation step strips off the outer skin of the root vegetable?",
                      "options": ["remove outer skin cleanly", "chill overnight in ice", "dry-roast whole in oven", "freeze hard before slicing"],
                      "correct_answer": "remove outer skin cleanly",
                      "hint": "去皮操作"
                    }
                  ]
                }
                """;
        AgentChatResponse r1 = buildStructuredChatResponse(mixedLangJson);
        AgentChatResponse r2 = buildStructuredChatResponse(pureEnglishJson);
        when(aiChatService.chat(any(AgentChatRequest.class)))
                .thenReturn(r1, r2);

        RuminationAiGenerator.GeneratedMaterialResult result = aiGenerator.generateMaterialAndProbes(
                1001L, "生鲜采购与厨房料理", "日常料理场景",
                List.of("potato", "onion", "tomato", "peel", "boil"),
                Map.of("potato", "土豆", "onion", "洋葱", "tomato", "番茄", "peel", "削皮", "boil", "水煮"));

        ArgumentCaptor<AgentChatRequest> reqCaptor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(aiChatService, times(2)).chat(reqCaptor.capture());
        assertThat(reqCaptor.getAllValues().get(1).getMessage()).contains("100% 纯英文");

        assertThat(result.probes()).hasSize(3);
        assertThat(result.probes()).allSatisfy(p -> {
            assertThat(p.question()).doesNotContainPattern("[\\u4e00-\\u9fff]");
            assertThat(p.options()).allSatisfy(opt ->
                    assertThat(opt).doesNotContainPattern("[\\u4e00-\\u9fff]"));
        });
        assertThat(result.probes().get(0).generationSource()).isEqualTo("ai");
        assertThat(result.probes().get(0).question()).contains("outer skin");
    }

    private AgentChatResponse buildStructuredChatResponse(String json) throws Exception {
        AgentChatResponse chatResponse = new AgentChatResponse();
        chatResponse.setModelProvider("deepseek");
        chatResponse.setModelName("deepseek-v4-flash");
        chatResponse.setContent(json);
        chatResponse.setStructuredResponse(new AiSceneResponse(
                AiInvocationScene.RUMINATION_MATERIAL,
                objectMapper.readTree(json),
                json,
                "deepseek_json",
                "strict",
                List.of()
        ));
        return chatResponse;
    }
}
