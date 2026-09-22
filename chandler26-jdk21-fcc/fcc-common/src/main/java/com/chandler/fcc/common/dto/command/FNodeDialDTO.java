package com.chandler.fcc.common.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * FNode.Dial 发起呼叫指令入参模型
 * <p>
 * 用于向 FreeSWITCH 下发 originate 指令，发起坐席呼叫或客户外呼。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "FNode.Dial 发起外呼指令入参")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeDialDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 控制流程关联唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-outbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 呼叫通道预分配 UUID
     */
    @Schema(description = "预分配的话道 UUID", example = "c5b23d5e-8812-4214-9912-abcdef123456")
    private String uuid;

    /**
     * 外呼目的地配置
     */
    @Schema(description = "呼叫目的地配置")
    private Destination destination;

    /**
     * 自定义回铃音
     */
    @Schema(description = "自定义回铃音文件或配置", example = "local_stream://default")
    private String ringback;

    /**
     * 是否以同步方式发起呼叫
     */
    @Schema(description = "是否同步等待呼叫建立", example = "false")
    private Boolean sync;

    /**
     * 呼叫振铃超时时间（秒）
     */
    @Schema(description = "振铃超时时间（秒）", example = "30")
    private Integer timeout;

    /**
     * 外呼目的地信息结构体
     */
    @Schema(description = "外呼目的地配置结构")
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @ToString
    @Builder
    public static class Destination implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 具体呼叫参数列表
         */
        @JsonProperty("call_params")
        @Schema(description = "单路或并行外呼的目标列表")
        private List<CallParam> callParams;

        /**
         * 全局通道变量字典
         */
        @JsonProperty("global_params")
        @Schema(description = "全局通道变量字典")
        private Map<String, String> globalParams;
    }

    /**
     * 单个外呼目标参数结构体
     */
    @Schema(description = "单个外呼目标呼叫参数")
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @ToString
    @Builder
    public static class CallParam implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 业务目标号码或内部分机；节点侧结合 context 构造 FreeSWITCH 拨号表达式。
         */
        @JsonProperty("dial_string")
        @Schema(description = "待路由的分机或电话号码；必须是业务号码，不能包含 FreeSWITCH 拨号前缀", example = "1007")
        private String dialString;

        /**
         * FreeSWITCH 拨号计划上下文。
         */
        @Schema(description = "必填拨号计划上下文，内部分机使用 default，运营商使用其配置上下文", example = "default")
        private String context;

        /**
         * 主叫名称
         */
        @JsonProperty("cid_name")
        @Schema(description = "主叫显号名称", example = "BoxBox客服-钱丁君")
        private String cidName;

        /**
         * 主叫号码
         */
        @JsonProperty("cid_number")
        @Schema(description = "主叫显示电话号码", example = "02150880000")
        private String cidNumber;

        /**
         * 当前 Leg 通道 UUID
         */
        @Schema(description = "通道唯一 UUID", example = "d6e35f8a-1122-3344-5566-778899aabbcc")
        private String uuid;

        /**
         * 当前通道专属变量字典
         */
        @Schema(description = "当前通道专属变量字典")
        private Map<String, String> params;
    }
}
