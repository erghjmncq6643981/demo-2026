package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通话流程版本快照持久化实体 (fcc_flow_definition_version)
 * <p>
 * 存储不可变版本 JSON 编排，支持草稿、版本发布与历史回溯。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_flow_definition_version")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FlowDefinitionVersionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 所属流程主定义 ID (fcc_flow_definition.id)
     */
    private Long flowDefinitionId;

    /**
     * 版本序号 (1, 2, 3...)
     */
    private Integer versionNo;

    /**
     * 流程完整编排定义 JSON 结构体
     */
    private String definitionJson;

    /**
     * 内容 SHA-256 校验和
     */
    private String checksum;

    /**
     * 发布状态 (DRAFT, PUBLISHED, ARCHIVED)
     */
    private String publishStatus;

    /**
     * 正式发布上线时间
     */
    private LocalDateTime publishedAt;

    /**
     * 创建/发布人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
