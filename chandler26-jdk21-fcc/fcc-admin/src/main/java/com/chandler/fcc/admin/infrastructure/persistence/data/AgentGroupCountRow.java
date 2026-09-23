package com.chandler.fcc.admin.infrastructure.persistence.data;

import lombok.Data;

/**
 * 组织节点及其整棵子树的去重坐席数量查询结果。
 */
@Data
public class AgentGroupCountRow {

    /** 组织节点主键。 */
    private Long groupId;

    /** 当前节点及全部启用子节点中的去重坐席数量。 */
    private Long memberCount;
}
