package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.infrastructure.nats.FccProperties;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 固定的坐席先接听外呼流程；共享给人工与渐进式任务。 */
@Service @RequiredArgsConstructor
public class OutboundCallService {
 private final AgentIdentityService identity;
 private final AgentRuntimeMapper agents;
 private final OutboundRoutePolicy routes;
 private final CallSessionManager sessions;
 private final CallPersistenceService persistence;
 private final FccClient client;
 private final FccProperties properties;
 private final ScreenPopService screenPop;
 private final AgentWebSocketService websocket;
 private final TransactionTemplate transactions;
 private final com.chandler.fcc.server.outbound.application.DialAttemptGuard attemptGuard;
 @org.springframework.beans.factory.annotation.Value("${fcc.outbound.notification-file:}") private String notificationFile;

 /** 创建通知型通话并先持久化意图。
  * @param tenant 租户 @param owner 任务坐席 @param number 号码 @param attemptId 尝试标识 @return 通话标识
  */
 public Map<String,Object> startNotificationFor(long tenant,String owner,String number,String attemptId){
  if(notificationFile.isBlank())throw new ResponseStatusException(HttpStatus.CONFLICT,"未配置通知提示音");
  var route=routes.resolve(tenant,number);
  var data=new HashMap<String,Object>();data.put("runtimeTemplate","NOTIFICATION");data.put("tenantId",tenant);data.put("dialJobId",attemptId);data.put("guestDialString",route.dialString());data.put("primaryWorkNo",owner);
  var call=CallInfoBO.builder().callId(IdUtil.getCallId()).ctrlId(IdUtil.getCtrlId("notification")).nodeId(properties.getDefaultNodeId())
   .modelKey(FlowModelType.AUTO_DIAL_NOTIFICATION.name()).direction(DirectionType.OUTBOUND).stageState(CallStageState.CALLING)
   .callerNumber(route.caller()).destinationNumber(route.number()).agentWorkNo(owner).guestChannelUuid(IdUtil.getUuid()).data(data).build();
  call.putData("guestChannelUuid",call.getGuestChannelUuid());call.putData("nodeId",call.getNodeId());
  transactions.executeWithoutResult(status->{attemptGuard.beforePersist(attemptId);persistence.saveOrUpdateSession(call);});
  sessions.registerSession(call);dial(call,false);
  return Map.of("callId",call.getCallId(),"status","ACCEPTED");
 }

 /** 保存通知按键确认。
  * @param call 通话 @param digit 确认键 @return 是否为通知流程
  */
 public boolean digits(CallInfoBO call,String digit){
  if(!"NOTIFICATION".equals(call.getDataStr("runtimeTemplate","")))return false;
  if("1".equals(digit)){
   call.putData("notificationConfirmed",true);persistence.saveOrUpdateSession(call);
   client.hangup(call.getNodeId(),call.getCtrlId(),call.getGuestChannelUuid(),"NORMAL_CLEARING");
  }
  return true;
 }

