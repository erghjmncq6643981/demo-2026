package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 流程动作的受控执行方式。
 *
 * <p>该枚举只描述执行边界，不允许流程定义携带 Java 类名、反射方法或任意外部地址。</p>
 */
@Getter
public enum FlowActionExecutorType {

    FNODE_COMMAND("FNode 指令"),
    INTERNAL_METHOD("内部业务方法"),
    THIRD_PARTY_API("第三方接口");

    private final String desc;

    /**
     * 创建执行器类型。
     *
     * @param desc 中文业务说明
     */
    FlowActionExecutorType(String desc) {
        this.desc = desc;
    }
}
