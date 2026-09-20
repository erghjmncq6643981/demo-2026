package com.chandler.fcc.server.call;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 呼叫会话运行时上下文管理器
 * <p>
 * 负责在控制面内存及 Redis 中高频维护所有进行中通话的生命周期上下文（CallInfoBO），
 * 支持通过 ctrl_id、call_id、channel_uuid 等多维度核心标识快速定位通话实例。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class CallSessionManager {

    /**
     * 控制流程标识 -> 通话上下文映射
     */
    private final Map<String, CallInfoBO> ctrlSessions = new ConcurrentHashMap<>();

    /**
     * 话道 UUID -> 控制流程标识映射
     */
    private final Map<String, String> channelToCtrl = new ConcurrentHashMap<>();

    /**
     * 全局通话标识 -> 控制流程标识映射
     */
    private final Map<String, String> callToCtrl = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_ACTIVE_PREFIX = "fcc:call:active:";

    /**
     * 注册新的通话生命周期上下文
     *
     * @param callInfo 通话业务对象
     */
    public void registerSession(CallInfoBO callInfo) {
        if (callInfo == null) {
            return;
        }
        String ctrlId = callInfo.getCtrlId();
        String callId = callInfo.getCallId();

        if (ctrlId != null) {
            ctrlSessions.put(ctrlId, callInfo);
        }
        if (callId != null && ctrlId != null) {
            callToCtrl.put(callId, ctrlId);
        }
        if (callInfo.getAgentChannelUuid() != null && ctrlId != null) {
            channelToCtrl.put(callInfo.getAgentChannelUuid(), ctrlId);
        }
        if (callInfo.getGuestChannelUuid() != null && ctrlId != null) {
            channelToCtrl.put(callInfo.getGuestChannelUuid(), ctrlId);
        }

        // 异步向 Redis 登记活跃会话索引（租期 2 小时）
        if (stringRedisTemplate != null && ctrlId != null) {
            try {
                stringRedisTemplate.opsForValue().set(
                        REDIS_ACTIVE_PREFIX + ctrlId,
                        callId != null ? callId : ctrlId,
                        Duration.ofHours(2)
                );
            } catch (Exception e) {
                log.warn("⚠️ [Redis] 同步活跃通话索引失败: {}", e.getMessage());
            }
        }

        log.info("📋 [会话注册] CtrlID: {}, CallID: {}, Model: {}",
                ctrlId, callId, callInfo.getModelKey());
    }

    /**
     * 将指定话道通道 UUID 绑定至控制流程会话
     *
     * @param channelUuid 话道 UUID
     * @param ctrlId      控制流程标识
     */
    public void bindChannel(String channelUuid, String ctrlId) {
        if (channelUuid != null && ctrlId != null) {
            channelToCtrl.put(channelUuid, ctrlId);
        }
    }

    /**
     * 根据控制流程标识获取通话上下文
     *
     * @param ctrlId 控制流程标识
     * @return 通话上下文 Optional
     */
    public Optional<CallInfoBO> getByCtrlUuid(String ctrlId) {
        if (ctrlId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ctrlSessions.get(ctrlId));
    }

    /**
     * 根据话道通道 UUID 获取通话上下文
     *
     * @param channelUuid 话道 UUID
     * @return 通话上下文 Optional
     */
    public Optional<CallInfoBO> getByChannelUuid(String channelUuid) {
        if (channelUuid == null) {
            return Optional.empty();
        }
        String ctrlId = channelToCtrl.get(channelUuid);
        if (ctrlId != null) {
            return getByCtrlUuid(ctrlId);
        }
        ctrlId = callToCtrl.get(channelUuid);
        if (ctrlId != null) {
            return getByCtrlUuid(ctrlId);
        }
        return Optional.empty();
    }

    /**
     * 根据全局业务通话标识获取通话上下文
     *
     * @param callId 全局业务通话标识
     * @return 通话上下文 Optional
     */
    public Optional<CallInfoBO> getByCallId(String callId) {
        if (callId == null) {
            return Optional.empty();
        }
        String ctrlId = callToCtrl.get(callId);
        if (ctrlId != null) {
            return getByCtrlUuid(ctrlId);
        }
        return Optional.empty();
    }

    /**
     * 释放并清理指定通话会话及其通道映射
     *
     * @param ctrlId 控制流程标识
     */
    public void removeSession(String ctrlId) {
        if (ctrlId == null) {
            return;
        }
        CallInfoBO info = ctrlSessions.remove(ctrlId);
        if (info != null) {
            if (info.getCallId() != null) {
                callToCtrl.remove(info.getCallId());
            }
            if (info.getAgentChannelUuid() != null) {
                channelToCtrl.remove(info.getAgentChannelUuid());
            }
            if (info.getGuestChannelUuid() != null) {
                channelToCtrl.remove(info.getGuestChannelUuid());
            }
            log.info("🗑️ [会话释放] CtrlID: {}, CallID: {}", ctrlId, info.getCallId());
        }

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(REDIS_ACTIVE_PREFIX + ctrlId);
            } catch (Exception e) {
                log.warn("⚠️ [Redis] 删除活跃通话索引失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取当前节点内存中最近活跃的一通通话会话
     *
     * @return 通话上下文 Optional
     */
    public Optional<CallInfoBO> getLatestActiveSession() {
        return ctrlSessions.values().stream()
                .filter(s -> s.getData() != null && !"true".equals(s.getData().get("guestEnded")))
                .reduce((first, second) -> second);
    }

    /**
     * 获取当前活跃通话会话总数
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return ctrlSessions.size();
    }

    /** 返回活跃上下文快照，供恢复与排队调度使用。
     * @return 当前上下文副本列表
     */
    public List<CallInfoBO> snapshot() {
        return List.copyOf(ctrlSessions.values());
    }
}
