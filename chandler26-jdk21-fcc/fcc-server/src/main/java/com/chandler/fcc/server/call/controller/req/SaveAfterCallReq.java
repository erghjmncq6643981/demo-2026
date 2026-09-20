package com.chandler.fcc.server.call.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 坐席话后小结保存请求。
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "坐席话后小结保存请求")
public class SaveAfterCallReq {

    /**
     * 业务分类。
     */
    @Schema(description = "业务分类，最多 64 个字符", example = "产品咨询")
    private String category;

    /**
     * 客户意向等级。
     */
    @Schema(description = "客户意向等级：HIGH、MID、LOW 或 UNASSESSED", example = "HIGH")
    private String intent;

    /**
     * 沟通纪要。
     */
    @Schema(description = "沟通纪要，最多 2000 个字符", example = "客户希望明天下午再次联系")
    private String notes;
}
