package com.chandler.fcc.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.agent.application.AgentEndpointService;
import com.chandler.fcc.admin.controller.req.SwitchAgentEndpointReq;
import com.chandler.fcc.admin.controller.resp.AgentEndpointsResp;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.*;
import com.chandler.fcc.admin.model.vo.*;
import com.chandler.fcc.admin.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 坐席人员、技能组与替班治理 REST 控制器
 * <p>
 * 坐席档案与登录账号统一以 fcc_agent 表为数据基准，不设任何人员准入名单。
 * </p>
 *
 * @author Chandler
 */
@Tag(name = "坐席人员与技能组管理", description = "提供坐席档案、登录口令、技能组编组、终端绑定与夜班/请假替班接口")
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentEndpointService agentEndpointService;

    /**
     * 新增坐席人员
     *
     * @param req 坐席创建入参
     * @return 坐席创建结果 (含一次性初始口令与分机注册口令)
     */
    @Operation(summary = "创建坐席人员 (口令留空则返回一次性随机口令)")
    @PostMapping
    public CommonResult<AccountCredentialVO> createAgent(@Valid @RequestBody AgentCreateReq req) {
        AccountCredentialVO credential = agentService.createAgent(req);
        return CommonResult.success(credential);
    }

    /**
     * 重置坐席登录口令
     *
     * @param id  坐席主键 ID
     * @param req 口令重置入参
     * @return 口令重置结果 (含一次性新口令)
     */
    @Operation(summary = "重置坐席登录口令")
    @PostMapping("/{id}/reset-password")
    public CommonResult<AccountCredentialVO> resetAgentPassword(@PathVariable("id") Long id,
                                                               @Valid @RequestBody ResetPasswordReq req) {
        AccountCredentialVO credential = agentService.resetPassword(id, req == null ? null : req.getPassword());
        return CommonResult.success(credential);
    }

    /**
     * 更新坐席资料
     *
     * @param req 坐席修改入参
     * @return 操作成功响应
     */
    @Operation(summary = "更新坐席档案信息")
    @PutMapping
    public CommonResult<Void> updateAgent(@Valid @RequestBody AgentUpdateReq req) {
        agentService.updateAgent(req);
        return CommonResult.success();
    }

    /**
     * 多条件分页检索坐席
     *
     * @param req 检索入参
     * @return 分页结果
     */
    @Operation(summary = "多条件分页查询坐席列表")
    @GetMapping
    public CommonResult<PageResult<AgentVO>> queryAgents(AgentQueryReq req) {
        PageResult<AgentVO> result = agentService.queryAgents(req);
        return CommonResult.success(result);
    }

    /**
     * 根据主键查询坐席详情
     *
     * @param id 坐席 ID
     * @return 坐席 VO
     */
    @Operation(summary = "根据主键获取坐席详情")
    @GetMapping("/{id}")
    public CommonResult<AgentVO> getAgentById(@PathVariable("id") Long id) {
        AgentVO vo = agentService.getAgentById(id);
        return CommonResult.success(vo);
    }

    /**
     * 删除坐席 (软删除)
     *
     * @param id 坐席 ID
     * @return 操作成功响应
     */
    @Operation(summary = "软删除坐席人员")
    @DeleteMapping("/{id}")
    public CommonResult<Void> deleteAgent(@PathVariable("id") Long id) {
        agentService.deleteAgent(id);
        return CommonResult.success();
    }

    /**
     * 创建技能组
     *
     * @param req 技能组入参
     * @return 技能组 ID
     */
    @Operation(summary = "创建话务技能组/组织部门")
    @PostMapping("/groups")
    public CommonResult<Long> createGroup(@Valid @RequestBody AgentGroupCreateReq req) {
        Long id = agentService.createGroup(req);
        return CommonResult.success(id);
    }

    /**
     * 修改技能组/部门信息
     *
     * @param req 技能组修改入参
     * @return 操作成功响应
     */
    @Operation(summary = "修改话务技能组/部门信息")
    @PutMapping("/groups")
    public CommonResult<Void> updateGroup(@Valid @RequestBody AgentGroupUpdateReq req) {
        agentService.updateGroup(req);
        return CommonResult.success();
    }

    /**
     * 删除技能组/部门 (根节点防删)
     *
     * @param id 技能组 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除话务技能组/部门")
    @DeleteMapping("/groups/{id}")
    public CommonResult<Void> deleteGroup(@PathVariable("id") Long id) {
        agentService.deleteGroup(id);
        return CommonResult.success();
    }

    /**
     * 查询全量技能组
     *
     * @return 技能组列表
     */
    @Operation(summary = "获取全量技能组列表")
    @GetMapping("/groups")
    public CommonResult<List<AgentGroupVO>> listGroups() {
        List<AgentGroupVO> list = agentService.listGroups();
        return CommonResult.success(list);
    }

    /**
     * 查询指定技能组的成员列表
     *
     * @param groupId 技能组 ID
     * @return 成员详情列表
     */
    @Operation(summary = "获取指定技能组成员列表")
    @GetMapping("/groups/{groupId}/members")
    public CommonResult<List<AgentGroupMemberVO>> listGroupMembers(@PathVariable("groupId") Long groupId) {
        List<AgentGroupMemberVO> list = agentService.listGroupMembers(groupId);
        return CommonResult.success(list);
    }

    /**
     * 分配坐席进入技能组 (绑定已有坐席)
     *
     * @param req 分接入参
     * @return 操作成功响应
     */
    @Operation(summary = "分配坐席加入技能组 (绑定已有坐席)")
    @PostMapping("/groups/members")
    public CommonResult<Void> addMemberToGroup(@Valid @RequestBody AgentGroupMemberReq req) {
        agentService.addMemberToGroup(req);
        return CommonResult.success();
    }

    /**
     * 创建新坐席并直接加入技能组
     *
     * @param groupId 技能组 ID
     * @param req 创建及绑定入参
     * @return 坐席创建结果 (含一次性初始口令与分机注册口令)
     */
    @Operation(summary = "创建新坐席并直接加入技能组")
    @PostMapping("/groups/{groupId}/create-and-bind")
    public CommonResult<AccountCredentialVO> createAndBindAgent(@PathVariable("groupId") Long groupId,
                                                              @Valid @RequestBody AgentCreateAndBindGroupReq req) {
        AccountCredentialVO credential = agentService.createAndBindAgent(groupId, req);
        return CommonResult.success(credential);
    }

    /**
     * 更新技能组成员角色与优先级
     *
     * @param groupId 技能组 ID
     * @param agentId 坐席 ID
     * @param req 成员更新入参
     * @return 操作成功响应
     */
    @Operation(summary = "更新技能组成员属性")
    @PutMapping("/groups/{groupId}/members/{agentId}")
    public CommonResult<Void> updateGroupMember(@PathVariable("groupId") Long groupId,
                                               @PathVariable("agentId") Long agentId,
                                               @Valid @RequestBody AgentGroupMemberUpdateReq req) {
        agentService.updateGroupMember(groupId, agentId, req);
        return CommonResult.success();
    }

    /**
     * 将坐席从技能组移出 (解绑)
     *
     * @param groupId 技能组 ID
     * @param agentId 坐席 ID
     * @return 操作成功响应
     */
    @Operation(summary = "将坐席从技能组移除 (解绑)")
    @DeleteMapping("/groups/{groupId}/members/{agentId}")
    public CommonResult<Void> removeMemberFromGroup(@PathVariable("groupId") Long groupId,
                                                    @PathVariable("agentId") Long agentId) {
        agentService.removeMemberFromGroup(groupId, agentId);
        return CommonResult.success();
    }

    /**
     * 查询坐席终端绑定记录
     *
     * @param agentId 坐席 ID
     * @return 绑定记录列表
     */
    @Operation(summary = "查询坐席名下绑定的终端记录")
    @GetMapping("/{agentId}/bindings")
    public CommonResult<List<AgentBindingVO>> listBindings(@PathVariable("agentId") Long agentId) {
        StpUtil.checkPermission("agent:view");
        List<AgentBindingVO> list = agentService.listBindingsByAgentId(agentId);
        return CommonResult.success(list);
    }

    /**
     * 申请坐席替班与夜班代接
     *
     * @param req 替班入参
     * @return 替班记录 ID
     */
    @Operation(summary = "申请坐席替班/夜班代接")
    @PostMapping("/substitutes")
    public CommonResult<Long> applySubstitute(@Valid @RequestBody AgentSubstituteReq req) {
        Long id = agentService.applySubstitute(req);
        return CommonResult.success(id);
    }

    /**
     * 获取替班代接申请流列表
     *
     * @return 替班列表
     */
    @Operation(summary = "获取替班代接记录列表")
    @GetMapping("/substitutes")
    public CommonResult<List<AgentSubstituteVO>> listSubstitutes() {
        List<AgentSubstituteVO> list = agentService.listSubstitutes();
        return CommonResult.success(list);
    }

    /**
     * 作废/取消替班
     *
     * @param id 替班记录 ID
     * @return 操作成功响应
     */
    @Operation(summary = "取消/作废替班记录")
    @DeleteMapping("/substitutes/{id}")
    public CommonResult<Void> cancelSubstitute(@PathVariable("id") Long id) {
        agentService.cancelSubstitute(id);
        return CommonResult.success();
    }

    /**
     * 获取指定坐席的三端接听配置全貌 (软话机 WebRTC、工位 SIP 话机、随行手机)
     *
     * @param workNo 坐席工号
     * @return 三端详情 VO
     */
    @Operation(summary = "获取坐席三种接听终端配置")
    @GetMapping("/{workNo}/endpoints")
    public CommonResult<AgentEndpointsResp> getAgentEndpoints(@PathVariable("workNo") String workNo) {
        StpUtil.checkPermission("agent:view");
        return CommonResult.success(AgentEndpointsResp.from(agentEndpointService.get(workNo)));
    }

    /**
     * 快速切换坐席接听方式 (软话机 WebRTC / 工位 SIP 话机 / 随行手机)
     *
     * @param req 切换入参
     * @return 切换后的最新配置
     */
    @Operation(summary = "快速切换坐席接听方式与终端")
    @PostMapping("/switch-endpoint")
    public CommonResult<AgentEndpointsResp> switchEndpoint(
        @Valid @RequestBody SwitchAgentEndpointReq req
    ) {
        StpUtil.checkPermission("agent:write");
        return CommonResult.success(
            AgentEndpointsResp.from(
                agentEndpointService.switchEndpoint(
                    req.getWorkNo(),
                    req.getEndpointType(),
                    req.getEndpointValue(),
                    StpUtil.getLoginIdAsString()
                )
            )
        );
    }
}
