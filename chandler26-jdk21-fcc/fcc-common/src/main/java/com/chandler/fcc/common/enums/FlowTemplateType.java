package com.chandler.fcc.common.enums;

import java.util.Arrays;
import lombok.Getter;

/**
 * 管理端允许创建和有限维护的业务流程类型。
 *
 * <p>自动外呼通知、坐席先呼、坐席终端主动外呼和话机绑定属于系统固定模型，
 * 不允许管理员创建同名业务流程。</p>
 */
@Getter
public enum FlowTemplateType {

    INBOUND("呼入 IVR", "DID 呼入、导航收号、条件分支、坐席路由和服务评价");

    private final String desc;
    private final String businessDescription;

    /**
     * 创建流程类型。
     *
     * @param desc 类型中文名称
     * @param businessDescription 可维护的业务闭环说明
     */
    FlowTemplateType(String desc, String businessDescription) {
        this.desc = desc;
        this.businessDescription = businessDescription;
    }

    /**
     * 解析公开流程类型代码。
     *
     * @param code 类型代码
     * @return 对应流程类型
     * @throws IllegalArgumentException 类型为空或未开放维护
     */
    public static FlowTemplateType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("必须选择流程类型");
        }
        return Arrays.stream(values())
            .filter(type -> type.name().equalsIgnoreCase(code.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("暂不支持该流程类型: " + code));
    }
}
