package com.chandler.learning.agent.learning.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 单次复习结果。
 * <p>
 * 每个结果封装自身对复习阶段、掌握度和计数器的影响，服务层只负责组织持久化流程。
 */
@Getter
public enum ReviewResult {

    REMEMBERED(ReviewConstants.RESULT_REMEMBERED, "记住了", ReviewStatus.FAMILIAR) {
        /** 应用复习状态变更。 */
        @Override
        public ReviewOutcome apply(LearningWordbookEntry entry) {
            int stageBefore = entry.reviewStage();
            int masteryBefore = entry.masteryScore();
            int stageAfter = Math.min(stageBefore + CommonConstants.SEQUENCE_STEP,
                    ReviewConstants.INTERVAL_DAYS.length - CommonConstants.SEQUENCE_STEP);
            int masteryAfter = Math.min(ReviewConstants.MAX_MASTERY,
                    masteryBefore + ReviewConstants.REMEMBERED_MASTERY_DELTA);
            entry.recordCorrectReview(stageAfter, masteryAfter, status.getCode());
            return new ReviewOutcome(stageBefore, stageAfter, masteryBefore, masteryAfter);
        }
    },

    VAGUE(ReviewConstants.RESULT_VAGUE, "有点模糊", ReviewStatus.VAGUE) {
        /** 应用复习状态变更。 */
        @Override
        public ReviewOutcome apply(LearningWordbookEntry entry) {
            int stageBefore = entry.reviewStage();
            int masteryBefore = entry.masteryScore();
            int stageAfter = Math.max(CommonConstants.FIRST_SEQUENCE, stageBefore);
            int masteryAfter = Math.max(ReviewConstants.MIN_MASTERY,
                    Math.min(ReviewConstants.MAX_MASTERY,
                            masteryBefore + ReviewConstants.VAGUE_MASTERY_DELTA));
            entry.recordNeutralReview(stageAfter, masteryAfter, status.getCode());
            return new ReviewOutcome(stageBefore, stageAfter, masteryBefore, masteryAfter);
        }
    },

    FORGOTTEN(ReviewConstants.RESULT_FORGOTTEN, "忘记了", ReviewStatus.FORGOTTEN) {
        /** 应用复习状态变更。 */
        @Override
        public ReviewOutcome apply(LearningWordbookEntry entry) {
            int stageBefore = entry.reviewStage();
            int masteryBefore = entry.masteryScore();
            int stageAfter = ReviewConstants.INITIAL_STAGE;
            int masteryAfter = Math.max(ReviewConstants.MIN_MASTERY,
                    masteryBefore - ReviewConstants.FORGOTTEN_MASTERY_DELTA);
            entry.recordWrongReview(stageAfter, masteryAfter, status.getCode());
            return new ReviewOutcome(stageBefore, stageAfter, masteryBefore, masteryAfter);
        }
    };

    private final String code;
    private final String label;
    protected final ReviewStatus status;

    ReviewResult(String code, String label, ReviewStatus status) {
        this.code = code;
        this.label = label;
        this.status = status;
    }

    /** 应用复习状态变更。 */
    public abstract ReviewOutcome apply(LearningWordbookEntry entry);

    /** 按编码解析对应的业务枚举。 */
    public static ReviewResult of(String code) {
        String normalized = StrUtil.blankToDefault(code, FORGOTTEN.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(result -> result.code.equals(normalized))
                .findFirst()
                .orElse(FORGOTTEN);
    }

    /** 判断复习结果是否为记住。 */
    public boolean remembered() {
        return this == REMEMBERED;
    }

    /** 判断复习结果是否为模糊。 */
    public boolean vague() {
        return this == VAGUE;
    }

    /**
     * 单次复习计算结果，记录复习前后阶段与掌握度。
     */
    public record ReviewOutcome(int stageBefore, int stageAfter, int masteryBefore, int masteryAfter) {
    }
}
