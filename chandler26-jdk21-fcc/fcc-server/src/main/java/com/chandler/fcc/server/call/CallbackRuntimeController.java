package com.chandler.fcc.server.call;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 坐席回拨运行接口，所属坐席来自认证身份。 */
@RestController @RequestMapping("/api/telephony/callbacks") @RequiredArgsConstructor
public class CallbackRuntimeController {
    private final CallbackRuntimeService service;
    /** 查询可领取及本人任务。
     * @param pageNum 页码 @param customerNumber 精确号码 @return 分页摘要
     */
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="1")int pageNum,@RequestParam(required=false)String customerNumber){return Map.of("code",200,"data",service.list(pageNum,customerNumber));}
    /** 领取并安排回拨；返回调度标识，不表示已拨出。
     * @param id 回拨标识 @return 持久外呼任务标识
     */
    @PostMapping("/{id}/call") public Map<String,Object> call(@PathVariable String id){return Map.of("code",200,"data",Map.of("dialJobId",service.schedule(id)));}
}
