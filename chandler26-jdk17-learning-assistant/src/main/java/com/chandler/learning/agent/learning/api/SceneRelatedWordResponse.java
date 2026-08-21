package com.chandler.learning.agent.learning.api;

import lombok.Data;

/** 场景相关词展示对象。 */
@Data
public class SceneRelatedWordResponse {
    private Long id;
    private Long sceneMaterialId;
    private String term;
    private String normalizedTerm;
    private String phonetic;
    private String meaning;
    private String contextMeaning;
    private String categoryCode;
    private String categoryName;
    private Boolean promoted;
    private Long promotedEntryId;
}
