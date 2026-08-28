package com.chandler.learning.agent.vocabulary.domain.bo;

import lombok.Data;

/**
 * 词卡任务明细的数据库聚合结果，避免为计算进度加载全部明细。
 */
@Data
public class VocabularyCardGenerationProgress {

    /** 任务明细总数。 */
    private Integer totalCount;

    /** 已成功或命中缓存的明细数。 */
    private Integer successCount;

    /** 失败明细数。 */
    private Integer failedCount;
}
