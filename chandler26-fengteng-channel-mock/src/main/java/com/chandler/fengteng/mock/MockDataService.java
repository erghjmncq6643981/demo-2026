package com.chandler.fengteng.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.chandler.fengteng.mock.EventTaskController.EventTaskData;
import com.chandler.fengteng.mock.EventTaskController.EventTaskPayload;
import com.chandler.fengteng.mock.EventTaskController.EventTaskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 为 PDA 请求生成进程内递增的模拟箱号和垛码。 */
@Service
public class MockDataService {

    private static final Logger LOG = LoggerFactory.getLogger(MockDataService.class);
    private static final long MAX_ASSET_NUMBER = 99_999_999L;
    private static final int MAX_ASSETS_PER_TASK = 1000;

    private final int assetCount;
    private final AtomicLong nextAssetNumber = new AtomicLong(
            ThreadLocalRandom.current().nextLong(1, MAX_ASSET_NUMBER + 1));
    private final AtomicLong nextShortId = new AtomicLong(1001);
    private final Map<String, ScanSession> sessions = new HashMap<>();

    /** 创建模拟数据服务，并校验每次返回的箱号数量。 */
    public MockDataService(@Value("${mock.asset-count:3}") int assetCount) {
        if (assetCount < 1 || assetCount > 1000) {
            throw new IllegalArgumentException("mock.asset-count 必须在 1 到 1000 之间");
        }
        this.assetCount = assetCount;
    }

    /** 校验请求并生成模拟业务数据。 */
    public synchronized EventTaskData generate(EventTaskRequest request) {
        validate(request);
        EventTaskPayload payload = request.payload();
        if ("scan-connect".equals(request.method())) {
            LOG.info("模拟通道机连接检查成功");
            return new EventTaskData("scan-connect", "", "", List.of(),
                    payload.variables() == null ? Map.of() : payload.variables());
        }
        String taskNo = payload.taskNo();
        if (request.method() == null || "scan-start".equals(request.method())) {
            boolean stacking = payload.taskType() == 1;
            String shortId = stacking ? Long.toString(nextShortId.getAndIncrement()) : "";
            String shortCode = stacking ? "STC%06d".formatted(Long.parseLong(shortId)) : "";
            ScanSession session = new ScanSession(payload.taskType(), shortCode, shortId);
            sessions.put(taskNo, session);
            addAssets(session);
            LOG.info("模拟通道机开始扫码 taskNo={} taskType={} count={}",
                    taskNo, payload.taskType(), session.assetCodes.size());
            return result(request, session);
        }
        ScanSession session = sessions.get(taskNo);
        if (session == null) {
            throw new IllegalArgumentException("任务尚未开始扫码, taskNo=" + taskNo);
        }
        if (payload.taskType() != null && !payload.taskType().equals(session.taskType)) {
            throw new IllegalArgumentException("taskType 与已开始的任务不一致");
        }
        if ("scan-info".equals(request.method())) {
            addAssets(session);
        } else if ("scan-end".equals(request.method())) {
            sessions.remove(taskNo);
        }
        LOG.info("模拟通道机任务操作 taskNo={} method={} count={}",
                taskNo, request.method(), session.assetCodes.size());
        return result(request, session);
    }

    private void addAssets(ScanSession session) {
        int count = Math.min(assetCount, MAX_ASSETS_PER_TASK - session.assetCodes.size());
        for (int i = 0; i < count; i++) {
            long number = nextAssetNumber.getAndUpdate(
                    current -> current == MAX_ASSET_NUMBER ? 1 : current + 1);
            session.assetCodes.add("FC.%08d".formatted(number));
        }
    }

    private EventTaskData result(EventTaskRequest request, ScanSession session) {
        return new EventTaskData(request.method() == null ? "" : request.method(),
                session.shortCode, session.shortId, List.copyOf(session.assetCodes),
                request.payload().variables() == null ? Map.of() : request.payload().variables());
    }

    private void validate(EventTaskRequest request) {
        if (request == null || !"1.0".equals(request.version()) || request.payload() == null) {
            throw new IllegalArgumentException("请求必须包含 version=1.0 和 payload 对象");
        }
        if (request.method() != null && !List.of("scan-start", "scan-info", "scan-end", "scan-connect")
                .contains(request.method())) {
            throw new IllegalArgumentException("method 仅支持 scan-start、scan-info、scan-end 或 scan-connect");
        }
        if ("scan-connect".equals(request.method())) {
            return;
        }
        EventTaskPayload payload = request.payload();
        boolean invalidTaskType = payload.taskType() != null
                && payload.taskType() != 0 && payload.taskType() != 1;
        boolean missingTaskType = payload.taskType() == null
                && (request.method() == null || "scan-start".equals(request.method()));
        if (invalidTaskType || missingTaskType) {
            throw new IllegalArgumentException("taskType 仅支持 0（扫码）或 1（打垛）");
        }
        if (payload.taskNo() == null || payload.taskNo().isBlank()) {
            throw new IllegalArgumentException("taskNo 不能为空");
        }
    }

    private static final class ScanSession {
        private final Integer taskType;
        private final String shortCode;
        private final String shortId;
        private final List<String> assetCodes = new ArrayList<>();

        private ScanSession(Integer taskType, String shortCode, String shortId) {
            this.taskType = taskType;
            this.shortCode = shortCode;
            this.shortId = shortId;
        }
    }
}
