package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识反刍场景响应。
 */
@Data
@Schema(name = "知识反刍场景响应")
public class RuminationScenarioResponse {

    @Schema(description = "场景编码，如 produce_kitchen")
    private String code;

    @Schema(description = "场景显示名称，如 生鲜采购与厨房料理")
    private String name;

    @Schema(description = "场景图标，如 🥦")
    private String icon;

    @Schema(description = "场景描述")
    private String description;

    @Schema(description = "当前词本在当前场景下的词汇总数")
    private Integer totalWords;

    @Schema(description = "当前词本在当前场景下待反刍/生疏词数")
    private Integer dueWords;
}
