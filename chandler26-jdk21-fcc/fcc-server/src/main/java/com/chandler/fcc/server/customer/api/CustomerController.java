package com.chandler.fcc.server.customer.api;

import com.chandler.fcc.server.customer.application.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** fcc-server 客户资料接口，不接受调用方指定租户或负责坐席。 */
@RestController @RequestMapping("/api/telephony/customers") @RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;
    /** 查询客户摘要。
     * @param page 页码 @param phone 精确号码 @return 有权访问的摘要列表
     */
    @GetMapping @Operation(summary="分页查询我的客户")
    public Map<String,Object> list(@RequestParam(defaultValue="1") int page, @RequestParam(required=false) String phone) { return Map.of("code",200,"data",service.list(page, phone)); }
    /** 查询详情。
     * @param id 客户标识 @return 客户详情
     */
    @GetMapping("/{id}") @Operation(summary="查询客户详情")
    public Map<String,Object> detail(@PathVariable String id) { return Map.of("code",200,"data",service.detail(id)); }
    /** 新增客户。
     * @param request 客户资料 @return 新标识
     */
    @PostMapping @Operation(summary="新增客户")
    public Map<String,Object> create(@RequestBody CustomerRecord request) { return Map.of("code",200,"data",Map.of("id", service.save(request,true))); }
    /** 修改客户。
     * @param id 客户标识 @param request 客户资料及版本 @return 客户标识
     */
    @PutMapping("/{id}") @Operation(summary="按版本修改客户")
    public Map<String,Object> update(@PathVariable String id,@RequestBody CustomerRecord request) { request.setId(id); return Map.of("code",200,"data",Map.of("id", service.save(request,false))); }
}
