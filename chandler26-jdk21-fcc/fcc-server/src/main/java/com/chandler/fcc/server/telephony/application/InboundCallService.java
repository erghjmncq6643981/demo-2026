package com.chandler.fcc.server.telephony.application;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** 固定 DID/技能组呼入流程，排队状态与已尝试坐席保存到通话事实。 */
@Service @RequiredArgsConstructor @Slf4j
public class InboundCallService {
 private final AgentRuntimeMapper agents;
 private final CallPersistenceService persistence;
 private final CallSessionManager sessions;
 private final FccClient client;
 private final ScreenPopService screenPop;
 private final AgentWebSocketService websocket;
 private final TransactionTemplate transactions;
 private final ObjectMapper json=new ObjectMapper();

 /** 接管普通呼入，固定发布版本或 group:组代码，不回落到任意租户。
  * @param call 通话 @param params 事件 @return 是否消费
  */
 public boolean event(CallInfoBO call,JsonNode params){
  if(call.getDirection()!=com.chandler.fcc.common.enums.DirectionType.INBOUND||"0000".equals(call.getDestinationNumber()))return false;
  synchronized(call){
   String state=params.path("state").asText(),uuid=params.path("uuid").asText();
   if("START".equals(state)&&!call.getData().containsKey("runtimeTemplate")){
    var routes=agents.inbound(call.getDestinationNumber());
    if(routes.size()!=1){client.hangup(call.getNodeId(),call.getCtrlId(),uuid,"UNALLOCATED_NUMBER");sessions.removeSession(call.getCtrlId());return true;}
    var route=routes.getFirst();long tenant=((Number)route.get("tenant")).longValue();String key=String.valueOf(route.get("routeKey"));
    call.putData("runtimeTemplate","INBOUND");call.putData("tenantId",tenant);call.putData("nodeId",call.getNodeId());call.putData("guestChannelUuid",call.getGuestChannelUuid());
    call.putData("queueDeadline",System.currentTimeMillis()+120000);call.putData("triedAgents",new ArrayList<String>());
    if(key.startsWith("group:")){call.putData("groupCode",key.substring(6));}
    else {
     try{String owner=json.readTree(String.valueOf(route.get("definition"))).path("didDirectConfig").path("workNo").asText();if(owner.isBlank())throw new IllegalArgumentException();call.putData("directOwner",owner);call.putData("flowVersionId",String.valueOf(route.get("versionId")));}
     catch(Exception invalid){client.hangup(call.getNodeId(),call.getCtrlId(),uuid,"CALL_REJECTED");sessions.removeSession(call.getCtrlId());return true;}
    }
    call.setStageState(CallStageState.CALLING);persistence.saveOrUpdateSession(call);
   }
   if(!"INBOUND".equals(call.getDataStr("runtimeTemplate","")))return true;
   if(call.getData().containsKey("terminal"))return true;
   // Late events from a previously rejected agent must not answer/end the new attempt.
   if(!uuid.equals(call.getGuestChannelUuid())&&!uuid.equals(call.getAgentChannelUuid()))return true;
   persistence.saveOrUpdateLeg(com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity.builder()
    .tenantId(((Number)call.getData().get("tenantId")).longValue()).callId(CallPersistenceService.parseNumericId(call.getCallId()))
    .channelUuid(uuid).nodeId(call.getNodeId()).roleType(uuid.equals(call.getGuestChannelUuid())?"CUSTOMER":"AGENT")
    .direction(uuid.equals(call.getGuestChannelUuid())?"INBOUND":"OUTBOUND").state(state)
    .hangupCause(params.path("cause").asText(null))
    .endedAt("DESTROY".equals(state)?java.time.LocalDateTime.now(java.time.ZoneOffset.UTC):null).build());
   if("READY".equals(state)&&uuid.equals(call.getGuestChannelUuid())){call.putData("guestReady",true);persistence.saveOrUpdateSession(call);route(call);}
   else if("READY".equals(state)&&uuid.equals(call.getAgentChannelUuid())){
    if(call.getData().putIfAbsent("bridgeRequested",true)==null){persistence.saveOrUpdateSession(call);CallControlService.requireAccepted(client.channelBridge(call.getNodeId(),call.getCtrlId(),call.getGuestChannelUuid(),uuid));}
   }else if("BRIDGE".equals(state)){
    call.setStageState(CallStageState.CONNECTED);persistence.saveOrUpdateSession(call);websocket.pushCallAnswered(call.getAgentWorkNo(),call.getCallId(),Map.of("callId",call.getCallId()));
   }else if("DESTROY".equals(state)){
    if(uuid.equals(call.getGuestChannelUuid())||call.getStageState()==CallStageState.CONNECTED){finish(call,params.path("cause").asText("NORMAL_CLEARING"));}
    else if(uuid.equals(call.getAgentChannelUuid())){
     websocket.pushCallHangup(call.getAgentWorkNo(),call.getCallId(),Map.of("cause",params.path("cause").asText("NO_ANSWER")));
     call.getData().remove("bridgeRequested");
     release(call);call.setAgentChannelUuid(null);call.setAgentWorkNo(null);call.getData().remove("agentChannelUuid");call.getData().remove("primaryWorkNo");call.getData().remove("screen_pop_pushed");persistence.saveOrUpdateSession(call);route(call);
    }
   }
  }
  return true;
 }

