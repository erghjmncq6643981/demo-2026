package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 单次复习结果。
 * <p>
 * 每个结果封装自身对复习阶段、掌握度和计数器的影响，服务层只负责组织持久化流程。
 */
@Getter
public enum ReviewResult {

    REMEMBERED(LearningConstants.Review.RESULT_REMEMBERED, "记住了", ReviewStatus.FAMILIAR) {
        /**
         * 更新 {@code apply} 相关业务。
         */
        @Override
        public ReviewOutcome apply(LearningWordbookEntry entry) {
            int stageBefore = entry.reviewStage();
            int masteryBefore = entry.masteryScore();
            int stageAfter = Math.min(stageBefore + LearningConstants.SEQUENCE_STEP,
                    LearningConstants.Review.INTERVAL_DAYS.length - LearningConstants.SEQUENCE_STEP);
            int masteryAfter = Math.min(LearningConstants.Review.MAX_MASTERY,
                    masteryBefore + LearningConstants.Review.REMEMBERED_MASTERY_DELTA);
            entry.recordCorrectReview(stageAfter, masteryAfter, status.getCode());
            return new ReviewOutcome(stageBefore, stageAfter, masteryBefore, masteryAfter);
        }
    },

    VAGUE(LearningConstants.Review.RESULT_VAGUE, "有点模糊", ReviewStatus.VAGUE) {
        /**
         * 更新 {@code apply} 相关业务。
         */
        @Override
        public ReviewOutcome apply(LearningWordbookEntry entry) {
            int stageBefore = entry.reviewStage();
            int masteryBefore = entry.masteryScore();
            int stageAfter = Math.max(LearningConstants.FIRST_SEQUENCE, stageBefore);
            int masteryAfter = Math.max(LearningConstants.Review.MIN_MASTERY,
                    Math.min(LearningConstants.Review.MAX_MASTERY,
                            masteryBefore + LearningConstants.Review.VAGUE_MASTERY_DELTA));
            entry.recordNeutralReview(stageAfter, masteryAfter, status.getCode());
            return new ReviewOutcome(stageBefore, stageAfter, masteryBefore, masteryAfter);
        }
    },

    FORGOTTEN(LearningConstants.Review.RESULT_FORGOTTEN, "忘记了", ReviewStatus.FORGOTTEN) {
        /**
         * 更新 {@code apply} 相关业务。
         */
        @Override
        public ReviewOutcome apply(LearningWordbookEntry entry) {
            int stageBefore = entry.reviewStage();
            int masteryBefore = entry.masteryScore();
            int stageAfter = LearningConstants.Review.INITIAL_STAGE;
            int masteryAfter = Math.max(LearningConstants.Review.MIN_MASTERY,
                    masteryBefore - LearningConstants.Review.FORGOTTEN_MASTERY_DELTA);
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

    /**
     * 更新 {@code apply} 相关业务。
     */
    public abstract ReviewOutcome apply(LearningWordbookEntry entry);

    /**
     * 处理 {@code of} 相关业务。
     */
    public static ReviewResult of(String code) {
        String normalized = StrUtil.blankToDefault(code, FORGOTTEN.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(result -> result.code.equals(normalized))
                .findFirst()
                .orElse(FORGOTTEN);
    }

    /**
     * 判断 {@code remembered} 相关业务。
     */
    public boolean remembered() {
        return this == REMEMBERED;
    }

    /**
     * 判断 {@code vague} 相关业务。
     */
    public boolean vague() {
        return this == VAGUE;
    }

    /**
     * 单次复习计算结果，记录复习前后阶段与掌握度。
     */
    public record ReviewOutcome(int stageBefore, int stageAfter, int masteryBefore, int masteryAfter) {
    }
}
