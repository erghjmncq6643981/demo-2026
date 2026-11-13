package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识反刍星系图谱保存与增量更新统一响应。
 */
@Data
@Schema(name = "知识反刍星系保存响应")
public class RuminationGraphSaveResponse {

    @Schema(description = "个人单词本 ID（作为字符串传输）")
    private String wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "落库后的最新星系图谱版本号")
    private Integer version;

    @Schema(description = "落库后的当前词袋词汇列表")
    private List<String> bagTerms;
}
