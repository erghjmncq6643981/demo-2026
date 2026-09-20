package com.chandler.fcc.server.outbound.application;

import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;

/** 持久外呼调度，数据库领取与网络执行分离；未知结果绝不盲重拨。 */
@Service @RequiredArgsConstructor @EnableScheduling @Slf4j
public class DialJobService {
 private final DialJobMapper mapper;
 private final AgentIdentityService identity;
 private final TransactionTemplate transactions;
 private final OutboundCallService calls;
 private final ObjectMapper json=new ObjectMapper();
 @Value("${fcc.outbound.enabled:false}") private boolean enabled;
 @Value("${fcc.outbound.max-in-flight:5}") private int maxInFlight;
 @Value("${fcc.outbound.start-hour:9}") private int startHour;
 @Value("${fcc.outbound.end-hour:18}") private int endHour;
 @Value("${fcc.outbound.timezone:Asia/Shanghai}") private String timezone;

 /** 创建本人任务。 @param number 目标 @param mode 模式 @param maxAttempts 次数 @param key 幂等业务键 @return 任务标识 */
 public String create(String number,String mode,int maxAttempts,String key){
  var actor=identity.requirePrincipal();
  if(!Set.of("PROGRESSIVE","NOTIFICATION").contains(mode)||maxAttempts<1||maxAttempts>3||key==null||!key.matches("[A-Za-z0-9_-]{8,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"外呼模式、次数或请求标识不合法");
  String id=String.valueOf(IdUtil.nextId());
  try{mapper.create(Map.of("id",id,"tenant",actor.tenantId(),"owner",actor.workNo(),"key",key,"mode",mode,"maxAttempts",maxAttempts,"payload",json.writeValueAsString(Map.of("number",PhoneNumber.normalize(number)))));}
  catch(org.springframework.dao.DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"该请求已创建，请刷新任务列表");}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
  log.info("[自动外呼] 创建任务 tenantId={} workNo={} jobId={} mode={}",actor.tenantId(),actor.workNo(),id,mode);
  return id;
 }
 /** 本人分页查询。 @param page 页码 @return 任务列表 */
 public List<Map<String,Object>> list(int page){var actor=identity.requirePrincipal();if(page<1||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");return mapper.list(actor.tenantId(),actor.workNo(),(page-1)*50);}
 /** 本人逐次结果。 @param id 任务 @return 详情 */
 public Map<String,Object> detail(String id){var actor=identity.requirePrincipal();var row=mapper.detail(actor.tenantId(),actor.workNo(),id);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务不存在");row.put("attempts",mapper.attempts(id));return row;}
 /** 暂停、继续或取消，取消不会强制挂断已开始的通话。 @param id 任务 @param action 操作 */
 public void control(String id,String action){var actor=identity.requirePrincipal();if(mapper.control(actor.tenantId(),actor.workNo(),id,action)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"当前任务不允许该操作");log.info("[自动外呼] 任务操作 jobId={} action={} workNo={}",id,action,actor.workNo());}

 /** 周期领取一个任务；多实例共享容量锁，默认关闭以便部署时明确启用。 */
 @Scheduled(fixedDelayString="${fcc.outbound.poll-millis:2000}")
 public void dispatch(){
  if(!enabled)return;
  try{
   reconcile();
   int hour=ZonedDateTime.now(ZoneId.of(timezone)).getHour();
   if(startHour<0||endHour>24||startHour>=endHour||hour<startHour||hour>=endHour)return;
   Map<String,Object> job=transactions.execute(status->{
    if(mapper.schedulerLock()==null)throw new IllegalStateException("缺少外呼调度锁基线");
    if(mapper.activeCount()>=maxInFlight)return null;
    var row=mapper.next();if(row==null)return null;
    long tenant=((Number)row.get("tenant")).longValue();String id=row.get("id").toString();
    if(mapper.frequency(tenant,row.get("number").toString())>=3){mapper.finishJob(id,"FAILED");return null;}
    int attemptNo=mapper.countAttempts(id)+1;
    if(attemptNo>((Number)row.get("maxAttempts")).intValue()){mapper.finishJob(id,"FAILED");return null;}
    if(mapper.claim(id)!=1)return null;
    row.put("attempt",String.valueOf(IdUtil.nextId()));row.put("attemptNo",attemptNo);mapper.startAttempt(row);return row;
   });
   if(job==null)return;
   String attempt=job.get("attempt").toString();
   try {
    var result="NOTIFICATION".equals(job.get("mode"))
     ? calls.startNotificationFor(((Number)job.get("tenant")).longValue(),job.get("owner").toString(),job.get("number").toString(),attempt)
     : calls.startFor(((Number)job.get("tenant")).longValue(),job.get("owner").toString(),job.get("number").toString(),attempt);
    mapper.attach(attempt,result.get("callId").toString());
   } catch(RuntimeException e){
    // A persisted call means a network effect may already exist. Reconciliation
    // owns the outcome; do not turn a timeout into a second originate.
    if(mapper.call(attempt)==null){
     transactions.executeWithoutResult(status->{
      if(mapper.finishAttempt(attempt,"FAILED","PRECONDITION_FAILED")==1){
       mapper.finishJob(job.get("id").toString(),"FAILED");
      }
     });
    }
    log.warn("[自动外呼] 派发未确认 attemptId={}",attempt);
   }
  }catch(RuntimeException e){log.error("[自动外呼] 调度暂不可用: {}",e.getClass().getSimpleName());}
 }

 /** 从通话事实回填尝试，只有忙线与无人接听可自动重试。 */
 private void reconcile(){
  var expired=mapper.expiredDispatches();
  if(!expired.isEmpty())mapper.expireDispatches(expired);
  var ids=mapper.running().stream().map(row->row.get("id").toString()).distinct().toList();
  if(!ids.isEmpty())mapper.reconcileBatch(ids);
 }
}
