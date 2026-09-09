package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularySemanticAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 全局语义资产批量存取与数据库会话互斥。 */
@Mapper
public interface VocabularySemanticAssetMapper extends BaseMapper<VocabularySemanticAsset> {
    /** 一次读取当前批次已有资产。 */
    List<VocabularySemanticAsset> findByTerms(@Param("terms") List<String> terms);
    /** 批量保存本次有效结果，显式重新分析时更新共享资产。 */
    int upsertBatch(@Param("list") List<VocabularySemanticAsset> assets);
    /** 持有数据库级互斥，避免跨进程冷词重复调用；不启动事务。 */
    Integer acquireGenerationLock();
    /** 在领取锁的同一连接释放互斥。 */
    Integer releaseGenerationLock();
}
