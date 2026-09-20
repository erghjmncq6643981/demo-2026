package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 人工和自动外呼共用的号码、中继与主叫策略，禁止调用方注入拨号串。
 */
@Service
@RequiredArgsConstructor
public class OutboundRoutePolicy {

    private final AgentRuntimeMapper mapper;

    /**
     * 已解析的出局资源。
     *
     * @param number 规范化目标号码
     * @param dialString 节点拨号串
     * @param caller 授权主叫号码
     */
    public record Route(String number, String dialString, String caller) {}

    /**
     * 根据单中心资源配置解析目标号码。
     *
     * @param input 用户输入号码
     * @return 出局策略
     * @throws ResponseStatusException 缺少启用中继或网关名称非法时抛出
     */
    public Route resolve(String input) {
        String number = PhoneNumber.normalize(input);
        var resource = mapper.outbound();
        if (mapper.internal(number) == 1) {
            String caller = resource == null ? number : resource.get("caller").toString();
            return new Route(number, "user/" + number, caller);
        }
        if (resource == null || resource.get("gateway") == null || resource.get("caller") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未配置启用的出局中继与主叫号码");
        }
        String gateway = resource.get("gateway").toString();
        if (!gateway.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "网关名称不合法");
        }
        return new Route(
            number,
            "sofia/gateway/" + gateway + "/" + number,
            PhoneNumber.normalize(resource.get("caller").toString())
        );
    }
}
