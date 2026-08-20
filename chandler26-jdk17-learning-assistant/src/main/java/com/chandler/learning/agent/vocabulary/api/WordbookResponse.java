package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WordbookResponse 类。
 */
@Data
public class WordbookResponse {

    private Long id;

    private String name;

    private String description;

    private Boolean isDefault;

    private Long entryCount;

    private Long dueCount;

    private LocalDateTime createTime;
}
