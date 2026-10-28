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
- **降 AIGC 率工作流**：按章节逐段处理，改写结果只输出到会话供用户确认，**不直接写回 docx**；处理顺序：摘要（中英文）→ 1.1 研究背景 → 1.2 研究意义 → ……
- **格式标准以维普成教模板为准**（用户已拍板）：与学校本科格式 PDF 冲突时一律听维普的。要点：正文 1.5 倍行距（非 23 磅）、二级标题小三、参考文献五号、页眉"上海大学继续教育学院毕业论文(设计)"仿宋五号右对齐。
- **降 AIGC 的核心规律**（维普实测）：带具体数据/代码/表格/参数值的段落判为安全；纯概括性、并列罗列式的段落必判 AI。改写时优先"加具体值 + 说清取舍与代价"，而不是只换句式。
- 降 AIGC 手法：破除"随着……的发展""针对……问题""这意味着……""因此，本文……"等模板句式；拆散三四项工整并列罗列；长短句交错；保留引用标号与作者观点。
