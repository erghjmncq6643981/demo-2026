package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import com.chandler.fcc.server.customer.application.CustomerService;
import com.chandler.fcc.server.customer.infrastructure.CustomerMapper;
import com.chandler.fcc.server.customer.api.CustomerRecord;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.telephony.application.OutboundRoutePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 客户归属、号码注入和路由真实配置边界。 */
class RuntimeDeliveryTest {
 /** 号码规范化不能接受 FS 拨号串。 */
 @Test void phoneInputCannotInjectCommands(){
  assertEquals("+8613800000000",PhoneNumber.normalize("+86 (138) 0000-0000"));
  for(String value:List.of("user/1001","1001&park()","${system(x)}","1\n2",""))assertThrows(IllegalArgumentException.class,()->PhoneNumber.normalize(value));
 }
 /** 外部号码没有中继则拒绝，不再硬编码 external。 */
 @Test void externalRouteRequiresRealConfiguration(){
  var mapper=mock(AgentRuntimeMapper.class);var policy=new OutboundRoutePolicy(mapper);
  assertThrows(ResponseStatusException.class,()->policy.resolve("13800000000"));
  when(mapper.outbound()).thenReturn(Map.of("gateway","carrier-a","caller","4000000000"));
  assertEquals("sofia/gateway/carrier-a/13800000000",policy.resolve("13800000000").dialString());
  when(mapper.outbound()).thenReturn(Map.of("gateway","bad/route","caller","4000000000"));
  assertThrows(ResponseStatusException.class,()->policy.resolve("13800000000"));
 }
 /** 所有客户查询使用认证坐席；歧义匹配不选第一个。 */
 @Test void customerOwnershipAndAmbiguousMatches(){
  var mapper=mock(CustomerMapper.class);var identity=mock(AgentIdentityService.class);
  when(identity.requirePrincipal()).thenReturn(new AgentIdentityService.Principal("alice"));
  var service=new CustomerService(mapper,identity);
  assertThrows(ResponseStatusException.class,()->service.detail("9007199254740993"));
  verify(mapper).detail("alice","9007199254740993");
  when(mapper.list("alice","1001",0,2)).thenReturn(List.of(new CustomerRecord(),new CustomerRecord()));
  assertTrue(service.match("alice","1001").isEmpty());
 }
 /** 乐观锁失败应保留他人或更新后的客户资料。 */
 @Test void customerUpdateRejectsStaleVersion(){
  var mapper=mock(CustomerMapper.class);var identity=mock(AgentIdentityService.class);
  when(identity.requirePrincipal()).thenReturn(new AgentIdentityService.Principal("alice"));
  var row=new CustomerRecord();row.setId("7");row.setName("客户");row.setPhoneNumber("1001");row.setVersion(0L);
  assertThrows(ResponseStatusException.class,()->new CustomerService(mapper,identity).save(row,false));
  verify(mapper,never()).insert(anyString(),any());
 }
}
