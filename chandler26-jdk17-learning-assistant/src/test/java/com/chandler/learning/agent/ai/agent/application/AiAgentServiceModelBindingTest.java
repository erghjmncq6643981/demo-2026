package com.chandler.learning.agent.ai.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.agent.api.AgentSaveRequest;
import com.chandler.learning.agent.ai.agent.domain.AiAgent;
import com.chandler.learning.agent.ai.model.domain.AiModelConfig;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.ai.agent.infrastructure.AiAgentMapper;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAgentServiceModelBindingTest {

    @Test
    void createBindsConcreteModelConfigAndCopiesItsModelSnapshot() {
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiModelConfigService modelConfigService = mock(AiModelConfigService.class);
        SystemLogService systemLogService = mock(SystemLogService.class);
        UserDisplayNameService userDisplayNameService = mock(UserDisplayNameService.class);
        AiAgentService service = new AiAgentService(agentMapper, modelConfigService, systemLogService,
                userDisplayNameService);

        AiModelConfig modelConfig = new AiModelConfig();
        modelConfig.setId(101L);
        modelConfig.setProvider("deepseek");
        modelConfig.setModelName("deepseek-v4-pro");
        when(modelConfigService.requireEnabled(101L)).thenReturn(modelConfig);
        when(agentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AgentSaveRequest request = new AgentSaveRequest();
        request.setName("场景规划 Agent");
        request.setCode("scene-planner");
        request.setModelConfigId(101L);
        request.setModelProvider("kimi");
        request.setModelName("kimi-k3");
        service.create(request);

        ArgumentCaptor<AiAgent> captor = ArgumentCaptor.forClass(AiAgent.class);
        verify(agentMapper).insert(captor.capture());
        assertThat(captor.getValue().getModelConfigId()).isEqualTo(101L);
        assertThat(captor.getValue().getModelProvider()).isEqualTo("deepseek");
        assertThat(captor.getValue().getModelName()).isEqualTo("deepseek-v4-pro");
    }
}
