# FCC 文档导航

本目录是 FCC 跨工程文档入口。文档按“产品事实、工程设计、交付计划、验证手册”分层；源码与数据库基线是实现细节的最终依据，阶段性修复记录和一次性测试结果不作为长期文档保留。

## 当前事实

| 文档 | 说明 |
| --- | --- |
| [产品设计](fcc-product-design.md) | 产品目标、系统职责、已实现能力与已知缺口 |
| [Java 控制面设计](../chandler26-jdk21-fcc/docs/DESIGN.md) | fcc-admin、fcc-server、公共协议、流程与持久化边界 |
| [Sidecar 设计](../../cloud-2025/chandler25-fs-sidecar-agent/docs/DESIGN.md) | NATS、ESL、FNode、事件、TTS 与节点治理 |
| [前端架构与工程治理](frontend-architecture-and-ui-design.md) | 管理端、坐席端和运维端的适配器、状态及 UI 准入规则 |
| [IVR Flow Studio](../chandler26-fcc-admin-web/docs/IVR_FLOW_ORCHESTRATION_DESIGN.md) | 固定流程模型的维护交互与版本语义 |
| [话单与通话轨迹](../chandler26-fcc-admin-web/docs/CALL_FLOW_AND_TRACE_DESIGN.md) | CDR、Leg、执行轨迹、录音和回拨展示边界 |

## 计划与验证

| 文档 | 说明 |
| --- | --- |
| [交付计划](fcc-delivery-plan.md) | 唯一的优先级、依赖、状态与完成条件来源 |
| [测试策略](testing-architecture-and-test-cases.md) | 分层测试矩阵、必测场景、标准命令和结果记录格式 |
| [跨电脑部署与验收](fcc-cross-machine-acceptance.md) | 环境准备、部署顺序、真实通话验收和证据要求 |

## 维护规则

- 架构文档只记录当前代码与稳定决策；未实现能力进入“已知缺口”。
- 交付状态只在交付计划中维护，其他文档不复制 P0/P1 清单。
- 一次执行的测试数量、日期和机器故障写入任务交付记录，不回填长期文档。
- “源码存在”“自动测试通过”“真实 FreeSWITCH/SIP/媒体/Windows 验收通过”必须分别表述。
- Schema 以 [fcc-schema.sql](../chandler26-jdk21-fcc/docs/fcc-schema.sql) 为准；固定通话模型以 `fcc-common/src/main/resources/flows/system-models.json` 为准。
- 文档不记录口令、令牌、真实电话号码、供应商密钥或可直接访问的内部地址。
