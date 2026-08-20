package com.chandler.learning.agent.learning.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景材料 Markdown 笔记响应。
 */
@Data
public class SceneMaterialNoteResponse {

    private Long id;

    private Long planId;

    private Long unitId;

    private Long sceneMaterialId;

    private String content;

    private String contentFormat;

    private LocalDateTime updateTime;
}
