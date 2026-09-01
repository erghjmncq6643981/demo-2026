package com.chandler.learning.agent.learning.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 场景材料生成任务第一步（确定学习词组）锁定的词组批次检查点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreparedVocabularyBatch {

    /** 学习计划 ID。 */
    private Long planId;

    /** 建议学习日期。 */
    private LocalDate recommendedDate;

    /** 本次计划生成的总词数。 */
    private Integer totalToGenerate;

    /** 拆分后的各单元词组列表。 */
    private List<PreparedUnitGroup> unitGroups;

    /** 汇总当前批次锁定的所有核心词 CatalogEntry ID 列表。 */
    public List<Long> allCandidateEntryIds() {
        if (unitGroups == null || unitGroups.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (PreparedUnitGroup group : unitGroups) {
            if (group.getCandidateEntryIds() != null) {
                result.addAll(group.getCandidateEntryIds());
            }
        }
        return List.copyOf(result);
    }
}
