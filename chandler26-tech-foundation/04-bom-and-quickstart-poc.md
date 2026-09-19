# 统一 BOM 依赖规范与本地 K8s 快速验证指南 (PoC)

> **目标**：给出工业级规范的统一 Maven BOM 依赖模型，并基于您本地已运行的 **Docker Desktop (Kubernetes)**，提供一份开箱即用、可快速复现“服务治理 + 金丝雀灰度发布”的实操指南。

---

## 一、 工业级统一依赖管理（`infra-bom` 根设计）

老系统最大的痛点之一是 20+ 个 Starter 各自依赖不同版本的 Spring Boot、Jackson、Guava 等，导致线上经常出现 `NoSuchMethodError` / `ClassNotFoundException`。  
在新基座中，所有版本由统一的 **`infra-bom`** 强制仲裁锁定：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>cn.chandler26.infra</groupId>
    <artifactId>infra-bom</artifactId>
    <version>2026.1.0-RELEASE</version>
    <packaging>pom</packaging>
    <name>Infra BOM :: Bill of Materials</name>
    <description>新一代微服务基础设施依赖版本统一仲裁中心</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- 核心三大框架基线版本 -->
        <spring-boot.version>3.3.4</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- 1. Spring Boot 统一依赖 -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 2. Spring Cloud 统一依赖 -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 3. Spring Cloud Alibaba (Nacos 2.4+ / Sentinel) 统一依赖 -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

---

## 二、 业务微服务极简脚手架设计（`infra-core-starter`）

业务团队只需要引入一个依赖，禁止引入杂乱的第三方底层 jar：

```xml
<dependencies>
    <!-- 业务只需这一个脚手架依赖，自动拥有以下能力：
         1. Java 21 虚拟线程支持
         2. Nacos 动态配置 + 注册发现
         3. Spring Cloud LoadBalancer 加权灰度路由
         4. 统一 Web 拦截与全局异常捕获
         5. Spring Boot Actuator 健康探针与优雅停机
         6. Prometheus 监控指标输出 -->
    <dependency>
        <groupId>cn.chandler26.infra</groupId>
        <artifactId>infra-core-starter</artifactId>
        <version>2026.1.0-RELEASE</version>
    </dependency>
</dependencies>
```

---

## 三、 本地 Docker Desktop K8s 验证实操（PoC 4 步走）

在当前电脑的终端直接按如下步骤操作，搭建一套完整的本地试验台：

### 第 1 步：本地拉取核心容器镜像
```bash
docker pull nacos/nacos-server:v2.4.3
docker pull eclipse-temurin:21-jre-alpine
docker pull registry.k8s.io/ingress-nginx/controller:v1.11.2
docker pull quay.io/argoproj/argo-rollouts:v1.7.2
docker pull prom/prometheus:v2.54.1
```

### 第 2 步：安装 Ingress-Nginx 与 Argo Rollouts
```bash
# 1. 安装 Ingress Nginx 控制器 (Docker Desktop 官方配置)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.2/deploy/static/provider/cloud/deploy.yaml

# 2. 安装 Argo Rollouts 控制器
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/download/v1.7.2/install.yaml

# 3. 安装 kubectl argo rollouts 终端插件 (用于直观观察灰度切流)
brew install argoproj/tap/kubectl-argo-rollouts
```

### 第 3 步：一键部署单机 Nacos 2.4（极省资源）
创建文件 `nacos-local.yaml` 并执行 `kubectl apply -f nacos-local.yaml`：
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: infra
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nacos-server
  namespace: infra
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nacos-server
  template:
    metadata:
      labels:
        app: nacos-server
    spec:
      containers:
        - name: nacos
          image: nacos/nacos-server:v2.4.3
          imagePullPolicy: IfNotPresent
          env:
            - name: MODE
              value: "standalone"
            - name: PREFER_HOST_MODE
              value: "hostname"
          ports:
            - containerPort: 8848
              name: http
            - containerPort: 9848
              name: grpc
---
apiVersion: v1
kind: Service
metadata:
  name: nacos-service
  namespace: infra
spec:
  type: NodePort
  ports:
    - port: 8848
      targetPort: 8848
      nodePort: 30848
      name: http
    - port: 9848
      targetPort: 9848
      nodePort: 30948
      name: grpc
  selector:
    app: nacos-server
```
*部署后可在本地浏览器访问：`http://localhost:30848/nacos` (默认用户名/密码：nacos/nacos)*。

### 第 4 步：验证金丝雀灰度切流（Argo Rollouts 体验）
发布新版本时，在终端执行命令：
```bash
# 1. 触发金丝雀发版更新
kubectl argo rollouts set image order-rollout order-service=order-service:v2.0.0 -n prod

# 2. 实时打开终端 Dashboard，观察权重切分
kubectl argo rollouts get rollout order-rollout -n prod --watch
```
**观察效果**：  
可以看到终端呈现出：新旧两组 Pod 并存，外部流量严格按照 `setWeight: 10`（10% 切入灰度），并在通过指标分析后自动推进到全量，达成发版环境全自动化的平滑闭环！
