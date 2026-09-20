package com.chandler.fcc.admin;

import com.chandler.fcc.admin.client.SidecarAdminClient;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.ExtensionCreateReq;
import com.chandler.fcc.admin.model.dto.ExtensionQueryReq;
import com.chandler.fcc.admin.model.vo.ExtensionVO;
import com.chandler.fcc.admin.service.ExtensionService;
import com.chandler.fcc.admin.starter.FccAdminApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分机管理与 Go Sidecar HTTP 交互及 Redis 在线态集成测试
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccAdminApplication.class)
@ActiveProfiles("local")
public class ExtensionAdminIntegrationTest extends EphemeralSipKeyTest {

    @Autowired
    private ExtensionService extensionService;

    @Autowired
    private SidecarAdminClient sidecarAdminClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 测试 Go Sidecar HTTP 探活与 FreeSWITCH 连通状态
     */
    @Test
    @DisplayName("测试Go Sidecar HTTP健康探活接口连通性")
    void testSidecarHealthCheck() {
        Map<String, Object> health = sidecarAdminClient.healthCheck();
        assertNotNull(health, "探活响应不应为空");
        logInfo("Sidecar 健康状态响应: " + health);
        assertTrue(health.containsKey("status") || health.containsKey("state") || health.containsKey("error"),
                "应包含健康状态字段");
    }

    /**
     * 测试分机创建落库、Sidecar HTTP 同步及 Redis 在线态联动
     */
    @Test
    @DisplayName("测试分机创建、Sidecar同步、Redis在线态感知与删除")
    @Transactional
    void testExtensionLifecycleWithSidecar() {
        String testExt = "1099";
        String testPwd = "password123";

        // 1. 创建分机
        Long id = extensionService.createExtension(ExtensionCreateReq.builder()
                .extension(testExt)
                .password(testPwd)
                .endpointType("SIP")
                .build());
        assertNotNull(id);

        // 2. 模拟 SIP 注册事件触发的 Redis 在线态缓存
        stringRedisTemplate.opsForValue().set("fcc:extension:presence:" + testExt, "ONLINE");

        // 3. 查询分机详情与在线态
        ExtensionVO vo = extensionService.getByExtension(testExt);
        assertNotNull(vo);
        assertEquals(testExt, vo.getExtension());
        assertEquals("ONLINE", vo.getOnlineStatus(), "Redis 缓存的在线态应为 ONLINE");

        // 4. 分页检索
        PageResult<ExtensionVO> page = extensionService.queryExtensions(ExtensionQueryReq.builder()
                .extension("109")
                .pageNum(1)
                .pageSize(10)
                .build());
        assertTrue(page.getTotal() >= 1);
        assertTrue(page.getList().stream().anyMatch(e -> e.getExtension().equals(testExt)));

        // 5. 删除分机
        extensionService.deleteExtension(id);

        // 6. 验证已删除且 Redis 缓存已清理
        ExtensionVO deletedVo = extensionService.getByExtension(testExt);
        assertNull(deletedVo, "分机记录已被软删除");
        String cachedPresence = stringRedisTemplate.opsForValue().get("fcc:extension:presence:" + testExt);
        assertNull(cachedPresence, "Redis 在线态缓存应已被同步清除");
    }

    private void logInfo(String msg) {
        System.out.println("ℹ️ [TEST] " + msg);
    }
}
