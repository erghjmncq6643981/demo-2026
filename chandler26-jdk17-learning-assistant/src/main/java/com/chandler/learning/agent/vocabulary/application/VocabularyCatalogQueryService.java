package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;

/** 公共词表对其他业务域开放的只读应用边界。 */
@Service
@RequiredArgsConstructor
public class VocabularyCatalogQueryService {

    private final VocabularyCatalogMapper catalogMapper;
    private final VocabularyCatalogVersionMapper versionMapper;
    private final VocabularyCatalogEntryMapper entryMapper;

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

    /** 按词表顺序返回版本中的已发布词条。 */
    public List<VocabularyCatalogEntry> listPublishedEntries(Long versionId) {
        return entryMapper.selectList(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, versionId)
                .eq(VocabularyCatalogEntry::getPublished, true)
                .eq(VocabularyCatalogEntry::getDeleted, false)
                .orderByAsc(VocabularyCatalogEntry::getSourceOrder));
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
