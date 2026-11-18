package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuminationSemanticEvaluatorTest {

    private final RuminationSemanticEvaluator evaluator = new RuminationSemanticEvaluator(new ObjectMapper());

    @Test
    @DisplayName("语义评估器：人体部位与器官场景精准匹配生理器官词汇，彻底排除全体、体贴、流体等单字误伤")
    void evaluateRelevance_bodyPartsScenario_matchesTrueOrgansAndRejectsFalsePositives() {
        LearningWordbookEntry heart = createEntry("heart", "名词", "心脏；核心");
        LearningWordbookEntry brain = createEntry("brain", "名词", "大脑；智力");
        LearningWordbookEntry lung = createEntry("lung", "名词", "肺；呼吸器官");
        LearningWordbookEntry muscle = createEntry("muscle", "名词", "肌肉；力量");
        LearningWordbookEntry bone = createEntry("bone", "名词", "骨骼；骨头");
        LearningWordbookEntry blood = createEntry("blood", "名词", "血液；血统");
        LearningWordbookEntry stomach = createEntry("stomach", "名词", "胃；胃部；腹部");

        // 包含 "体"、"人" 等虚词单字误伤词
        LearningWordbookEntry everyone = createEntry("everyone", "代词", "全体人员；每个人");
        LearningWordbookEntry all = createEntry("all", "形容词", "全部的；全体的");
        LearningWordbookEntry considerate = createEntry("considerate", "形容词", "体贴的；体谅的");
        LearningWordbookEntry fluid = createEntry("fluid", "名词", "流体；液体");
        LearningWordbookEntry mankind = createEntry("mankind", "名词", "人类总体");
        LearningWordbookEntry accountant = createEntry("accountant", "名词", "会计人员");

        List<LearningWordbookEntry> entries = List.of(
                heart, brain, lung, muscle, bone, blood, stomach,
                everyone, all, considerate, fluid, mankind, accountant
        );

        List<RuminationSemanticEvaluator.ScoredEntry> scored = evaluator.evaluateRelevance(
                entries, "人体部位与器官", "自定义语境高关联词汇生态"
        );

        // 高关联核心词必须是真实身体部位器官词汇
        List<String> topMatches = scored.stream()
                .filter(se -> se.relevanceScore() >= 0.40)
                .map(se -> se.entry().getNormalizedTerm())
                .toList();

        assertThat(topMatches).contains("heart", "brain", "lung", "muscle", "bone", "blood", "stomach");
        assertThat(topMatches).doesNotContain("everyone", "all", "considerate", "fluid", "mankind", "accountant");
    }

    private LearningWordbookEntry createEntry(String term, String pos, String def) {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setNormalizedTerm(term);
        entry.setTerm(term);
        entry.setSnapshotParsedJson("{\"definitions\":[{\"pos\":\"" + pos + "\",\"meaning\":\"" + def + "\"}]}");
        return entry;
    }
}
