package com.chandler.dingtalk.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 箱箱 OpenAPI 事件回调测试接收器。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/openapi/events")
@Tag(name = "OpenAPI事件回调测试", description = "接收并查看箱箱OpenAPI事件推送")
public class OpenApiEventCallbackController {

    private static final int MAX_RECORD_SIZE = 200;

    private final ObjectMapper objectMapper;
    private final AtomicLong idGenerator = new AtomicLong();
    private final List<CallbackRecord> records = new CopyOnWriteArrayList<>();

    @PostMapping("/callback")
    @Operation(description = "接收箱箱系统出库/入库事件推送")
    public Map<String, Object> callback(@RequestBody(required = false) String body, HttpServletRequest request) {
        CallbackRecord record = buildRecord(body, request);
        records.add(0, record);
        trimRecords();
        log.info("收到OpenAPI事件回调, id={}, eventId={}, eventType={}, demandNo={}, waybillNo={}, requestId={}",
                record.getId(), record.getEventId(), record.getEventType(), record.getDemandNo(),
                record.getWaybillNo(), record.getRequestId());
        return Map.of(
                "code", 200,
                "successful", true,
                "message", "success",
                "timestamp", System.currentTimeMillis(),
                "data", Map.of(
                        "id", record.getId(),
                        "eventId", Objects.toString(record.getEventId(), ""),
                        "eventType", Objects.toString(record.getEventType(), "")
                )
        );
    }

    @GetMapping("/received")
    @Operation(description = "查询已接收的事件回调")
    public Map<String, Object> received(@RequestParam(value = "eventType", required = false) String eventType,
                                        @RequestParam(value = "demandNo", required = false) String demandNo,
                                        @RequestParam(value = "waybillNo", required = false) String waybillNo) {
        List<CallbackRecord> result = records.stream()
                .filter(record -> StringUtils.isBlank(eventType) || StringUtils.equals(eventType, record.getEventType()))
                .filter(record -> StringUtils.isBlank(demandNo) || StringUtils.equals(demandNo, record.getDemandNo()))
                .filter(record -> StringUtils.isBlank(waybillNo) || StringUtils.equals(waybillNo, record.getWaybillNo()))
                .toList();
        return Map.of(
                "code", 200,
                "successful", true,
                "message", "success",
                "timestamp", System.currentTimeMillis(),
                "data", result
        );
    }

    @DeleteMapping("/received")
    @Operation(description = "清空已接收的事件回调")
    public Map<String, Object> clear() {
        int count = records.size();
        records.clear();
        return Map.of(
                "code", 200,
                "successful", true,
                "message", "success",
                "timestamp", System.currentTimeMillis(),
                "data", Map.of("cleared", count)
        );
    }

    private CallbackRecord buildRecord(String body, HttpServletRequest request) {
        JsonNode root = parseBody(body);
        JsonNode order = root == null ? null : root.path("order");
        return CallbackRecord.builder()
                .id(idGenerator.incrementAndGet())
                .receivedAt(Instant.now())
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .requestId(firstHeader(request, "X-Request-ID"))
                .appId(firstHeader(request, "X-App-Id"))
                .timestamp(firstHeader(request, "X-Timestamp"))
                .eventId(text(root, "eventId"))
                .eventType(text(root, "eventType"))
                .eventName(text(root, "eventName"))
                .demandNo(text(order, "demandNo"))
                .waybillNo(text(order, "waybillNo"))
                .headers(headers(request))
                .body(StringUtils.defaultString(body))
                .build();
    }

    private JsonNode parseBody(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("OpenAPI事件回调JSON解析失败, body={}", body, e);
            return null;
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asText();
    }

    private String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (StringUtils.equalsIgnoreCase(headerName, name)) {
                return request.getHeader(headerName);
            }
        }
        return null;
    }

    private Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            result.put(headerName, Collections.list(request.getHeaders(headerName)));
        }
        return result;
    }

    private void trimRecords() {
        if (records.size() <= MAX_RECORD_SIZE) {
            return;
        }
        records.subList(MAX_RECORD_SIZE, records.size()).clear();
    }

    @Data
    @Builder
    public static class CallbackRecord {
        private Long id;
        private Instant receivedAt;
        private String method;
        private String uri;
        private String requestId;
        private String appId;
        private String timestamp;
        private String eventId;
        private String eventType;
        private String eventName;
        private String demandNo;
        private String waybillNo;
        private Map<String, List<String>> headers;
        private String body;
    }
}
