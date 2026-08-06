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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 中石化回调模拟接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "中石化模拟接口", description = "模拟中石化移动吨罐 HTTP JSON 回调")
public class SinopecMockController {

    private static final int MAX_RECORD_SIZE = 200;
    private static final String GET_ORDER = "getOrder";
    private static final String GET_RECOVERY_STATUS = "getRecoveryStatus";
    private static final String JSON_MODE = "JSON";
    private static final String SOAP_MODE = "SOAP";
    private static final String FORM_MODE = "FORM";

    private final ObjectMapper objectMapper;
    private final AtomicLong idGenerator = new AtomicLong();
    private final List<SinopecRecord> records = new CopyOnWriteArrayList<>();

    @Value("${sinopec.mock.secret-key:ec4ba0eca73db0986a7c0cc47da2dacd}")
    private String expectedSecretKey;

    @PostMapping(value = "/TankInterfaceWebService.asmx")
    @Operation(description = "接收中石化回调；当前支持HTTP JSON，保留SOAP兼容")
    public ResponseEntity<String> asmx(@RequestBody(required = false) String body, HttpServletRequest request) {
        if (isJsonRequest(body, request)) {
            return receiveJson(null, body, request);
        }
        String action = resolveSoapAction(request, body);
        String secretKey = firstNonBlank(extractSoapValue(body, "secret_key"), extractSoapValue(body, "secretKey"));
        return receiveLegacy(action, secretKey, extractSoapValue(body, "data"), body, request, SOAP_MODE, true);
    }

    @PostMapping(value = "/TankInterfaceWebService.asmx/{action}")
    @Operation(description = "中石化ASMX路径格式：/TankInterfaceWebService.asmx/getOrder 或 /TankInterfaceWebService.asmx/getRecoveryStatus")
    public ResponseEntity<String> asmxAction(@PathVariable String action,
                                             @RequestParam(value = "secret_key", required = false) String secretKeyAlias,
                                             @RequestParam(value = "secretKey", required = false) String secretKey,
                                             @RequestParam(value = "data", required = false) String data,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        if (isJsonRequest(body, request)) {
            return receiveJson(action, body, request);
        }
        return receiveLegacy(action, firstNonBlank(secretKeyAlias, secretKey), data, body, request, FORM_MODE, false);
    }

    @PostMapping(value = "/sinopec/mock/callback", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "接收中石化HTTP JSON回调，建议本地测试baseUrl使用此地址")
    public ResponseEntity<String> jsonCallback(@RequestBody(required = false) String body, HttpServletRequest request) {
        return receiveJson(null, body, request);
    }

