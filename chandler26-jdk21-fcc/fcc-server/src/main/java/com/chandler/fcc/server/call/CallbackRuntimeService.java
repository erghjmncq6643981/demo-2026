package com.chandler.fcc.server.call;

import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.outbound.application.DialJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Set;

/** 回拨领取和调度任务同事务提交，客户端不再另发一次人工外呼。 */
@Service @RequiredArgsConstructor @Slf4j
public class CallbackRuntimeService {
    private final CallbackRuntimeMapper mapper;
    private final AgentIdentityService identity;
    private final DialJobService jobs;
    private final TransactionTemplate transactions;
    /** 分页查询本人或未分配的回拨。
     * @param page 页码 @param number 精确号码筛选 @return 分页摘要
     */
    public Map<String,Object> list(int page,String number){
        var actor=identity.requirePrincipal();
        if(page<1||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        return Map.of("pageNum",page,"pageSize",10,"total",mapper.count(actor.tenantId(),actor.workNo(),number),
                "list",mapper.list(actor.tenantId(),actor.workNo(),number,(page-1)*10));
    }
    /** 原子领取并创建渐进式调度，重复点击返回同一待执行任务。
     * @param id 回拨标识 @return 外呼任务标识
     */
    public String schedule(String id){
        var actor=identity.requirePrincipal();
        String job=transactions.execute(status->{
            var row=mapper.lock(actor.tenantId(),actor.workNo(),id);
            if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"回拨已由其他坐席领取或不存在");
            if(row.get("jobId")!=null){
                if(Set.of("PENDING","RUNNING","PAUSED").contains(String.valueOf(row.get("jobStatus"))))return row.get("jobId").toString();
                if(!Set.of("FAILED","CANCELLED").contains(String.valueOf(row.get("jobStatus"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"回拨已完成或结果尚未确认");
            } else if(!Set.of("PENDING","ASSIGNED").contains(String.valueOf(row.get("status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前回拨不能执行");
            int attempt=((Number)row.get("attempts")).intValue()+1;
            String created=jobs.createFor(actor.tenantId(),actor.workNo(),row.get("number").toString(),"PROGRESSIVE",1,"callback-"+id+"-"+attempt);
            if(mapper.schedule(actor.tenantId(),actor.workNo(),id,created)!=1)throw new IllegalStateException("回拨领取冲突");
            return created;
        });
        log.info("[漏话回拨] 已关联调度 tenantId={} workNo={} callbackId={} jobId={}",actor.tenantId(),actor.workNo(),id,job);
        return job;
    }
}
