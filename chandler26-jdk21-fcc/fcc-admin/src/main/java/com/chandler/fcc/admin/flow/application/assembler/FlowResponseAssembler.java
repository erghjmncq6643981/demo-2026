package com.chandler.fcc.admin.flow.application.assembler;

import com.chandler.fcc.admin.flow.controller.resp.FlowSummaryResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowVersionResp;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;

/** 装配流程与版本响应，统一版本格式及摘要/详情载荷边界。 */
public final class FlowResponseAssembler {
    private static final String SYSTEM_PREFIX = "SYSTEM_";

    /** 工具类不创建实例。 */
    private FlowResponseAssembler() {}

    /**
     * 将主数据转换为轻量摘要。
     *
     * @param entity 流程实体
     * @return 流程摘要
     */
    public static FlowSummaryResp summary(FlowDefinitionEntity entity) {
        Integer current = entity.getCurrentVersion();
        return FlowSummaryResp.builder()
            .id(entity.getId().toString())
            .flowKey(entity.getFlowKey())
            .flowName(entity.getFlowName())
            .modelType(entity.getModelType())
            .status(entity.getStatus())
            .currentVersion(current == null || current < 1 ? null : formatVersion(current))
            .system(entity.getFlowKey().startsWith(SYSTEM_PREFIX))
            .build();
    }

    /**
     * 将版本实体转换为摘要或详情。
     *
     * @param entity 版本实体
     * @param includeDefinition 是否返回完整定义
     * @return 版本响应
     */
    public static FlowVersionResp version(FlowDefinitionVersionEntity entity, boolean includeDefinition) {
        return FlowVersionResp.builder()
            .id(entity.getId().toString())
            .version(formatVersion(entity.getVersionNo()))
            .versionNo(entity.getVersionNo())
            .publishStatus(entity.getPublishStatus())
            .definitionJson(includeDefinition ? entity.getDefinitionJson() : null)
            .publishedAt(entity.getPublishedAt())
            .createdBy(entity.getCreatedBy())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    /**
     * 将数据库版本序号格式化为公开版本号。
     *
     * @param versionNo 数据库递增版本序号
     * @return 公开版本号
     */
    public static String formatVersion(Integer versionNo) {
        if (versionNo == null || versionNo < 1) {
            throw new IllegalArgumentException("版本序号不合法");
        }
        return "v1." + (versionNo - 1) + ".0";
    }
}
