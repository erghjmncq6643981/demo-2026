package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 人工和自动外呼共用的号码、拨号上下文与主叫策略，禁止调用方注入底层拨号串。
 */
@Service
@RequiredArgsConstructor
public class OutboundRoutePolicy {

    private final AgentRuntimeMapper mapper;

    /**
     * 已解析的出局资源。
     *
     * @param number 规范化目标号码
     * @param context FreeSWITCH 拨号计划上下文
     * @param caller 授权主叫号码
     */
    public record Route(String number, String context, String caller) {}

    /**
     * 根据单中心资源配置解析目标号码。
     *
     * @param input 用户输入号码
     * @return 出局策略
     * @throws ResponseStatusException 缺少可用出局上下文或上下文非法时抛出
     */
    public Route resolve(String input) {
        String number = PhoneNumber.normalize(input);
        var resource = mapper.outbound();
        if (mapper.internal(number) == 1) {
            String caller = resource == null ? number : resource.get("caller").toString();
            return new Route(number, "default", caller);
        }
        if (resource == null || resource.get("context") == null || resource.get("caller") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未配置启用的出局上下文与主叫号码");
        }
        String context = resource.get("context").toString();
        if (!context.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "出局拨号上下文不合法");
        }
        return new Route(
            number,
            context,
            PhoneNumber.normalize(resource.get("caller").toString())
        );
    }
}
