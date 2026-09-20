package com.chandler.fcc.server.call.controller;

import com.chandler.fcc.server.call.application.CallbackRuntimeService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前坐席可处理的漏话回拨运行接口。
 */
@RestController
@RequestMapping("/api/telephony/callbacks")
@RequiredArgsConstructor
public class CallbackRuntimeController {

    private final CallbackRuntimeService service;

    /**
     * 查询可领取及本人已领取的回拨任务。
     *
     * @param pageNum 页码
     * @param customerNumber 精确号码筛选，可为空
     * @return 分页摘要
     */
    @GetMapping
    public Map<String, Object> list(
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(required = false) String customerNumber
    ) {
        return Map.of("code", 200, "data", service.list(pageNum, customerNumber));
    }

    /**
     * 领取并安排回拨，返回值不表示电话已经拨出。
     *
     * @param id 回拨记录标识
     * @return 持久外呼任务标识
     */
    @PostMapping("/{id}/call")
    public Map<String, Object> call(@PathVariable String id) {
        return Map.of("code", 200, "data", Map.of("dialJobId", service.schedule(id)));
    }
}
