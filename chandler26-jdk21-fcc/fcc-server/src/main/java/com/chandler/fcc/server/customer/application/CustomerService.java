package com.chandler.fcc.server.customer.application;

import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.customer.api.CustomerRecord;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.customer.infrastructure.CustomerMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** 客户资料用例，仅允许负责坐席维护自己的客户资料。 */
@Service @RequiredArgsConstructor @Slf4j
public class CustomerService {
    private final CustomerMapper mapper;
    private final AgentIdentityService identity;

    /** 按通话已确认身份匹配唯一客户，同号多客户不擅自选取。
     * @param tenant 租户 @param owner 接待坐席 @param phone 通话号码 @return 唯一客户或空
     */
    public java.util.Optional<CustomerRecord> match(long tenant,String owner,String phone) {
        try {
            var matches=mapper.list(tenant,owner,PhoneNumber.normalize(phone),0,2);
            return matches.size()==1?java.util.Optional.of(matches.getFirst()):java.util.Optional.empty();
        } catch (IllegalArgumentException e) { return java.util.Optional.empty(); }
    }

    /** 分页查摘要。
     * @param page 页码 @param phone 精确号码，可空 @return 最多五十条摘要
     */
    public List<CustomerRecord> list(int page, String phone) {
        var actor = identity.requirePrincipal();
        if (page < 1 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码无效");
        return mapper.list(actor.tenantId(), actor.workNo(), phone == null || phone.isBlank() ? null : PhoneNumber.normalize(phone), (page-1)*50, 50);
    }

    /** 读取有权访问的详情。
     * @param id 客户标识 @return 客户资料
     */
    public CustomerRecord detail(String id) {
        var actor = identity.requirePrincipal();
        var result = mapper.detail(actor.tenantId(), actor.workNo(), id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在或无权访问");
        return result;
    }

    /** 新增或按版本更新客户。
     * @param customer 输入资料 @param create 是否新增 @return 保存后的客户标识
     */
    public String save(CustomerRecord customer, boolean create) {
        var actor = identity.requirePrincipal();
        return saveFor(actor.tenantId(),actor.workNo(),customer,create);
    }

    /** 已鉴权的管理用例按真实负责坐席维护客户，复用相同校验与乐观锁。
     * @param tenant 租户 @param owner 负责坐席 @param customer 资料 @param create 是否新增 @return 客户 ID
     */
    public String saveFor(long tenant,String owner,CustomerRecord customer,boolean create) {
        var actor = new AgentIdentityService.Principal(owner,tenant);
        if (customer.getName() == null || customer.getName().isBlank() || customer.getName().length()>128
                || (customer.getNotes()!=null && customer.getNotes().length()>4000)
                || (customer.getCompanyName()!=null && customer.getCompanyName().length()>255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户姓名必填且各字段不得超过长度限制");
        }
        customer.setPhoneNumber(PhoneNumber.normalize(customer.getPhoneNumber()));
        if (create) { customer.setId(String.valueOf(IdUtil.nextId())); mapper.insert(actor.tenantId(), actor.workNo(), customer); }
        else if (customer.getVersion() == null || mapper.update(actor.tenantId(), actor.workNo(), customer)!=1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "客户已变更或无权访问，请刷新后重试");
        }
        log.info("[客户资料] 保存成功 tenantId={} workNo={} customerId={} create={}", actor.tenantId(), actor.workNo(), customer.getId(), create);
        return customer.getId();
    }
}
