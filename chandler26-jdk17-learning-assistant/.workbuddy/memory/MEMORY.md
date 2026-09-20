# 项目长期备忘

## 环境约定
- 本机代理：Clash 系，混合端口 **7897**（7890 未监听）。AI 调用默认不走代理（`learning.ai.http.proxy.enabled=false`）；需要代理时设 `LEARNING_AI_HTTP_PROXY_ENABLED=true`（host 127.0.0.1, port 7897）。
- MySQL：本机 /usr/local/mysql/bin/mysql，账号 micro/123456，库名 study；无 mysql CLI 于 PATH。
- AI 模型配置存于 `ai_model_config` 表（kimi2.6=kimi-k2.6 为默认启用）。
- Maven：PATH 无 mvn，用 `/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn` + `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`；必须联网（`-o` 离线会因私有仓库缺件失败）。

## 工程约定
- 模型返回"语法合法但缺必需字段"的 JSON 属上游非确定性输出：统一经 `ai/chat/application/AiStructuredResponseRetryPolicy` 定点重试（默认 2 次，追加纠正要求；网络/配额/内容安全类失败不重试）。新增长结构化生成入口应复用该策略，不要直接把缺字段判为任务失败。

## 用户背景
- 上海大学继续教育学院专升本毕业论文（基于大模型的英语学习助手），格式依据《上海大学本科毕业论文（设计）格式要求》PDF + 学院附件7模板。
- 论文关键口径：正文固定行距23磅；章标题黑体小二18pt；表题黑体小4置于表上方、图题宋体小4置于图下方；每章另起一页。
- 论文目录已改为 PAGEREF 域（书签 _Toc0000+），编辑后需 Ctrl+A + F9 刷新页码。
- 修改论文用 python-docx 直接改 XML（编辑器 SDK 表格/批量格式操作不可靠）。
