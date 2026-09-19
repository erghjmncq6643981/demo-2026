package com.chandler.fcc.admin.client;

import com.chandler.fcc.common.dto.admin.ExtensionCreateReq;
import com.chandler.fcc.common.dto.admin.SidecarResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 软交换管理面 HTTP REST 客户端
 * <p>
 * 与部署在软交换节点侧的 Go Sidecar Agent (:8088) 进行同步强一致性交互，
 * 负责分机开户、销户、状态探活及节点连通性检查。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class SidecarAdminClient {

    private final String sidecarAdminUrl;
    private final RestTemplate restTemplate;

    /**
     * 构造函数注入管理端地址与初始化 RestTemplate
     *
     * @param sidecarAdminUrl Go Sidecar 管理端 HTTP 地址
     */
    public SidecarAdminClient(@Value("${fcc.sidecar-admin-url:http://127.0.0.1:8088}") String sidecarAdminUrl) {
        this.sidecarAdminUrl = sidecarAdminUrl.endsWith("/")
                ? sidecarAdminUrl.substring(0, sidecarAdminUrl.length() - 1)
                : sidecarAdminUrl;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 同步创建/注册 SIP 分机 (使用默认 context 和 callgroup)
     *
     * @param extension 分机号
     * @param password  分机注册密码
     * @return Go Sidecar 同步应答体
     */
    public SidecarResponse<?> createExtension(String extension, String password) {
        return createExtension(extension, password, "default", "default");
    }

    /**
     * 同步创建/注册 SIP 分机 (本地写 XML + 触发 reloadxml)
     *
     * @param extension 分机号 (如 1007)
     * @param password  分机注册密码
     * @param context   拨号计划上下文 (默认 default)
     * @param callgroup 呼叫组 (默认 default)
     * @return Go Sidecar 同步应答体
     */
    public SidecarResponse<?> createExtension(String extension, String password, String context, String callgroup) {
        String url = sidecarAdminUrl + "/api/v1/extensions";
        ExtensionCreateReq req = ExtensionCreateReq.builder()
                .extension(extension)
                .password(password)
                .context(context != null ? context : "default")
                .callgroup(callgroup != null ? callgroup : "default")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExtensionCreateReq> entity = new HttpEntity<>(req, headers);

        log.info("🌐 [Sidecar HTTP] 准备创建分机: ext={}, context={}, url={}", extension, req.getContext(), url);
        try {
            ResponseEntity<SidecarResponse<Map<String, Object>>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            log.info("🌐 [Sidecar HTTP] 创建分机响应: status={}, body={}", resp.getStatusCode(), resp.getBody());
            return resp.getBody();
        } catch (Exception e) {
            log.error("❌ [Sidecar HTTP] 创建分机请求失败: {}", e.getMessage(), e);
            return SidecarResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 同步注销/删除 SIP 分机 (本地删除 XML + 触发 reloadxml)
     *
     * @param extension 目标分机号
     * @return Go Sidecar 同步应答体
     */
    public SidecarResponse<?> deleteExtension(String extension) {
        String url = sidecarAdminUrl + "/api/v1/extensions?extension=" + extension;
        log.info("🌐 [Sidecar HTTP] 准备删除分机: ext={}, url={}", extension, url);
        try {
            ResponseEntity<SidecarResponse<Map<String, Object>>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            log.info("🌐 [Sidecar HTTP] 删除分机响应: status={}, body={}", resp.getStatusCode(), resp.getBody());
            return resp.getBody();
        } catch (Exception e) {
            log.error("❌ [Sidecar HTTP] 删除分机请求失败: {}", e.getMessage(), e);
            return SidecarResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 检查分机配置是否存在及 Sofia 注册态
     *
     * @param extension 目标分机号
     * @return Go Sidecar 同步应答体
     */
    public SidecarResponse<?> checkExtension(String extension) {
        String url = sidecarAdminUrl + "/api/v1/extensions?extension=" + extension;
        log.info("🌐 [Sidecar HTTP] 检查分机状态: ext={}, url={}", extension, url);
        try {
            ResponseEntity<SidecarResponse<Map<String, Object>>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            log.info("🌐 [Sidecar HTTP] 检查分机响应: status={}, body={}", resp.getStatusCode(), resp.getBody());
            return resp.getBody();
        } catch (Exception e) {
            log.error("❌ [Sidecar HTTP] 检查分机请求失败: {}", e.getMessage(), e);
            return SidecarResponse.fail(500, e.getMessage());
        }
    }

    /**
     * Go Sidecar 节点健康探活与 FreeSWITCH ESL 连通性检查
     *
     * @return 节点健康探活载荷字典
     */
    public Map<String, Object> healthCheck() {
        String url = sidecarAdminUrl + "/api/v1/health";
        log.info("[Sidecar HTTP] 开始执行节点健康探测");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            log.info("[Sidecar HTTP] 节点健康探测完成: status={}, fsAlive={}",
                    resp == null ? null : resp.get("status"),
                    resp == null ? null : resp.get("fs_alive"));
            return resp;
        } catch (Exception e) {
            log.warn("[Sidecar HTTP] 节点健康探测失败: {}", e.getMessage());
            return Map.of("status", "UNHEALTHY");
        }
    }
}