    @PostMapping(value = "/sinopec/mock/{action}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "按action接收中石化HTTP JSON回调")
    public ResponseEntity<String> jsonAction(@PathVariable String action,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return receiveJson(action, body, request);
    }

    @GetMapping(value = "/TankInterfaceWebService.asmx", produces = MediaType.TEXT_XML_VALUE)
    @Operation(description = "模拟 WSDL 探活")
    public ResponseEntity<String> wsdl() {
        return ResponseEntity.ok("""
                <?xml version="1.0" encoding="utf-8"?>
                <definitions name="TankInterfaceWebService" targetNamespace="http://tempuri.org/">
                  <service name="TankInterfaceWebService">
                    <documentation>Mock Sinopec WebService: getOrder, getRecoveryStatus</documentation>
                  </service>
                </definitions>
                """);
    }

    @GetMapping("/sinopec/mock/received")
    @Operation(description = "查询中石化模拟接口已收到的请求")
    public Map<String, Object> received(@RequestParam(value = "action", required = false) String action,
                                        @RequestParam(value = "orderNo", required = false) String orderNo,
                                        @RequestParam(value = "demandNo", required = false) String demandNo) {
        List<SinopecRecord> result = records.stream()
                .filter(record -> StringUtils.isBlank(action) || StringUtils.equalsIgnoreCase(action, record.getAction()))
                .filter(record -> StringUtils.isBlank(orderNo) || StringUtils.equals(orderNo, record.getOrderNo()))
                .filter(record -> StringUtils.isBlank(demandNo) || StringUtils.equals(demandNo, record.getDemandNo()))
                .toList();
        return success(result);
    }

    @DeleteMapping("/sinopec/mock/received")
    @Operation(description = "清空中石化模拟接口接收记录")
    public Map<String, Object> clear() {
        int count = records.size();
        records.clear();
        return success(Map.of("cleared", count));
    }

    private ResponseEntity<String> receiveJson(String action, String rawBody, HttpServletRequest request) {
        JsonNode root = parseJson(rawBody);
        JsonNode dataNode = extractJsonDataNode(root);
        String data = toJson(dataNode);
        String normalizedAction = normalizeAction(firstNonBlank(action, inferAction(dataNode)));
        if (StringUtils.isBlank(normalizedAction)) {
            return badAction(action);
        }
        String secretKey = firstNonBlank(text(root, "secret_key"), text(root, "secretKey"));
        SinopecRecord record = buildRecord(normalizedAction, secretKey, data, rawBody, request, JSON_MODE, dataNode);
        return saveAndRespond(record, false);
    }

    private ResponseEntity<String> receiveLegacy(String action, String secretKey, String data, String rawBody,
                                                 HttpServletRequest request, String requestMode, boolean soapResponse) {
        JsonNode dataNode = parseJson(data);
        String normalizedAction = normalizeAction(action);
        if (StringUtils.isBlank(normalizedAction)) {
            return badAction(action);
        }
        SinopecRecord record = buildRecord(normalizedAction, secretKey, data, rawBody, request, requestMode, dataNode);
        return saveAndRespond(record, soapResponse);
    }

    private ResponseEntity<String> saveAndRespond(SinopecRecord record, boolean soapResponse) {
        records.add(0, record);
        trimRecords();

        boolean successful = record.isSecretKeyValid() && !record.isForceFail();
        String errMsg = successful ? "" : failureMessage(record);
        String result = callbackResponse(successful, errMsg);
        record.setResponseBody(result);

        log.info("收到中石化模拟接口请求, id={}, mode={}, action={}, orderNo={}, demandNo={}, qty={}, codeCount={}, secretKeyValid={}, forceFail={}",
                record.getId(), record.getRequestMode(), record.getAction(), record.getOrderNo(), record.getDemandNo(), record.getQty(),
                record.getCodeCount(), record.isSecretKeyValid(), record.isForceFail());
        String requestJson = JSON_MODE.equals(record.getRequestMode()) ? record.getRawBody() : record.getRawData();
        if (StringUtils.isNotBlank(requestJson)) {
            log.info("中石化模拟接口请求JSON:\n{}", prettyJson(requestJson));
        }
        if (soapResponse) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_XML)
                    .body(soapEnvelope(record.getAction(), result));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    private SinopecRecord buildRecord(String action, String secretKey, String data, String rawBody,
                                      HttpServletRequest request, String requestMode, JsonNode dataNode) {
        List<String> codes = codes(dataNode);
        return SinopecRecord.builder()
                .id(idGenerator.incrementAndGet())
                .receivedAt(Instant.now())
                .requestMode(requestMode)
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .clientIp(clientIp(request))
                .contentType(request.getContentType())
                .action(action)
                .secretKey(secretKey)
                .secretKeyValid(StringUtils.equals(expectedSecretKey, secretKey))
                .forceFail(forceFail(request))
                .mockFailMessage(mockFailMessage(request))
                .orderNo(text(dataNode, "orderNo"))
                .demandNo(text(dataNode, "demandNo"))
                .qty(integer(dataNode, "qty"))
                .codeCount(codes.size())
                .codes(codes)
                .rawData(StringUtils.defaultString(data))
                .rawBody(StringUtils.defaultString(rawBody))
                .headers(headers(request))
                .build();
    }

    private JsonNode parseJson(String data) {
        if (StringUtils.isBlank(data)) {
            return null;
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            log.warn("中石化模拟接口data解析失败, data={}", data, e);
            return null;
        }
    }

    private String prettyJson(String json) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }

    private JsonNode extractJsonDataNode(JsonNode root) {
        if (root == null || root.path("data").isMissingNode() || root.path("data").isNull()) {
            return null;
        }
        JsonNode dataNode = root.path("data");
        if (!dataNode.isTextual()) {
            return dataNode;
        }
        return parseJson(dataNode.asText());
    }

    private boolean isJsonRequest(String body, HttpServletRequest request) {
        return StringUtils.containsIgnoreCase(request.getContentType(), MediaType.APPLICATION_JSON_VALUE)
                || StringUtils.startsWith(StringUtils.trimToEmpty(body), "{");
    }

