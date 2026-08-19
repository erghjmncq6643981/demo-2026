package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.domain.entity.AiModelConfig;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.AiAgentMapper;
import com.chandler.learning.agent.mapper.AiModelCallRecordMapper;
import com.chandler.learning.agent.mapper.AiModelConfigMapper;
import com.chandler.learning.agent.security.ApiKeyCryptoService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelConfigServiceBindingGuardTest {

    @Test
    void refusesToDisableModelConfigUsedByAgent() {
        AiModelConfigMapper modelMapper = mock(AiModelConfigMapper.class);
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiModelConfig config = new AiModelConfig();
        config.setId(101L);
        config.setName("DeepSeek 主配置");
        config.setEnabled(true);
        AiAgent agent = new AiAgent();
        agent.setName("英语词汇学习 Agent");
        agent.setModelConfigId(101L);
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);
        when(agentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));

        AiModelConfigService service = new AiModelConfigService(modelMapper, agentMapper,
                mock(AiModelCallRecordMapper.class), mock(ApiKeyCryptoService.class),
                mock(SystemLogService.class), mock(UserDisplayNameService.class));

        assertThatThrownBy(() -> service.updateEnabled(101L, false))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("英语词汇学习 Agent")
                .hasMessageContaining("先更换 Agent 模型");
        verify(modelMapper, never()).updateById(any(AiModelConfig.class));
    }
}