 /** 登录坐席发起外呼。 @param requestedOwner 请求工号 @param number 目标号码 @return 通话标识与受理状态 */
 public Map<String,Object> start(String requestedOwner,String number){
  var actor=identity.requirePrincipal();
  if(requestedOwner!=null&&!actor.workNo().equals(requestedOwner))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"坐席身份不匹配");
  return startFor(actor.tenantId(),actor.workNo(),number,null);
 }

 /** 调度器以持久任务身份启动渐进式呼叫，不接受浏览器指定租户。
  * @param tenant 租户 @param owner 坐席 @param number 被叫 @param taskId 调度任务，可空 @return 受理结果
  */
 public Map<String,Object> startFor(long tenant,String owner,String number,String taskId){
  var agent=agents.agent(tenant,owner);
  if(agent==null || agent.get("extension")==null || !agent.get("extension").toString().matches("[0-9]{2,20}"))throw new ResponseStatusException(HttpStatus.CONFLICT,"坐席没有可用话机绑定");
  var route=routes.resolve(tenant,number);
  String callId=IdUtil.getCallId();
  var data=new HashMap<String,Object>();
  data.put("runtimeTemplate","AGENT_FIRST"); data.put("tenantId",tenant); data.put("primaryWorkNo",owner);
  data.put("guestDialString",route.dialString());data.put("agentExt",agent.get("extension").toString());
  if(taskId!=null)data.put("dialJobId",taskId);
  var call=CallInfoBO.builder().callId(callId).ctrlId(IdUtil.getCtrlId("outbound")).nodeId(properties.getDefaultNodeId())
   .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name()).direction(DirectionType.OUTBOUND).stageState(CallStageState.CALLING)
   .callerNumber(route.caller()).destinationNumber(route.number()).agentWorkNo(owner).agentExt(agent.get("extension").toString())
   .agentChannelUuid(IdUtil.getUuid()).guestChannelUuid(IdUtil.getUuid()).data(data).build();
  call.putData("agentChannelUuid",call.getAgentChannelUuid());call.putData("guestChannelUuid",call.getGuestChannelUuid());call.putData("nodeId",call.getNodeId());
  transactions.executeWithoutResult(status->{
   attemptGuard.beforePersist(taskId);
   agents.ensurePresence(tenant,owner);
   if(agents.reserve(tenant,owner,callId)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"坐席未就绪或已被其他通话占用");
   persistence.saveOrUpdateSession(call);
  });
  sessions.registerSession(call);
  dial(call,true);
  screenPop.pushForAgentLeg(call,owner,call.getAgentExt(),30);
  return Map.of("callId",callId,"ctrlId",call.getCtrlId(),"status","ACCEPTED");
 }

 /** 处理固定模板事件，先持久阶段再发下一条稳定标识命令。
  * @param call 当前通话 @param params 节点事件 @return 是否由此模板消费
  */
 public boolean event(CallInfoBO call,JsonNode params){
  String template=call.getDataStr("runtimeTemplate","");
  if(!"AGENT_FIRST".equals(template)&&!"NOTIFICATION".equals(template))return false;
  synchronized(call){
   String state=params.path("state").asText(),uuid=params.path("uuid").asText();
   if(call.getData().containsKey("terminal"))return true;
   if(!uuid.equals(call.getAgentChannelUuid())&&!uuid.equals(call.getGuestChannelUuid()))return true;
   persistence.saveOrUpdateLeg(com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity.builder()
    .tenantId(((Number)call.getData().get("tenantId")).longValue()).callId(CallPersistenceService.parseNumericId(call.getCallId()))
    .channelUuid(uuid).nodeId(call.getNodeId()).roleType(uuid.equals(call.getAgentChannelUuid())?"AGENT":"CUSTOMER")
    .direction("OUTBOUND").state(state).hangupCause(params.path("cause").asText(null))
    .endedAt("DESTROY".equals(state)?java.time.LocalDateTime.now(java.time.ZoneOffset.UTC):null).build());
   if("NOTIFICATION".equals(template)&&"READY".equals(state)&&call.getData().putIfAbsent("notificationStarted",true)==null){
    call.setStageState(CallStageState.CONNECTED);persistence.saveOrUpdateSession(call);
    CallControlService.requireAccepted(client.readDTMF(call.getNodeId(),com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO.builder()
     .ctrlUuid(call.getCtrlId()).uuid(call.getGuestChannelUuid()).media(com.chandler.fcc.common.dto.command.MediaInfo.builder().type("FILE").data(notificationFile).build())
     .minDigits(1).maxDigits(1).tries(1).timeout(10).digitTimeout(2000).terminators("#").regex("^[1]$").actionAfter("HANGUP").build()));
   }else if("READY".equals(state)&&uuid.equals(call.getAgentChannelUuid())&&call.getData().putIfAbsent("agentReady",true)==null){
    persistence.saveOrUpdateSession(call);dial(call,false);
   }else if("READY".equals(state)&&uuid.equals(call.getGuestChannelUuid())&&call.getData().containsKey("agentReady")&&call.getData().putIfAbsent("bridgeRequested",true)==null){
    persistence.saveOrUpdateSession(call);
    CallControlService.requireAccepted(client.channelBridge(call.getNodeId(),call.getCtrlId(),call.getAgentChannelUuid(),call.getGuestChannelUuid()));
   }else if("BRIDGE".equals(state)){
    call.setStageState(CallStageState.CONNECTED);persistence.saveOrUpdateSession(call);
    websocket.pushCallAnswered(call.getAgentWorkNo(),call.getCallId(),Map.of("callId",call.getCallId()));
   }else if("DESTROY".equals(state)){
    var previousStage=call.getStageState();
    call.putData("terminal",true);call.setHangupCause(params.path("cause").asText("NORMAL_CLEARING"));call.setStageState(CallStageState.NORMAL_END);
    call.setDuration(params.path("duration").asInt());call.setBillsec(params.path("billsec").asInt());
    try {
     transactions.executeWithoutResult(status->{
      persistence.saveOrUpdateSession(call);
      if("AGENT_FIRST".equals(template))agents.release(((Number)call.getData().get("tenantId")).longValue(),call.getAgentWorkNo(),call.getCallId());
     });
    } catch(RuntimeException failure){call.getData().remove("terminal");call.setStageState(previousStage);throw failure;}
    String peer=uuid.equals(call.getAgentChannelUuid())?call.getGuestChannelUuid():call.getAgentChannelUuid();
    if(peer!=null)client.hangup(call.getNodeId(),call.getCtrlId(),peer,"NORMAL_CLEARING");
    if("AGENT_FIRST".equals(template)){
     websocket.pushCallHangup(call.getAgentWorkNo(),call.getCallId(),Map.of("cause",call.getHangupCause()));
    }
    sessions.removeSession(call.getCtrlId());
   }
  }
  return true;
 }

 /** 以预分配 UUID 拨号，超时保留持久会话供查询，不释放坐席再重拨。
  * @param call 通话 @param agent 是否拨坐席
  */
 private void dial(CallInfoBO call,boolean agent){
  String uuid=agent?call.getAgentChannelUuid():call.getGuestChannelUuid();
  String destination=agent?"user/"+call.getAgentExt():call.getDataStr("guestDialString","");
  var dto=FNodeDialDTO.builder().ctrlUuid(call.getCtrlId()).uuid(uuid).timeout(30)
   .destination(FNodeDialDTO.Destination.builder().callParams(List.of(FNodeDialDTO.CallParam.builder().uuid(uuid).dialString(destination).cidNumber(call.getCallerNumber()).cidName("FCC").build())).build()).build();
  CallControlService.requireAccepted(client.dial(call.getNodeId(),dto));
 }
}
