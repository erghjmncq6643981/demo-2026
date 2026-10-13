package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.command.FccClient;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 通过逻辑 FNode 入口读取完整快照，失败、无响应和格式不完整均返回未知。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelSnapshotReader {
    private final FccClient client;

    /**
     * 在数据库事务之外查询聚合话道快照。
     * @return 完整 UUID 集合；无法确认完整性时返回空引用
     */
    public Set<String> read() {
        try {
            var result = client.channelSnapshot();
            if (result == null || !result.isSuccess() || !(result.getData() instanceof Map<?, ?> data)
                || !Boolean.TRUE.equals(data.get("complete"))
                || !(data.get("channel_uuids") instanceof List<?> rows)) return null;
            Set<String> channels = new HashSet<>();
            for (Object row : rows) {
                if (!(row instanceof String uuid) || uuid.isBlank() || !channels.add(uuid)) return null;
            }
            return channels;
        } catch (RuntimeException failure) {
            log.debug("[通话对账] 完整快照不可用 type={}", failure.getClass().getSimpleName());
            return null;
        }
    }
}
