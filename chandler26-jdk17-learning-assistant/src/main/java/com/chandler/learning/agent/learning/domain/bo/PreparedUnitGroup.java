package com.chandler.learning.agent.learning.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单元已分配的词组检查点模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreparedUnitGroup {

    /** 单元内序号（从 0 开始）。 */
    private Integer unitIndex;

    /** 核心词 CatalogEntry ID 列表。 */
    private List<Long> candidateEntryIds;

    /** 复习词 CatalogEntry ID 列表。 */
    private List<Long> reviewEntryIds;

    /** 目标核心词数量。 */
    private Integer targetCount;
}
