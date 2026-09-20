package com.chandler.fcc.server.call;

import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Set;

/** 话后小结持久化后才结束整理态，不创建虚构工单。 */
@Service @RequiredArgsConstructor @Slf4j
public class AfterCallService {
    private final AfterCallMapper mapper;
    private final AgentRuntimeMapper agents;
    private final AgentIdentityService identity;
    private final TransactionTemplate transactions;
    private final ObjectMapper json=new ObjectMapper();

    /** 保存本人已结束通话的小结，重复请求保持首次结果。
     * @param callId 通话标识 @param category 类别 @param intent 意向 @param notes 纪要
     */
    public void save(String callId,String category,String intent,String notes) {
        var actor=identity.requirePrincipal();
        if(category==null||category.isBlank()||category.length()>64||!Set.of("HIGH","MID","LOW","UNASSESSED").contains(intent==null?"":intent)
                ||notes==null||notes.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请检查小结类别、意向和纪要长度");
        final String payload;
        try {payload=json.writeValueAsString(Map.of("category",category,"intent",intent,"notes",notes,"submittedAt",java.time.Instant.now().toString()));}
        catch(Exception failure){throw new IllegalStateException(failure);}
        transactions.executeWithoutResult(status->{
            if(mapper.lockEnded(actor.tenantId(),actor.workNo(),callId)==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"已结束通话不存在");
            if(mapper.save(actor.tenantId(),actor.workNo(),callId,payload)==1){
                // Only ACW can become READY; never overwrite a later call's BUSY state.
                agents.completeAcw(actor.tenantId(),actor.workNo(),callId);
            }
        });
        log.info("[话后整理] 小结已保存 tenantId={} workNo={} callId={}",actor.tenantId(),actor.workNo(),callId);
    }

    /** 查询本人小结。
     * @param callId 通话标识 @return 已保存的小结，无记录为空对象
     */
    public Object detail(String callId){
        var actor=identity.requirePrincipal();var value=mapper.detail(actor.tenantId(),actor.workNo(),callId);
        try{return value==null?Map.of():json.readTree(value);}
        catch(Exception failure){throw new IllegalStateException("小结数据损坏",failure);}
    }
}
