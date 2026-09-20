package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.PhoneBindingMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/** PC 登录确认加话机持有证明，禁止仅凭工号抢占话机。 */
@Service @RequiredArgsConstructor @Slf4j
public class PhoneBindingService {
 private final PhoneBindingMapper mapper;
 private final AgentIdentityService identity;
 private final TransactionTemplate transactions;
 private final FccClient client;
 private final SecureRandom random=new SecureRandom();
 @Value("${fcc.binding.prompt-file:}") private String promptFile;

 /** 为当前坐席生成两分钟有效的一次绑定码。
  * @param extension 待绑定分机 @return 一次码及有效期
  */
 public Map<String,Object> challenge(String extension) {
  var actor=identity.requirePrincipal();
  if(extension==null || !extension.matches("[0-9]{2,20}") || mapper.extensionExists(actor.tenantId(),extension)!=1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分机不存在或未启用");
  if(promptFile.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"未配置话机绑定提示音");
  String code=String.format("%08d",random.nextInt(100000000));
  mapper.challenge(actor.tenantId(),actor.workNo(),extension,hash(code));
  log.info("[话机绑定] 创建验证请求 tenantId={} workNo={}",actor.tenantId(),actor.workNo());
  return Map.of("code",code,"expiresInSeconds",120,"extension",extension);
 }

 /** 查询本人绑定结果。
  * @return 最新结果，没有挑战返回 NONE
  */
 public Map<String,Object> status() {
  var actor=identity.requirePrincipal(); var result=mapper.status(actor.tenantId(),actor.workNo());
  return result==null?Map.of("status","NONE"):result;
 }

 /** 截获 0000 通道，不进入普通客服路由。
  * @param call 当前会话 @param params 可信节点事件 @return 是否为绑定流程
  */
 public boolean channel(CallInfoBO call, JsonNode params) {
  if(!"0000".equals(call.getDestinationNumber())) return false;
  String state=params.path("state").asText();
  if("START".equals(state)) {
   String extension=params.path("params").path("authenticated_extension").asText();
   if(!extension.matches("[0-9]{2,20}") || promptFile.isBlank()) {client.hangup(call.getNodeId(),call.getCtrlId(),call.getGuestChannelUuid(),"CALL_REJECTED");return true;}
   call.putData("bindingExtension",extension);
   call.putData("bindingDeadline",System.currentTimeMillis()+120000);
  }
  if("READY".equals(state) && call.getData().containsKey("bindingExtension")) {
   if(call.getData().putIfAbsent("bindingPrompt","true")==null) {
    client.readDTMF(call.getNodeId(),FNodeReadDTMFDTO.builder().ctrlUuid(call.getCtrlId()).uuid(call.getGuestChannelUuid()).media(MediaInfo.builder().type("FILE").data(promptFile).build()).minDigits(8).maxDigits(8).tries(1).timeout(60).digitTimeout(10000).terminators("#").regex("^[0-9]{8}$").build());
   }
  }
  return true;
 }

 /** 只消费聚合完成的八位一次码，内容不进入日志。
  * @param call 通话 @param digit 按键结果 @return 是否已由绑定流程处理
  */
 public boolean digits(CallInfoBO call,String digit) {
  if(!"0000".equals(call.getDestinationNumber()))return false;
  if(digit==null || !digit.matches("[0-9]{8}"))return true;
  if(call.getData().putIfAbsent("bindingConsumed","true")!=null)return true;
  String extension=call.getDataStr("bindingExtension","");
  boolean valid=System.currentTimeMillis()<=((Number)call.getData().getOrDefault("bindingDeadline",0L)).longValue();
  Boolean success=valid?transactions.execute(status->{
   var challenge=mapper.lockChallenge(hash(digit),extension);
   if(challenge==null)return false;
   long tenant=((Number)challenge.get("tenantId")).longValue(); String owner=challenge.get("owner").toString();
   mapper.ensureLock(tenant);mapper.lockTenant(tenant);
   if(mapper.busy(tenant,owner,extension)>0)return false;
   mapper.disableBindings(tenant,owner,extension);mapper.clearExtensions(tenant,owner,extension);mapper.clearAgents(tenant,owner,extension);
   if(mapper.bindExtension(tenant,owner,extension)!=1 || mapper.bindAgent(tenant,owner,extension)!=1 || mapper.appendBinding(IdUtil.nextId(),tenant,owner,extension)!=1)throw new IllegalStateException("绑定对象已变更");
   mapper.consume(hash(digit),call.getGuestChannelUuid());
   log.info("[话机绑定] 验证并绑定成功 tenantId={} workNo={} callId={}",tenant,owner,call.getCallId());
   return true;
  }):false;
  client.hangup(call.getNodeId(),call.getCtrlId(),call.getGuestChannelUuid(),Boolean.TRUE.equals(success)?"NORMAL_CLEARING":"CALL_REJECTED");
  return true;
 }

 /** 生成不可逆摘要。
  * @param code 一次码 @return SHA-256 摘要
  */
 private String hash(String code) {
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
