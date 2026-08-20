package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.learning.domain.LearningReviewRecord;
import com.chandler.learning.agent.learning.infrastructure.LearningReviewRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 向其他业务域提供复习记录的写入与查询边界。 */
@Service
@RequiredArgsConstructor
public class LearningReviewService {

    private final LearningReviewRecordMapper reviewRecordMapper;

    /** 查询用户指定时间之后的复习记录。 */
    public List<LearningReviewRecord> listSince(Long userId, LocalDateTime startTime) {
        return reviewRecordMapper.selectList(new LambdaQueryWrapper<LearningReviewRecord>()
                .eq(LearningReviewRecord::getUserId, userId)
                .ge(LearningReviewRecord::getCreateTime, startTime));
    }

    /** 保存一次复习结果。 */
    public void record(LearningReviewRecord record) {
        reviewRecordMapper.insert(record);
    }
}
