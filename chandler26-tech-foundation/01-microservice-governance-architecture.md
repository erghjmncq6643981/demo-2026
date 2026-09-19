# 方向一：新一代微服务治理基座体系设计

> **定位**：在 JDK 21 + Spring Boot 3.3/3.4 + Spring Cloud + Nacos 2.4+ 的现代技术栈上，完整传承企业级微服务治理经验，实现极致吞吐、动态路由与无损上下线。

---

## 一、 为什么选择这套技术栈？

### 1. JDK 21 LTS：彻底颠覆微服务并发吞吐
*   **虚拟线程（Virtual Threads - JEP 444）**：  
    在传统 Spring Boot 2.x（平台线程）中，每个并发 HTTP 请求独占一个 OS 线程（约 1MB 栈内存），Tomcat 默认 200 线程，遇到 I/O 阻塞（DB 查询、RPC 调用、Redis 访问）极易耗尽线程池。  
    开启 JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`）后，线程创建开销几乎为零，可在单机轻松维持上万并发，显著降低线程切换开销与内存占用。
*   **模式匹配与 Record 特性**：使 DTO、事件消息定义和统一返回体更加轻量、安全。

### 2. Nacos 2.4+：统一收敛“注册中心”与“配置中心”
*   在老系统中，使用了 Consul（服务注册）+ ZooKeeper（调度协调）+ ConfPlus/configservice（配置中心），维护 4 套异构系统，运维极其繁重。
*   Nacos 2.4 基于 **gRPC 长连接流式推送**，连接保持与消息下发延迟从秒级降至毫秒级，同时原生支持服务实例元数据（Metadata）、集群/分组隔离以及配置历史版本对比。

---

## 二、 深度吸取老系统治理经验并现代重构

### 经验传承一：加权灰度路由（升级自 `consul-starter`）

#### 老系统精髓：
老系统在 [DefaultRoute.java](file:///Users/chandler/Documents/repository/gitlab-shuhe/consul-starter/src/main/java/cn/caijiajia/consul/starter/loadbalance/DefaultRoute.java) 中通过 `WeightElement` 和 `RandomSampler` 实现了客户端加权随机路由，并在权重小于等于 0 时主动过滤该节点。

#### 现代重构方案（Spring Cloud LoadBalancer）：
废弃已停更的 Ribbon，通过自定义 `ReactorServiceInstanceLoadBalancer` 扩展 Spring Cloud LoadBalancer：

```java
package cn.chandler26.infra.governance.loadbalancer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 现代加权灰度与全链路泳道负载均衡器
 * 继承自 DefaultRoute.java 的加权采样思想
 */
