package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyImportConstants;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalog;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogVersion;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 公共词表对其他业务域开放的只读应用边界。 */
@Service
@RequiredArgsConstructor
public class VocabularyCatalogQueryService {

    private final VocabularyCatalogMapper catalogMapper;
    private final VocabularyCatalogVersionMapper versionMapper;
    private final VocabularyCatalogEntryMapper entryMapper;
    /** 公共词表词条是高频只读数据，短 TTL 避免场景生成反复加载 5000+ 词条。 */
    private final Cache<Long, List<VocabularyCatalogEntry>> publishedEntriesCache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterWrite(45, TimeUnit.SECONDS)
            .build();

    /** 校验已发布版本及其词表访问权限。 */
    public VocabularyCatalogVersion requirePublishedVersion(Long userId, Long versionId) {
        VocabularyCatalogVersion version = versionMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalogVersion>()
                .eq(VocabularyCatalogVersion::getId, versionId)
                .eq(VocabularyCatalogVersion::getStatus, VocabularyImportConstants.VERSION_STATUS_PUBLISHED)
                .eq(VocabularyCatalogVersion::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (version == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.VOCABULARY_CATALOG_NOT_FOUND,
                    "已发布词表版本不存在: " + versionId);
        }
        requireAccessibleCatalog(userId, version.getCatalogId());
        return version;
    }

    /** 校验个人或公共词表的访问权限。 */
    public VocabularyCatalog requireAccessibleCatalog(Long userId, Long catalogId) {
        VocabularyCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalog>()
                .eq(VocabularyCatalog::getId, catalogId)
                .and(wrapper -> wrapper.eq(VocabularyCatalog::getOwnerUserId, userId)
                        .or().eq(VocabularyCatalog::getVisibility,
                                VocabularyImportConstants.VISIBILITY_PUBLIC))
                .eq(VocabularyCatalog::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (catalog == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.VOCABULARY_CATALOG_NOT_FOUND,
                    "词表不存在: " + catalogId);
        }
        return catalog;
    }

    /** 统计已发布的有效词条数。 */
    public int countPublishedEntries(Long versionId) {
        return entryMapper.selectCount(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, versionId)
                .eq(VocabularyCatalogEntry::getPublished, true)
                .eq(VocabularyCatalogEntry::getDeleted, false)).intValue();
    }

    /**
     * 判断计划是否仍有可编排词条。
     * <p>供学习计划完成判定使用，避免在事务内读取并排序整本公共词表。</p>
     */
    public boolean hasAvailableEntriesForPlan(Long planId, Long userId, Long catalogVersionId) {
        if (planId == null || userId == null || catalogVersionId == null) {
            return false;
        }
        return entryMapper.countAvailableForPlan(planId, userId, catalogVersionId) > CommonConstants.ZERO;
    }

    /** 按词表顺序返回版本中的已发布词条。 */
    public List<VocabularyCatalogEntry> listPublishedEntries(Long versionId) {
        List<VocabularyCatalogEntry> cached = publishedEntriesCache.getIfPresent(versionId);
        if (cached != null) {
            // 选词策略会对结果排序，不能把缓存中的 List 暴露给调用方直接修改。
            return new ArrayList<>(cached);
        }
        List<VocabularyCatalogEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, versionId)
                .eq(VocabularyCatalogEntry::getPublished, true)
                .eq(VocabularyCatalogEntry::getDeleted, false)
                .orderByAsc(VocabularyCatalogEntry::getSourceOrder));
        publishedEntriesCache.put(versionId, List.copyOf(entries));
        return new ArrayList<>(entries);
    }

    /** 按归一化词批量查询指定版本词条。 */
    public List<VocabularyCatalogEntry> findByNormalizedTerms(Long versionId, Collection<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        return entryMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, versionId)
                .in(VocabularyCatalogEntry::getNormalizedTerm, terms)
                .eq(VocabularyCatalogEntry::getDeleted, false));
    }

    /** 按条件查询公共词本数据。 */
    public VocabularyCatalogEntry findEntry(Long entryId) {
        return entryId == null ? null : entryMapper.selectById(entryId);
    }

    /** 按条件查询公共词本数据。 */
    public List<VocabularyCatalogEntry> findEntries(Collection<Long> entryIds) {
        return entryIds == null || entryIds.isEmpty() ? List.of() : entryMapper.selectBatchIds(entryIds);
    }
}
