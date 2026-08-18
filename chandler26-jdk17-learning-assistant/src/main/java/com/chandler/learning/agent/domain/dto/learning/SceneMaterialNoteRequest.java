package com.chandler.learning.agent.domain.dto.learning;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 场景材料 Markdown 笔记保存请求。
 */
@Data
public class SceneMaterialNoteRequest {

    @Size(max = 20000, message = "场景笔记不能超过 20000 个字符")
    private String content;
}
