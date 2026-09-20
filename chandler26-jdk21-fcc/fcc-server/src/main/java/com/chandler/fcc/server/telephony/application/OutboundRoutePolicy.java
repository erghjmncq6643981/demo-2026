package com.chandler.fcc.server.telephony.application;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 人工和自动外呼共用号码、中继与主叫策略，禁止用户注入拨号串。 */
@Service @RequiredArgsConstructor
public class OutboundRoutePolicy {
 private final AgentRuntimeMapper mapper;
 /** 已解析出局资源。 @param number 目标号码 @param dialString 节点拨号串 @param caller 授权主叫 */
 public record Route(String number,String dialString,String caller){}
 /** 根据租户配置解析号码。 @param tenant 租户 @param input 用户号码 @return 出局策略 */
 public Route resolve(long tenant,String input){
  String number=PhoneNumber.normalize(input);
  var resource=mapper.outbound(tenant);
  if(mapper.internal(tenant,number)==1)return new Route(number,"user/"+number,resource==null?number:resource.get("caller").toString());
  if(resource==null||resource.get("gateway")==null||resource.get("caller")==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"未配置启用的出局中继与主叫号码");
  String gateway=resource.get("gateway").toString();
  if(!gateway.matches("[A-Za-z0-9_.-]{1,128}"))throw new ResponseStatusException(HttpStatus.CONFLICT,"网关名称不合法");
  return new Route(number,"sofia/gateway/"+gateway+"/"+number,PhoneNumber.normalize(resource.get("caller").toString()));
 }
}
