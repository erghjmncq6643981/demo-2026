package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.client.SidecarAdminClient;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.vo.SidecarHealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端运行状态查询控制器。
 *
 * @author Chandler
 */
@Tag(name = "运行状态", description = "提供管理端可访问的 Sidecar 与 FreeSWITCH 实时健康状态")
@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final SidecarAdminClient sidecarAdminClient;

    /**
     * 查询 Sidecar 及其 FreeSWITCH 连接的实时状态。
     *
     * @return 经过管理端归一化的健康状态
     */
    @Operation(summary = "查询 Sidecar 与 FreeSWITCH 健康状态")
    @GetMapping("/sidecar/health")
    public CommonResult<SidecarHealthVO> getSidecarHealth() {
        Map<String, Object> health = sidecarAdminClient.healthCheck();
        if (health == null) {
            health = Map.of("status", "UNHEALTHY");
        }
        String reportedStatus = text(health.get("status"));
        boolean sidecarResponded = !"UNHEALTHY".equalsIgnoreCase(reportedStatus);
        boolean freeSwitchAlive = Boolean.TRUE.equals(health.get("fs_alive"));
        boolean databaseConnected = Boolean.TRUE.equals(health.get("pg_connected"));
        String status = !sidecarResponded
                ? "UNAVAILABLE"
                : freeSwitchAlive && databaseConnected && "UP".equalsIgnoreCase(reportedStatus)
                ? "HEALTHY"
                : "DEGRADED";

        SidecarHealthVO result = SidecarHealthVO.builder()
                .status(status)
                .nodeId(text(health.get("node_id")))
                .nodeState(text(health.get("state")))
                .freeSwitchAlive(freeSwitchAlive)
                .databaseConnected(databaseConnected)
                .activeChannels(integer(health.get("active_channels")))
                .maxChannels(integer(health.get("max_channels")))
                .checkedAt(LocalDateTime.now())
                .message(sidecarResponded ? null : "Sidecar 请求失败")
                .build();
        return CommonResult.success(result);
    }

    /**
     * 将协议值转换为可选文本。
     *
     * @param value 原始协议值
     * @return 文本值，缺失时返回 {@code null}
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将协议数值转换为整数。
     *
     * @param value 原始协议值
     * @return 整数值，缺失或格式错误时返回 {@code null}
     */
    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
