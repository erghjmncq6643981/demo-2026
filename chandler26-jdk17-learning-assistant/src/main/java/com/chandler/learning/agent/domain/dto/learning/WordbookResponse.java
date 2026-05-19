package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

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
