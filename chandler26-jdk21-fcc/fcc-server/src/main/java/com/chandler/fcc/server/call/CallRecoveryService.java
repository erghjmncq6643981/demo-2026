package com.chandler.fcc.server.call;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/** 消费事件之前分页恢复已持久化的固定模板会话，不自动重拨。 */
@Service @RequiredArgsConstructor @Slf4j
public class CallRecoveryService {
 private final AgentRuntimeMapper mapper;
 private final CallSessionManager sessions;
 private final ObjectMapper json=new ObjectMapper();
 /** 恢复节点和双方话道关联，数据库不可用则阻止消费。
  * @throws IllegalStateException 持久上下文损坏
  */
 public void restore(){
  long after=0;
  while(true){
   var rows=mapper.active(after);if(rows.isEmpty())return;
   for(var row:rows){
    after=((Number)row.get("id")).longValue();
    if(sessions.getByCtrlUuid(row.get("ctrlId").toString()).isPresent())continue;
    if(row.get("attributes")==null)continue;
    try{
     Map<String,Object> data=json.readValue(row.get("attributes").toString(),new TypeReference<HashMap<String,Object>>(){});
     if(!data.containsKey("runtimeTemplate"))continue;
     var call=CallInfoBO.builder().callId(String.valueOf(after)).ctrlId(row.get("ctrlId").toString())
      .nodeId((String)data.get("nodeId")).modelKey(row.get("modelType").toString()).direction(DirectionType.valueOf(row.get("direction").toString()))
      .stageState(CallStageState.valueOf(row.get("status").toString())).callerNumber((String)row.get("caller")).destinationNumber((String)row.get("destination"))
      .agentWorkNo((String)data.get("primaryWorkNo")).agentExt((String)data.get("agentExt"))
      .agentChannelUuid((String)data.get("agentChannelUuid")).guestChannelUuid((String)data.get("guestChannelUuid")).data(data).build();
     if(call.getNodeId()==null||call.getGuestChannelUuid()==null)throw new IllegalStateException("缺少话道归属");
     sessions.registerSession(call);
    }catch(Exception e){throw new IllegalStateException("无法恢复通话 "+after,e);}
   }
  }
 }
}
