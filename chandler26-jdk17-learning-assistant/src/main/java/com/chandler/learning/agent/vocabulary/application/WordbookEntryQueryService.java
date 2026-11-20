package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 个人单词本词条查询用例服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordbookEntryQueryService {

    private final LearningWordbookEntryMapper entryMapper;
    private final LearningWordbookMapper wordbookMapper;
    private final WordbookResponseAssembler responseAssembler;

    /**
     * 查询当前用户在个人单词本中收录的该词条（优先默认词本或最近更新）。
     */
    public WordbookEntryResponse findUserEntryByTerm(Long userId, String normalizedTerm) {
        if (userId == null || !StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        String normalized = normalizedTerm.trim().toLowerCase();
        List<LearningWordbookEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getNormalizedTerm, normalized)
                .orderByDesc(LearningWordbookEntry::getUpdateTime));
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        LearningWordbookEntry selected = entries.get(0);
        if (entries.size() > 1) {
            LearningWordbook defaultWb = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                    .eq(LearningWordbook::getUserId, userId)
                    .eq(LearningWordbook::getIsDefault, true)
                    .last(CommonConstants.SQL_LIMIT_ONE));
            if (defaultWb != null) {
                for (LearningWordbookEntry e : entries) {
                    if (defaultWb.getId().equals(e.getWordbookId())) {
                        selected = e;
                        break;
                    }
                }
            }
        }
        return responseAssembler.toEntryResponse(selected);
    }
}
