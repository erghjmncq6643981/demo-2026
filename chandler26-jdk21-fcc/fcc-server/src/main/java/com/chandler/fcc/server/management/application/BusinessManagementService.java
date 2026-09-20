package com.chandler.fcc.server.management.application;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.customer.api.CustomerRecord;
import com.chandler.fcc.server.customer.application.CustomerService;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.customer.infrastructure.CustomerMapper;
import com.chandler.fcc.server.management.infrastructure.BusinessManagementMapper;
import com.chandler.fcc.server.outbound.application.DialJobService;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 为 fcc-admin 提供经过管理身份校验的客户和自动外呼业务用例。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessManagementService {

    private final AgentIdentityService identity;
    private final BusinessManagementMapper mapper;
    private final AgentRuntimeMapper agents;
    private final CustomerMapper customers;
    private final CustomerService customerService;
    private final DialJobService jobs;
    private final DialJobMapper jobMapper;

    /**
     * 分页查询客户摘要。
     *
     * @param page 页码
     * @param owner 坐席筛选，可为空
     * @param phone 号码筛选，可为空
     * @return 客户摘要
     */
    public List<Map<String, Object>> customers(int page, String owner, String phone) {
        identity.requireManagement();
        return mapper.customers(
            owner,
            phone == null || phone.isBlank() ? null : PhoneNumber.normalize(phone),
            offset(page)
        );
    }

    /**
     * 查询客户详情。
     *
     * @param id 客户标识
     * @return 客户详情
     */
    public CustomerRecord customer(String id) {
        validateId(id, "客户标识无效");
        identity.requireManagement();
        return customers.detail(requireOwner(mapper.customerOwner(id)), id);
    }

    /**
     * 新建或按版本修改客户资料。
     *
     * @param id 客户标识，新建时为空
     * @param requestedOwner 新建时的负责坐席
     * @param record 客户资料
     * @return 客户标识
     */
    public String save(String id, String requestedOwner, CustomerRecord record) {
        identity.requireManagement();
        if (id != null) validateId(id, "客户标识无效");
        String owner = id == null
            ? requireAgent(requestedOwner)
            : requireOwner(mapper.customerOwner(id));
        record.setId(id);
        return customerService.saveFor(owner, record, id == null);
    }

    /**
     * 分页查询自动外呼任务摘要。
     *
     * @param page 页码
     * @param owner 坐席筛选，可为空
     * @return 任务摘要
     */
    public List<Map<String, Object>> jobs(int page, String owner) {
        identity.requireManagement();
        return mapper.jobs(owner, offset(page));
    }

    /**
     * 为已启用坐席创建自动外呼任务。
     *
     * @param owner 执行坐席
     * @param number 目标号码
     * @param mode 外呼模式
     * @param attempts 最大尝试次数
     * @param requestKey 业务幂等键
     * @return 任务标识
     */
    public String createJob(
        String owner,
        String number,
        String mode,
        int attempts,
        String requestKey
    ) {
        identity.requireManagement();
        return jobs.createFor(requireAgent(owner), number, mode, attempts, requestKey);
    }

    /**
     * 查询任务的逐次尝试结果。
     *
     * @param id 任务标识
     * @return 尝试记录
     */
    public List<Map<String, Object>> attempts(String id) {
        validateId(id, "任务标识无效");
        identity.requireManagement();
        requireOwner(mapper.jobOwner(id));
        return jobMapper.attempts(id);
    }

    /**
     * 暂停、恢复或取消自动外呼任务。
     *
     * @param id 任务标识
     * @param action 操作代码
     */
    public void control(String id, String action) {
        validateId(id, "任务标识无效");
        var actor = identity.requireManagement();
        String owner = requireOwner(mapper.jobOwner(id));
        if (jobMapper.control(owner, id, action) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不允许此操作");
        }
        log.info(
            "[外呼管理] 操作任务 operator={} jobId={} action={}",
            actor.workNo(),
            id,
            action
        );
    }

    /**
     * 校验分页范围并计算偏移。
     *
     * @param page 页码
     * @return 分页偏移
     */
    private int offset(int page) {
        if (page < 1 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码无效");
        }
        return (page - 1) * 50;
    }

    /**
     * 验证负责坐席存在且处于启用状态。
     *
     * @param owner 坐席工号
     * @return 已验证工号
     */
    private String requireAgent(String owner) {
        if (owner == null || agents.agent(owner) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责坐席不存在或未启用");
        }
        return owner;
    }

    /**
     * 将资源不存在统一转换为未找到响应。
     *
     * @param owner 数据库查询到的负责工号
     * @return 非空负责工号
     */
    private String requireOwner(String owner) {
        if (owner == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return owner;
    }

    /**
     * 验证雪花标识的公开字符串形式。
     *
     * @param id 待验证标识
     * @param message 无效时的业务提示
     */
    private void validateId(String id, String message) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
