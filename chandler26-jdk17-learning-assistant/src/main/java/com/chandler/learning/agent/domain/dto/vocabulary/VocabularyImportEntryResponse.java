package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

import java.util.List;

/**
 * 词表导入预览词条。
 */
@Data
public class VocabularyImportEntryResponse {

    private Long id;

    private Integer sourceOrder;

    private String originalTerm;

    private String suggestedTerm;

    private String approvedTerm;

    private String effectiveTerm;

    private String phonetic;

    private String definition;

    private Boolean suspicious;

    private String reviewStatus;

    private List<String> warnings;
}
