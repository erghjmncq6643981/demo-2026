package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 维护服务端权威坐席状态，防止浏览器覆盖通话占用状态。
 */
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {

    private final AgentRuntimeMapper mapper;
    private final AgentIdentityService identity;
    private final TransactionTemplate transactions;

    /**
     * 查询当前登录坐席的工作状态。
     *
     * @return 当前工作状态；尚未初始化时返回休息状态
     */
    public Map<String, Object> status() {
        String workNo = identity.requirePrincipal().workNo();
        Map<String, Object> result = mapper.presence(workNo);
        return result == null ? Map.of("status", "REST") : result;
    }

    /**
     * 设置当前登录坐席的非通话状态。
     *
     * @param state READY 就绪或 REST 休息
     * @return 数据库中的最终状态
     * @throws ResponseStatusException 状态非法或坐席正被通话占用时抛出
     */
    public Map<String, Object> change(String state) {
        if (!"READY".equals(state) && !"REST".equals(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许就绪或休息");
        }
        String workNo = identity.requirePrincipal().workNo();
        return transactions.execute(transaction -> {
            mapper.ensurePresence(workNo);
            if (mapper.setPresence(workNo, state) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "坐席正在通话或不可用");
            }
            return mapper.presence(workNo);
        });
    }
}
