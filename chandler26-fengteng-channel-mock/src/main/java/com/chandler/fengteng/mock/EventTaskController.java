package com.chandler.fengteng.mock;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 接收 PDA 扫码和打垛任务，按风腾协议返回模拟数据。 */
@RestController
public class EventTaskController {

    private final MockDataService mockDataService;

    /** 创建接口控制器。 */
    public EventTaskController(MockDataService mockDataService) {
        this.mockDataService = mockDataService;
    }

    /** 执行扫码或打垛任务。 */
    @PostMapping(path = "/privare-protocol/fengteng/event-task",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<EventTaskData> eventTask(@RequestBody EventTaskRequest request) {
        return new ApiResponse<>(true, 200, "操作成功!", System.currentTimeMillis(),
                mockDataService.generate(request));
    }

    /** 将参数和 JSON 错误转换为协议外层结构。 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "请求体不是合法 JSON";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, 400, message, System.currentTimeMillis(), null));
    }

    /** PDA 请求外层结构。 */
    public record EventTaskRequest(String version, String method, EventTaskPayload payload) {
    }

    /** PDA 任务参数。 */
    public record EventTaskPayload(Integer taskType, String taskNo, Map<String, Object> variables) {
    }

    /** 通道机返回的业务数据。 */
    public record EventTaskData(String method, String shortCode, String shortId,
                                List<String> assetsCodeList, Map<String, Object> variables) {
    }

    /** 通道机统一响应外层结构。 */
    public record ApiResponse<T>(boolean successful, int code, String message, long timestamp, T data) {
    }
}
