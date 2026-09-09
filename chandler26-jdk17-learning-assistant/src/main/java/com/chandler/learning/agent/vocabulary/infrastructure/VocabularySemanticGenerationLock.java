package com.chandler.learning.agent.vocabulary.infrastructure;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularySemanticAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.function.Supplier;

/** 冷词生成互斥；独立自动提交连接持锁，AI 调用期间不持有数据库事务或行锁。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VocabularySemanticGenerationLock {
    private final SqlSessionFactory sessionFactory;

    /** 同一数据库的冷词批次依次处理；等待超时交给原任务的失败重试机制。 */
    public <T> T execute(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw LearningAssistantException.badRequest(LearningErrorCode.SYSTEM_UNEXPECTED,
                    "词汇语义生成不能在数据库事务中执行");
        }
        try (var session = sessionFactory.openSession(true)) {
            var mapper = session.getMapper(VocabularySemanticAssetMapper.class);
            if (!Integer.valueOf(1).equals(mapper.acquireGenerationLock())) {
                throw LearningAssistantException.badRequest(LearningErrorCode.VOCABULARY_SEMANTIC_BUSY);
            }
            try {
                return action.get();
            } finally {
                try {
                    if (!Integer.valueOf(1).equals(mapper.releaseGenerationLock())) {
                        session.getConnection().abort(Runnable::run);
                    }
                } catch (Exception ex) {
                    // 不让锁清理故障覆盖已保存的成功结果，并销毁连接避免池中遗留锁。
                    log.warn("全局词汇分析互斥释放异常，关闭持锁连接");
                    log.debug("全局词汇分析互斥释放失败", ex);
                    try {
                        session.getConnection().abort(Runnable::run);
                    } catch (Exception abortError) {
                        log.debug("持锁连接关闭失败", abortError);
                    }
                }
            }
        }
    }
}
