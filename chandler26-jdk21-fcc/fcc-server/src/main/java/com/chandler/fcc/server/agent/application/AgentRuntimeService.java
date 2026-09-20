package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** 服务端权威坐席状态，通话占用不可被浏览器覆盖。 */
@Service @RequiredArgsConstructor
public class AgentRuntimeService {
 private final AgentRuntimeMapper mapper;
 private final AgentIdentityService identity;
 private final org.springframework.transaction.support.TransactionTemplate transactions;
 /** 查询本人状态。 @return 当前状态 */
 public Map<String,Object> status(){var actor=identity.requirePrincipal();var result=mapper.presence(actor.tenantId(),actor.workNo());return result==null?Map.of("status","REST"):result;}
 /** 设置非通话状态。 @param state 就绪或休息 @return 真实状态 */
 public Map<String,Object> change(String state){
  var actor=identity.requirePrincipal();
  if(!"READY".equals(state)&&!"REST".equals(state))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"只允许就绪或休息");
  return transactions.execute(transaction->{
  mapper.ensurePresence(actor.tenantId(),actor.workNo());
  if(mapper.setPresence(actor.tenantId(),actor.workNo(),state)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"坐席正在通话或不可用");
  return mapper.presence(actor.tenantId(),actor.workNo());
  });
 }
}
