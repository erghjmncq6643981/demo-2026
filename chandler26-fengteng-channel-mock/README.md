# 风腾通道机接口 Mock

独立的 Java 17 Spring Boot 后端项目，根据《交互接口说明文档》V1.0（2026-09-24）模拟通道机接收 PDA 请求并返回箱号。

## 启动

```bash
cd chandler26-fengteng-channel-mock
mvn spring-boot:run
```

默认监听 `8080` 端口。可设置 `PORT` 和 `ASSET_COUNT` 环境变量；`ASSET_COUNT` 默认为 3，范围为 1～1000。

接口地址：`POST http://localhost:8080/privare-protocol/fengteng/event-task`。路径中的 `privare` 按文档原样保留。

## 调用示例

```bash
curl -X POST http://localhost:8080/privare-protocol/fengteng/event-task \
  -H 'Content-Type: application/json' \
  -d '{"version":"1.0","method":"scan-start","payload":{"taskType":0,"taskNo":"YD20260101001","variables":{"source":"pda"}}}'
```

`taskType=0` 表示扫码，返回模拟箱号，`shortCode` 和 `shortId` 均为空字符串；`taskType=1` 表示打垛，额外返回非空的模拟垛码和垛码 ID。`scan-start` 为 `taskNo` 建立扫码记录并生成首批箱号；省略 `method` 时也按开始扫码处理，响应中的 `method` 为空字符串。`variables` 原样透传，省略时返回空对象。

开始扫码后，可通过同一接口查询当前累计箱号：

```json
{"version":"1.0","method":"scan-info","payload":{"taskNo":"YD20260101001"}}
```

每次 `scan-info` 查询模拟新增 `ASSET_COUNT` 个箱号，返回该任务截至当前的全部箱号，最多累计 1000 个。`scan-info` 和 `scan-end` 只需传 `taskNo`，`taskType` 可省略；如果提供，必须与开始扫码时一致。`scan-end` 返回最终箱号并结束记录；结束后再查询会返回 HTTP 400。打垛任务的垛码和垛码 ID 在整个任务期间保持不变。

停止扫码请求示例：`{"version":"1.0","method":"scan-end","payload":{"taskNo":"YD20260101001"}}`。

连接检查使用同一接口，传 `{"version":"1.0","method":"scan-connect","payload":{}}`。无需 `taskType` 和 `taskNo`；响应 `successful=true`、`code=200`、`data.method="scan-connect"`，箱号列表为空，垛码和垛码 ID 为空字符串。`payload.variables` 如有传入会原样返回。

服务返回文档规定的 `successful`、`code`、`message`、`timestamp` 和 `data`。校验失败使用相同外层结构并返回 HTTP 400；这些错误响应是 mock 的约定，原文档未定义错误码细节。扫码记录只保存在当前进程中，重启后清空，垛码 ID 从 1001 重新开始。

## 验证

```bash
mvn -q -DskipTests compile
mvn -q test
```
