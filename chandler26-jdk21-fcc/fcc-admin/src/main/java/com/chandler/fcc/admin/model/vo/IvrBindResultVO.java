package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 实体话机 0000 语音工号自助绑定响应视图
 * <p>
 * 包含绑定状态与用于 IVR 语音合成播报的 promptMessage 提示语。
 * 若工号不存在，返回 promptMessage = "该工号不存在，请重新输入"，驱动 IVR 重新收号。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "话机语音绑定结果视图")
public class IvrBindResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否绑定成功", example = "true")
    private Boolean success;

    @Schema(description = "业务响应码 (200-成功, 404-工号不存在, 400-分机不存在)", example = "200")
    private Integer code;

    @Schema(description = "分机号", example = "1001")
    private String extension;

    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    @Schema(description = "坐席姓名", example = "钱丁君")
    private String agentName;

    @Schema(description = "IVR 播报提示语 (工号不存在或成功提示)", example = "绑定成功，工号901001，祝你工作愉快")
    private String promptMessage;
}
