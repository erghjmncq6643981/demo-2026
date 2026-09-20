package com.chandler.fcc.server.management;

import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.customer.infrastructure.CustomerMapper;
import com.chandler.fcc.server.customer.application.CustomerService;
import com.chandler.fcc.server.customer.api.CustomerRecord;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.outbound.application.DialJobService;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** 客户及调度管理仍属于 fcc-server，管理端仅改变访问入口和授权角色。 */
@Service @RequiredArgsConstructor @Slf4j
public class BusinessManagementService {
    private final AgentIdentityService identity;
    private final BusinessManagementMapper mapper;
    private final AgentRuntimeMapper agents;
    private final CustomerMapper customers;
    private final CustomerService customerService;
    private final DialJobService jobs;
    private final DialJobMapper jobMapper;

    /** 分页客户摘要。 @param page 页码 @param owner 工号过滤 @param phone 号码过滤 @return 摘要 */
    public List<Map<String,Object>> customers(int page,String owner,String phone){var actor=identity.requireManagement();return mapper.customers(actor.tenantId(),owner,phone==null||phone.isBlank()?null:PhoneNumber.normalize(phone),offset(page));}
    /** 查询客户详情。 @param id 客户 @return 详情 */
    public CustomerRecord customer(String id){var actor=identity.requireManagement();return customers.detail(actor.tenantId(),owner(mapper.customerOwner(actor.tenantId(),id)),id);}
    /** 新建或修改客户；修改不接受任意变更归属。
     * @param id 客户，可空 @param requestedOwner 新建时负责工号 @param record 资料 @return ID
     */
    public String save(String id,String requestedOwner,CustomerRecord record){
        var actor=identity.requireManagement();String owner=id==null?agent(actor.tenantId(),requestedOwner):owner(mapper.customerOwner(actor.tenantId(),id));
        record.setId(id);return customerService.saveFor(actor.tenantId(),owner,record,id==null);
    }
    /** 分页任务摘要。 @param page 页码 @param owner 坐席筛选 @return 摘要 */
    public List<Map<String,Object>> jobs(int page,String owner){return mapper.jobs(identity.requireManagement().tenantId(),owner,offset(page));}
    /** 创建持久任务。 @param owner 执行坐席 @param number 号码 @param mode 模式 @param attempts 次数 @param key 请求幂等键 @return ID */
    public String createJob(String owner,String number,String mode,int attempts,String key){var actor=identity.requireManagement();return jobs.createFor(actor.tenantId(),agent(actor.tenantId(),owner),number,mode,attempts,key);}
    /** 查询逐次结果。 @param id 任务 @return 尝试记录 */
    public List<Map<String,Object>> attempts(String id){long tenant=identity.requireManagement().tenantId();owner(mapper.jobOwner(tenant,id));return jobMapper.attempts(id);}
    /** 受控任务操作，不强拆现有通话。 @param id 任务 @param action PAUSE/RESUME/CANCEL */
    public void control(String id,String action){var actor=identity.requireManagement();String owner=owner(mapper.jobOwner(actor.tenantId(),id));if(jobMapper.control(actor.tenantId(),owner,id,action)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"当前状态不允许此操作");log.info("[外呼管理] 操作任务 tenantId={} operator={} jobId={} action={}",actor.tenantId(),actor.workNo(),id,action);}
    /** 校验分页上界。 @param page 页码 @return 偏移 */
    private int offset(int page){if(page<1||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");return (page-1)*50;}
    /** 验证负责坐席归属。 @param tenant 租户 @param owner 工号 @return 工号 */
    private String agent(long tenant,String owner){if(owner==null||agents.agent(tenant,owner)==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"负责坐席不存在或未启用");return owner;}
    /** 对不存在或跨租户资源统一拒绝。 @param owner 查询结果 @return 非空工号 */
    private String owner(String owner){if(owner==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");return owner;}
}
