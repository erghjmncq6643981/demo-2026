package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.server.agent.application.model.AgentRuntimeState;
import com.chandler.fcc.server.agent.domain.AgentLoginStatus;
import com.chandler.fcc.server.agent.domain.AgentWorkStatus;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.agent.infrastructure.data.AgentRuntimeStateData;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
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
     * @return 当前工作状态；尚未初始化时返回退出状态
     */
    public AgentRuntimeState status() {
        String workNo = identity.requirePrincipal().workNo();
        AgentRuntimeStateData result = mapper.presence(workNo);
        if (result == null) {
            return AgentRuntimeState.builder()
                .loginStatus(AgentLoginStatus.LOGOUT)
                .workStatus(AgentWorkStatus.UNREADY)
                .build();
        }
        return toState(result);
    }

    /**
     * 设置当前登录坐席的非通话状态。
     *
     * @param state LOGIN 示闲或 LOGIN_BUSY 示忙
     * @return 数据库中的最终状态
     * @throws ResponseStatusException 状态非法或坐席正被通话占用时抛出
     */
    public AgentRuntimeState change(AgentLoginStatus state) {
        if (state == null || !state.isManuallySelectable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许示闲或示忙");
        }
        String workNo = identity.requirePrincipal().workNo();
        return transactions.execute(transaction -> {
            mapper.ensurePresence(workNo);
            if (mapper.setLoginStatus(workNo, state.name()) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "坐席正在通话或不可用");
            }
            return toState(mapper.presence(workNo));
        });
    }

    /**
     * 将持久化结果转换为明确的两个状态维度。
     *
     * @param data 持久化结果
     * @return 应用层状态
     */
    private AgentRuntimeState toState(AgentRuntimeStateData data) {
        if (data == null) {
            throw new IllegalStateException("坐席运行状态初始化失败");
        }
        return AgentRuntimeState.builder()
            .loginStatus(AgentLoginStatus.fromCode(data.getLoginStatus()))
            .workStatus(AgentWorkStatus.fromCode(data.getWorkStatus()))
            .activeCallId(data.getActiveCallId())
            .build();
    }
}
