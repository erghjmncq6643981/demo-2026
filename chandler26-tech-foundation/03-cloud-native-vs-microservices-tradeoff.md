# 关键架构决策：微服务与云原生能力重叠的取舍与边界准则

> **问题背景**：构建新一代技术基座时，开发与运维团队最常发生争论的问题是：  
> *“既然 Kubernetes 已经有了 Service、CoreDNS、ConfigMap、HealthCheck，甚至还有 Istio Service Mesh，为什么还要用 Nacos、Spring Cloud LoadBalancer 和 Sentinel？它们到底谁取代谁？”*  
> 本文沉淀了明确的架构边界与选型决策，作为基座长期演进的指导原则。

---

## 一、 能力重叠的本质原因与两大误区

### 1. 历史演进的时代错位
*   **传统微服务（Spring Cloud）**：诞生于物理机/虚拟机（IaaS）时代。底层基础设施只给 IP 和操作系统，应用层的注册发现、配置热推、负载均衡、容灾保护必须**打包在“进程内的厚 SDK”**中；
*   **云原生体系（Kubernetes / Cloud Native）**：诞生于容器时代。主张**“应用只写业务代码，非业务治理全部下沉到底层平台或 Sidecar 代理”**。

### 2. 必须规避的两大极端误区
*   **误区 A（把 K8s 当高级虚拟机）**：  
    把老旧的一整套 Spring Cloud 强搬上 K8s，忽略 K8s 的调度、生命周期探针和 HPA，甚至还手写大量线程去监视 Pod，系统极度臃肿。
*   **误区 B（云原生教条主义，强推全量 Service Mesh）**：  
    试图用 K8s Service / Envoy 取代一切 Java 治理。结果发现：ConfigMap 无法秒级热更 Java Bean、K8s DNS 扛不住微服务高并发、Envoy 识别不了 Java 业务异常和复杂灰度规则，且引入了巨大的网络跃点延迟与内存翻倍开销。

---

## 二、 核心治理能力取舍与裁决矩阵

| 领域 | 微服务层 (Spring Cloud / Nacos) | 云原生层 (Kubernetes / Mesh) | **最终架构裁决** | 深度决策理由与权衡 |
| :--- | :--- | :--- | :--- | :--- |
| **内部 RPC 寻址** | **Nacos** (基于 gRPC，客户端维护列表直连目标 Pod) | **K8s Service / CoreDNS** (基于域名解析与 iptables/IPVS 四层转发) | **坚决选 Nacos**<br>*(K8s Service 仅用于入口接入)* | • K8s CoreDNS 无法承受数千微服务高频 DNS 解析，极易出现单点阻塞；<br>• Nacos 原生携带丰富的**服务元数据（Metadata：权重、泳道版本、分组）**，这是高阶灰度路由的基石，而 K8s Service 纯基于 IP 四层轮询，毫无业务语义。 |
| **配置管理** | **Nacos Config** (基于 gRPC 长轮询，毫秒级热更) | **K8s ConfigMap / Secret** (通过 Volume 挂载文件或系统环境变量注入) | **业务配置选 Nacos**<br>**环境常量选 ConfigMap** | • **K8s ConfigMap 存“死配置”**：如激活环境名 `SPRING_PROFILES_ACTIVE=prod`、Nacos 地址 `NACOS_ADDR`、JVM 堆内存参数；<br>• **Nacos 存“活配置”**：所有业务参数、动态开关（Feature Toggle）、规则引擎；享受秒级热刷新、版本对比 Diff 审计（吸收老系统 `proplus` 经验）。 |
| **服务负载均衡** | **Spring Cloud LoadBalancer** (应用进程内计算加权算法) | **kube-proxy / Ingress / Envoy** | **东西向选 SC LoadBalancer**<br>**南北向选 Ingress 网关** | • **微服务间互调（东西向）**：用 SC LoadBalancer 直接根据 Nacos 元数据算目标 IP，直连通信无网络损耗，且能读取 Java 上下文（用户 ID、泳道 Tag）；<br>• **外部入口打入（南北向）**：用 Ingress 统一配置 SSL 卸载、域名路由与入口防刷。 |
| **容灾与限流** | **Sentinel / Resilience4j** (Java 线程级与代码方法级) | **Envoy / Istio** (基于网络连接与 HTTP Status Code) | **选微服务层 (Sentinel)** | 云原生代理只知道请求是否超时或 503，根本无法感知 Java 内部的方法粒度、数据库慢 SQL、自定义业务异常（如余额不足、验签失败），也无法做热点参数限流。 |
| **无损上下线** | Spring Boot 优雅停机与健康端点 `/actuator/health` | **K8s 就绪探针 + `preStop` 容器生命周期钩子** | **两者深度联动 (1+1>2)** | 单靠任一方均无法做到真无损：必须由 K8s `preStop` 触发实例下线，留出 15 秒排空窗口，再由 Spring Boot 执行优雅关机。 |
| **弹性伸缩** | 无能力（Java 无法主动增加服务器） | **K8s HPA / KEDA** (基于 CPU/内存/业务指标自动扩增 Pod) | **坚决选云原生 (K8s)** | 容器调度与底层资源弹性是 K8s 的天生强项，微服务层完全无需干预。 |
| **金丝雀灰度发布**| 纯自研发布平台（如老系统 `bettercds` 1000+ 文件） | **Argo Rollouts (云原生声明式 CRD)** | **平台管审批，切流管 Rollout** | 放弃自研繁杂的 Ingress patch 与 Pod 轮询，统一由 Argo Rollouts 标准控制器驱动灰度。 |

---

## 三、 黄金分工准则（“四要四不要”）

为了避免技术基座开发走弯路，确立如下落地红线：

```
【四要】
1. 要把“南北向流量（入口）与金丝雀切流”交给云原生 Ingress 与 Argo Rollouts；
2. 要把“东西向流量（内部 RPC）与细粒度加权”留在微服务（Nacos + SC LoadBalancer）；
3. 要把“弹性调度与容器生命周期”交给 K8s 调度器与探针；
4. 要把“动态业务配置与开关治理”留在 Nacos 并建立审批防错中台。

【四不要】
1. 不要用 K8s Service / CoreDNS 做微服务内部高并发 RPC 调用；
2. 不要用 K8s ConfigMap 存放频繁变动的业务开关与配置；
3. 不要现阶段盲目引入 Istio Service Mesh（在 Java 生态中性价比极低）；
4. 不要从零自研底层的容器发布调度引擎（善用 Argo Rollouts）。
```

---

## 四、 为什么暂不推行 Service Mesh (Istio)？

在架构评估中，我们明确**暂缓推行 Istio 全量网格化**，原因有三：
1. **网络跳数翻倍**：每次 RPC 调用从原来的 `Pod A -> Pod B` 变成 `Pod A -> Envoy Sidecar -> Envoy Sidecar -> Pod B`，跨服务调用延迟增加 2~5ms，在高并发链路下对 P99 损耗明显；
2. **资源成本激增**：每个 Pod 注入 Envoy 容器，通常额外消耗 128MB~256MB 内存和 0.1~0.2 核 CPU，在数千 Pod 规模下带来巨大的云资源浪费；
3. **排障黑盒与运维成本**：Envoy 的路由规则由 Pilot 转换，排障时需要抓取 Envoy 日志与 xDS 配置，对开发人员的心智负担极重。

**结论**：**“Spring Boot 3 + Nacos + Spring Cloud LoadBalancer”** 是当前 Java 体系中性能最高、运维成本最低、最稳定成熟的绝佳实践。