 /** 定时处理持久队列恢复后的待分配通话，客户已结束则不会再拨坐席。 */
 @Scheduled(fixedDelay=2000)
 public void routeWaiting(){for(var call:sessions.snapshot())if("INBOUND".equals(call.getDataStr("runtimeTemplate","")))try{synchronized(call){route(call);}}catch(RuntimeException e){log.warn("[呼入排队] 等待恢复 callId={}",call.getCallId());}}

 /** 在同一事务内预占并保存选中目标。
  * @param call 排队通话
  */
 @SuppressWarnings("unchecked")
 private void route(CallInfoBO call){
  if(call.getData().containsKey("terminal")||!call.getData().containsKey("guestReady")||call.getAgentChannelUuid()!=null)return;
  if(System.currentTimeMillis()>((Number)call.getData().get("queueDeadline")).longValue()){finish(call,"NO_ANSWER");return;}
  long tenant=((Number)call.getData().get("tenantId")).longValue();
  List<String> tried=(List<String>)call.getData().get("triedAgents");
  String direct=call.getDataStr("directOwner",null);
  Map<String,Object> candidate=direct!=null?agents.agent(tenant,direct):agents.candidate(tenant,call.getDataStr("groupCode",""),tried);
  if(candidate==null||candidate.get("extension")==null)return;
  String owner=candidate.get("workNo").toString(),extension=candidate.get("extension").toString();
  if(tried.contains(owner)||!extension.matches("[0-9]{2,20}"))return;
  Boolean reserved=transactions.execute(transaction->{
   if(agents.reserve(tenant,owner,call.getCallId())!=1)return false;
   call.setAgentWorkNo(owner);call.setAgentExt(extension);call.setAgentChannelUuid(IdUtil.getUuid());tried.add(owner);
   call.putData("primaryWorkNo",owner);call.putData("agentExt",extension);call.putData("agentChannelUuid",call.getAgentChannelUuid());persistence.saveOrUpdateSession(call);return true;
  });
  if(!Boolean.TRUE.equals(reserved))return;
  sessions.bindChannel(call.getAgentChannelUuid(),call.getCtrlId());
  var dto=FNodeDialDTO.builder().ctrlUuid(call.getCtrlId()).uuid(call.getAgentChannelUuid()).timeout(30)
   .destination(FNodeDialDTO.Destination.builder().callParams(List.of(FNodeDialDTO.CallParam.builder().uuid(call.getAgentChannelUuid()).dialString("user/"+extension).cidNumber(call.getCallerNumber()).cidName("FCC").build())).build()).build();
  CallControlService.requireAccepted(client.dial(call.getNodeId(),dto));screenPop.pushForAgentLeg(call,owner,extension,30);
 }

 /** 幂等结束，未接听追加漏话回拨。
  * @param call 通话 @param cause 原因
  */
 private void finish(CallInfoBO call,String cause){
  if(call.getData().putIfAbsent("terminal",true)!=null)return;
  boolean answered=call.getStageState()==CallStageState.CONNECTED;
  call.setStageState(CallStageState.NORMAL_END);call.setHangupCause(cause);
  transactions.executeWithoutResult(transaction->{persistence.saveOrUpdateSession(call);release(call);
   if(!answered)agents.callback(IdUtil.nextId(),((Number)call.getData().get("tenantId")).longValue(),call.getCallId(),call.getCallerNumber(),call.getDestinationNumber(),cause);
  });
  client.hangup(call.getNodeId(),call.getCtrlId(),call.getGuestChannelUuid(),"NORMAL_CLEARING");
  if(call.getAgentChannelUuid()!=null){client.hangup(call.getNodeId(),call.getCtrlId(),call.getAgentChannelUuid(),"NORMAL_CLEARING");websocket.pushCallHangup(call.getAgentWorkNo(),call.getCallId(),Map.of("cause",cause));}
  sessions.removeSession(call.getCtrlId());
 }
 /** 仅释放本次占用。 @param call 通话 */
 private void release(CallInfoBO call){if(call.getAgentWorkNo()!=null)agents.release(((Number)call.getData().get("tenantId")).longValue(),call.getAgentWorkNo(),call.getCallId());}
}