public class ModernWeightedLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
    private final String serviceId;

    public ModernWeightedLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider, String serviceId) {
        this.supplierProvider = supplierProvider;
        this.serviceId = serviceId;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next().map(instances -> selectInstance(instances, request));
    }

    private Response<ServiceInstance> selectInstance(List<ServiceInstance> instances, Request request) {
        if (instances.isEmpty()) {
            return new EmptyResponse();
        }

        // 1. 泳道特征提取（支持从当前请求上下文中提取 x-lane 标签）
        String targetLane = extractTargetLane(request);

        // 2. 过滤候选实例：
        // 规则 A：优先命中相同泳道标签的实例；
        // 规则 B：严格过滤 weight <= 0 的实例（老系统核心经验：权重为0表示下线/发布中）
        List<ServiceInstance> candidates = instances.stream()
                .filter(inst -> {
                    String lane = inst.getMetadata().getOrDefault("lane", "default");
                    return targetLane != null ? targetLane.equals(lane) : "default".equals(lane);
                })
                .filter(inst -> {
                    int weight = Integer.parseInt(inst.getMetadata().getOrDefault("weight", "100"));
                    return weight > 0;
                })
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            // 降级兜底：若特定泳道无实例，回退至基线 default 实例
            candidates = instances.stream()
                    .filter(inst -> "default".equals(inst.getMetadata().getOrDefault("lane", "default")))
                    .filter(inst -> Integer.parseInt(inst.getMetadata().getOrDefault("weight", "100")) > 0)
                    .collect(Collectors.toList());
        }

        // 3. 基于权重的平滑随机采样 (Weighted Random Sampling)
        return new DefaultResponse(chooseByWeight(candidates));
    }

    private ServiceInstance chooseByWeight(List<ServiceInstance> candidates) {
        int totalWeight = candidates.stream()
                .mapToInt(inst -> Integer.parseInt(inst.getMetadata().getOrDefault("weight", "100")))
                .sum();

        if (totalWeight <= 0) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;
        for (ServiceInstance instance : candidates) {
            currentWeight += Integer.parseInt(instance.getMetadata().getOrDefault("weight", "100"));
            if (randomWeight < currentWeight) {
                return instance;
            }
        }
        return candidates.get(0);
    }

    private String extractTargetLane(Request request) {
        if (request.getContext() instanceof RequestDataContext context) {
            return context.getClientRequest().getHeaders().getFirst("x-lane");
        }
        return null;
    }
}
```

---

### 经验传承二：全自动无损上下线与调度防打扰（升级自 `schedulerplus`）

#### 老系统痛点与精髓：
*   **精髓**：[WeightChangeListener.java](file:///Users/chandler/Documents/repository/gitlab-shuhe/schedulerplus/schedulerplus/src/main/java/cn/caijiajia/schedulerplus/core/WeightChangeListener.java) 中，当发布检测到 `weight == 0` 时，调度平台立即为该节点设置 `deleteFlag`，停止分发批处理 Job。
*   **痛点**：老系统依赖外部发布平台通过 API 轮询写 Consul，链路较长且偶发通知延迟。

#### 现代重构方案（K8s 探针 + preStop + Spring Actuator 闭环）：

1. **容器生命周期 preStop 脚本定义（标准模板）**：
   在 Pod 定义中配置优雅下线钩子，先主动向 Nacos 去注册，并 Sleep 等待调用端缓存刷新：
   ```yaml
   lifecycle:
     preStop:
       exec:
         command:
           - "/bin/sh"
           - "-c"
           - |
             # 1. 标记 Spring Boot 注册状态为 DOWN，向 Nacos 注销该实例或将权重置零
             curl -X POST "http://127.0.0.1:8080/actuator/service-registry?status=DOWN" -s -o /dev/null
             # 2. 通知内部分布式调度客户端：暂停本节点任务领取
             curl -X POST "http://127.0.0.1:8080/actuator/job-governance/pause" -s -o /dev/null
             # 3. 核心等待窗口：等待各网关与上游微服务的本地路由缓存失效 (推荐 10-15s)
             sleep 15
   ```
2. **Spring Boot 3 原生优雅停机开启**：
   ```yaml
   server:
     shutdown: graceful # 开启优雅停机，不再接受新请求，等待存量请求处理完成
   spring:
     lifecycle:
       timeout-per-shutdown-phase: 30s # 最长缓冲 30 秒
   ```
3. **就绪探针（Readiness Probe）保证无损上线**：
   ```yaml
   readinessProbe:
     httpGet:
       path: /actuator/health/readiness
       port: 8080
     initialDelaySeconds: 15
     periodSeconds: 5
     failureThreshold: 3
   ```

---

### 经验传承三：生产配置发布审批与 Diff 审计（升级自 `proplus`）

#### 老系统精髓：
老系统 [proplus](file:///Users/chandler/Documents/repository/gitlab-shuhe/proplus) 深刻认识到：**“配置变更是第一大致障源”**。绝不允许研发人员在控制台随意修改生产配置，而是必须提交变更单、展示 Diff、挂接 Jira/审批流。

#### 现代重构方案（Nacos 管控代理平台）：
不把 Nacos 控制台的生产权限直接开放给研发，而是在其上方构建一个轻量级 **Config Control Plane**：
1. **变更发起**：用户在 Web 端修改某应用的 `application-prod.yaml`，平台自动调用 Nacos 获取当前已发布的线上内容，进行行级 **Unified Diff 高亮比对**；
2. **风险分级与审批**：
   *   高危配置（如数据库连接池、线程池大小、超时时间）必须经过架构师/TL 审批；
   *   普通业务开关直接通过；
3. **金丝雀灰度推送（Canary Config）**：
   利用 Nacos 2.x 的 `Beta 实例灰度发布` 功能，先将配置推送至特定 1 台灰度 Pod，观察 5 分钟无异常后，再点击“全网发布”。

---

## 三、 本方案带来的架构收益

| 对比项 | 传统/老架构 | 新一代治理基座 |
| :--- | :--- | :--- |
| **并发吞吐能力** | 传统 I/O 阻塞模型，受限于 OS 线程数上限 | **Java 21 虚拟线程原生驱动，无阻塞开销** |
| **下线抖动与 502 率** | 依赖外部发布平台手动摘流，偶发 1~3s 报错 | **preStop + Nacos gRPC 秒级同步，0 抖动** |
| **调度任务安全性** | 实例下线时易造成执行中的批量 Job 中断 | **调度组件集成去注册感知，发布期拒绝派发** |
| **配置安全保障** | 裸改配置易引发全局故障 | **严格继承 `proplus` 的 Diff 比对与审批流防错** |
