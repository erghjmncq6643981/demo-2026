package com.chandler.fcc.admin.flow.application.assembler;

import static org.junit.jupiter.api.Assertions.*;

import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import org.junit.jupiter.api.Test;

/** 验证响应装配保留身份、公开版本格式及详情按需加载边界。 */
class FlowResponseAssemblerTest {
    /** 摘要透传大整数身份并识别系统模型，未发布时不制造当前版本。 */
    @Test
    void preservesSummaryIdentityAndUnpublishedState() {
        var entity = FlowDefinitionEntity.builder().id(9007199254740993L)
            .flowKey("SYSTEM_TEST").flowName("测试模型").modelType("INBOUND").currentVersion(0).build();
        var response = FlowResponseAssembler.summary(entity);
        assertEquals("9007199254740993", response.getId());
        assertTrue(response.isSystem());
        assertNull(response.getCurrentVersion());
        entity.setCurrentVersion(2);
        assertEquals("v1.1.0", FlowResponseAssembler.summary(entity).getCurrentVersion());
    }

    /** 列表摘要不携带定义，详情才返回完整定义。 */
    @Test
    void loadsDefinitionOnlyForDetail() {
        var entity = FlowDefinitionVersionEntity.builder().id(9007199254740993L)
            .versionNo(3).definitionJson("{\"fixture\":true}").publishStatus("DRAFT").build();
        assertNull(FlowResponseAssembler.version(entity, false).getDefinitionJson());
        var detail = FlowResponseAssembler.version(entity, true);
        assertEquals(entity.getDefinitionJson(), detail.getDefinitionJson());
        assertEquals("9007199254740993", detail.getId());
        assertEquals("v1.2.0", detail.getVersion());
    }

    /** 无效版本序号保持原异常，不输出虚假的公开版本。 */
    @Test
    void rejectsInvalidVersionOrdinal() {
        assertThrows(IllegalArgumentException.class, () -> FlowResponseAssembler.formatVersion(null));
        assertThrows(IllegalArgumentException.class, () -> FlowResponseAssembler.formatVersion(0));
        assertEquals("v1.0.0", FlowResponseAssembler.formatVersion(1));
    }
}
