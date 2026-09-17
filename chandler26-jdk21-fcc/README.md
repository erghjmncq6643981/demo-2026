# chandler26-jdk21-fcc

基于 Java 21、Spring Boot 4、NATS 与 FreeSWITCH Sidecar 的 FCC（FreeSWITCH Call Center）控制面多模块工程。

当前版本只包含工程骨架、架构设计和数据库 DDL，不包含业务实现。

## 模块

- `fcc-common`：跨模块共享的基础类型、异常、枚举和工具。
- `fcc-server`：通话控制、流程编排、FCC/NATS 通信及持久化核心。
- `fcc-server-starter`：核心服务可执行入口。
- `fcc-admin`：管理端查询、配置及运维接口。
- `fcc-admin-starter`：管理服务可执行入口。

## 环境

- JDK 21
- Maven 3.6.3+
- MySQL 8.0+
- Redis 7+
- NATS 2.x

## 构建

```bash
mvn clean verify
```

## 文档

- [架构设计](docs/DESIGN.md)
- [数据库 DDL](docs/fcc-schema.sql)

