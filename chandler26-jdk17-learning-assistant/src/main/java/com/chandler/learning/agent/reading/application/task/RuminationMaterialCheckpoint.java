package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.application.RuminationAiGenerator;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 知识反刍材料生成步骤 1 产出的可恢复检查点快照。
 *
 * @param generationSource 内容生成来源：ai 默认大模型生成，fallback 本地语义引擎降级
 * @param title            英文场景材料标题
 * @param summary          场景语境背景概述
 * @param article          英文原文正文
 * @param translation      中文对照翻译
 * @param probes           校验通过后的微环境探针草稿列表
 */
public record RuminationMaterialCheckpoint(
        String generationSource,
        String title,
        String summary,
        String article,
        String translation,
        List<ProbeCheckpoint> probes
) {

    /** 将检查点快照还原为统一的生成结果模型，供步骤 2 幂等落库使用。 */
    public RuminationAiGenerator.GeneratedMaterialResult toGeneratedResult() {
        boolean aiPowered = !"fallback".equalsIgnoreCase(generationSource);
        List<RuminationAiGenerator.ProbeDraft> drafts = probes == null ? List.of() : probes.stream()
                .map(p -> new RuminationAiGenerator.ProbeDraft(
                        p.targetWord(),
                        p.probeType(),
                        p.question(),
                        p.options(),
                        p.correctAnswer(),
                        p.hint(),
                        StringUtils.hasText(p.generationSource())
                                ? p.generationSource()
                                : (aiPowered ? "ai" : "fallback")))
                .toList();
        return new RuminationAiGenerator.GeneratedMaterialResult(
                title, summary, article, translation, drafts, aiPowered);
    }

    /** 探针题目检查点单项快照。 */
    public record ProbeCheckpoint(
            String targetWord,
            String probeType,
            String question,
            List<String> options,
            String correctAnswer,
            String hint,
            String generationSource
    ) {
        public ProbeCheckpoint(String targetWord, String probeType, String question, List<String> options,
                               String correctAnswer, String hint) {
            this(targetWord, probeType, question, options, correctAnswer, hint, null);
        }
    }
}