    private String inferAction(JsonNode dataNode) {
        if (dataNode != null && !dataNode.path("recoveryTime").isMissingNode()) {
            return GET_RECOVERY_STATUS;
        }
        return GET_ORDER;
    }

    private String resolveSoapAction(HttpServletRequest request, String body) {
        String soapAction = StringUtils.strip(request.getHeader("SOAPAction"), "\"");
        if (StringUtils.isNotBlank(soapAction)) {
            return StringUtils.substringAfterLast(soapAction, "/");
        }
        if (StringUtils.contains(body, "<" + GET_ORDER)) {
            return GET_ORDER;
        }
        if (StringUtils.contains(body, "<" + GET_RECOVERY_STATUS)) {
            return GET_RECOVERY_STATUS;
        }
        return GET_ORDER;
    }

    private String normalizeAction(String action) {
        if (StringUtils.equalsIgnoreCase(GET_ORDER, action)) {
            return GET_ORDER;
        }
        if (StringUtils.equalsIgnoreCase(GET_RECOVERY_STATUS, action)) {
            return GET_RECOVERY_STATUS;
        }
        return null;
    }

    private ResponseEntity<String> badAction(String action) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(callbackResponse(false, "不支持的中石化接口: " + StringUtils.defaultString(action)));
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.isNotBlank(primary) ? primary : fallback;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("中石化模拟接口data序列化失败, data={}", node, e);
            return node.toString();
        }
    }

    private String extractSoapValue(String body, String tagName) {
        if (StringUtils.isBlank(body)) {
            return "";
        }
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";
        String value = StringUtils.substringBetween(body, startTag, endTag);
        return unescapeXml(StringUtils.defaultString(value));
    }

    private String callbackResponse(boolean successful, String errMsg) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("result", successful);
            data.put("errMsg", StringUtils.defaultString(errMsg));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("中石化模拟接口响应序列化失败", e);
            return "{\"data\":{\"result\":" + successful + ",\"errMsg\":\"" + escapeJson(errMsg) + "\"}}";
        }
    }

    private String failureMessage(SinopecRecord record) {
        if (!record.isSecretKeyValid()) {
            return "secret_key校验失败";
        }
        return firstNonBlank(record.getMockFailMessage(), "mock强制失败");
    }

    private String soapEnvelope(String action, String result) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <%sResponse xmlns="http://tempuri.org/">
                      <%sResult>%s</%sResult>
                    </%sResponse>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(action, action, escapeXml(result), action, action);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asText();
    }

    private Integer integer(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asInt();
    }

    private List<String> codes(JsonNode dataNode) {
        if (dataNode == null || dataNode.path("codes").isMissingNode() || !dataNode.path("codes").isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        dataNode.path("codes").forEach(item -> {
            String code = item.isTextual() ? item.asText() : text(item, "code");
            if (StringUtils.isNotBlank(code)) {
                result.add(code);
            }
        });
        return result;
    }

    private boolean forceFail(HttpServletRequest request) {
        return truthy(firstNonBlank(request.getParameter("mockFail"), request.getHeader("X-Mock-Fail")));
    }

    private String mockFailMessage(HttpServletRequest request) {
        return firstNonBlank(request.getParameter("mockFailMessage"), request.getHeader("X-Mock-Fail-Message"));
    }

    private boolean truthy(String value) {
        return StringUtils.equalsAnyIgnoreCase(StringUtils.trimToEmpty(value), "true", "1", "yes", "y");
    }

    private String clientIp(HttpServletRequest request) {
        return firstNonBlank(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
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

    private Map<String, Object> success(Object data) {
        return Map.of(
                "code", 200,
                "successful", true,
                "message", "success",
                "timestamp", System.currentTimeMillis(),
                "data", data
        );
    }

    private String escapeXml(String value) {
        return StringUtils.defaultString(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String unescapeXml(String value) {
        return StringUtils.defaultString(value)
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private String escapeJson(String value) {
        return StringUtils.defaultString(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    @Data
    @Builder
    public static class SinopecRecord {
        private Long id;
        private Instant receivedAt;
        private String requestMode;
        private String method;
        private String uri;
        private String clientIp;
        private String contentType;
        private String action;
        private String secretKey;
        private boolean secretKeyValid;
        private boolean forceFail;
        private String mockFailMessage;
        private String orderNo;
        private String demandNo;
        private Integer qty;
        private Integer codeCount;
        private List<String> codes;
        private String rawData;
        private String rawBody;
        private String responseBody;
        private Map<String, List<String>> headers;
    }
}
