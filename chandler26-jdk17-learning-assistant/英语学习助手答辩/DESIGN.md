# DESIGN.md — 基于大模型的英语学习助手 · 毕业答辩

## 0. 需求对齐

| 维度 | 结论 |
|---|---|
| 主题 | 《基于大模型的英语学习助手》系统设计与实现 |
| 场合 | 上海大学继续教育学院 专升本毕业设计答辩 |
| 受众 | 答辩委员会（计算机方向教师） |
| 时长 | 8 分钟 |
| 页数 | 11 页（封面 + 9 页正文 + 封底） |
| 侧重 | 技术实现为主：架构、模块治理、AI 网关、异步一致性、验证结果；研究背景压到 1 页 |
| 素材来源 | 论文正文与图表（作者本人成果）、后端仓库 `chandler26-jdk17-learning-assistant`、前端仓库 `...-web` 真实代码统计、论文中的真实运行截图 |

## 1. 全局视觉约定

### ① 画布与安全区
- 画布 1280×720
- Slide padding：上 72 / 右 60 / 下 60 / 左 60 → 内容区 1160×588
- 顶部深蓝标题条为通栏绝对定位（top 0，高 56），不占内容区
- 页脚绝对定位 y=670（下 padding 带内），左右 60

### ② 配色（学术宝蓝 + 白底）

| 槽位 | Hex | 用途 |
|---|---|---|
| 主色 | `#1E4FA8` | 顶条、编号块、章节标识、强调 |
| 中蓝 | `#3D7BD9` | 图表第二色、次级强调、流程箭头 |
| 浅蓝 | `#E8EFF8` | 结论条底、KPI 卡底、表格表头 |
| 强调红 | `#D9534F` | 仅限未达标指标、关键风险（全篇 ≤3 处） |
| 正文深墨 | `#1A2230` | 主文字 |
| 中灰 | `#4A5568` | 次级说明 |
| 浅灰 | `#8B97A8` | 注释、页脚 |
| 分隔线 | `#D6DCE5` / `#E5E7EB` | 卡片描边、行分隔 |
| 辅底 | `#F7F9FC` | 局部浅底（≤2 处/页） |
| 底色 | `#FFFFFF` | 主背景（≥60%） |

纪律：蓝族 20–40%，红黄合计 ≤5%，无金色，无紫粉，无大色块。

### ③ 字体

- 中文：`微软雅黑`（无衬线，答辩现场投影可读）
- 西文/数字：`Arial`（数字统一右对齐）
- 字号阶梯：封面主标 60 / 页面主标 32 / 卡片小标 22 / 正文 19 / 表体 16 / 注释 14 / KPI 巨数字 64

### ④ 视觉母题（全篇复用）

1. 通栏深蓝标题条：左标题 32px 白字，右侧 `0X · 英文章节名` 16px，透明度 0.72 白
2. 页脚：左「基于大模型的英语学习助手 · 毕业答辩」，右 `0X / 11`，14px `#8B97A8`
3. 页面底部结论条：浅蓝底 `#E8EFF8`，左侧 3px 主色竖条，一行结论

### ⑤ 配图清单

| 文件 | 等级 | 来源 | 真实内容 | 使用页 |
|---|---|---|---|---|
| `arch_layers.png` | L1 | 论文原图 | 五层架构：表示层 / API 与安全层 / 业务域应用层 / 领域与基础设施 / MySQL + AI 网关 | P3 |
| `biz_flow.png` | L1 | 论文原图 | 词汇学习闭环三段流程（资源准备 / 计划与生成 / 学习与复习） | P5 |
| `ui_challenge.png` | L2 | 真实运行截图 | 词汇大挑战周历，151/5087 词 | P8 |
| `ui_scene.png` | L2 | 真实运行截图 | 场景文章 + 译文折叠，37 个待挑战词 | P8 |
| `ui_reading.png` | L2 | 真实运行截图 | 语境精读，8 句原文 + 核心词高亮 | P8 |
| `ui_review.png` | L2 | 真实运行截图 | 复习跟敲页 a-b-i-l-i-t-y | P8 |

不使用 ImageGen：学术答辩所有图示必须来自论文原图或真实截图，禁止生成图替代。

## 2. 逐页设计

### P1 封面
- type: cover / role: hero / rhythm: peak / layout: 左对齐信息带 + 通栏顶部细条
- visual: 无图（学术封面不放图） / visual_role: none
- density: 字数约 80 / 图 0 / 留白约 45%
- description: kicker「上海大学继续教育学院 · 专升本毕业设计答辩」；主标「基于大模型的英语学习助手」；英文「An English Learning Assistant Based on Large Language Models」；副标「系统设计与实现」；信息层：答辩人 钱丁君（234A0776）、指导教师 刘福岩、2026 年 10 月。左侧一条 6px 主色竖条建立视觉锚点。
- anti_pattern: 禁止放数据指标、禁止深色大底、禁止装饰插图、禁止页脚

### P2 研究问题
- type: content / role: supporting / rhythm: valley / layout: 左 58% 三段断层（编号 + 细线）/ 右 42% 研究问题与目标
- visual: 无图，靠排版 / visual_role: none
- density: 字数约 300 / 图 0 / 留白约 18%
- description: 三条断层各配一句可验证的事实：① 内容固化（Markdown 整表导入，改一句要重导全表）② 输出易失（会话关闭即丢，进不了复习排期）③ 流程割裂（词表与复习两套数据，掌握度各记各的）。右侧提出研究问题「怎么把模型的生成能力变成学习者留得下的资产」，并给出 3 条可考核目标：闭环可跑通、边界可审计、指标可测量。
- anti_pattern: 禁止三卡等宽横排；禁止「三大痛点」式空洞小标题

### P3 总体架构
- type: content / role: hero / rhythm: peak / layout: 左 58% 架构图 + 右 42% 四项决策
- visual: L1 `arch_layers.png`（左栏，占内容区约 42%） / visual_role: anchor
- density: 字数约 260 / 图 1 / 留白约 15%
- description: 图讲五层链路：表示层（HTML/CSS/ES Modules）→ API 与安全层（Controller / JWT / 声明式权限）→ 业务域应用层（identity / vocabulary / learning / reading / task / system / ai）→ 领域与基础设施（Domain / MyBatis-Plus / 事件 / Outbox）→ MySQL 与 AI 网关（DeepSeek / Kimi）。右侧四项决策附理由：模块化单体不拆微服务（1 进程 1 jar）、供应商差异只在网关、单表走 MyBatis-Plus 复杂 SQL 走 XML、Long ID 序列化为字符串防 JS 精度丢失。底部 KPI 行：400 个 Java 类 / 127 个接口 / 39 张表 / 83 个前端模块。
- anti_pattern: 禁止把架构图缩成配图大小；禁止右栏四项等宽卡片

### P4 模块与依赖治理
- type: content / role: supporting / rhythm: valley / layout: 左 52% 域规模条形 + 右 48% 依赖规则与治理测试
- visual: Box 绘制的横向条形图（无图片） / visual_role: evidence
- density: 字数约 280 / 图 0 / 留白约 12%
- description: 条形图给出真实类数：vocabulary 106、learning 82、ai 81、identity 29、task 25、system 21、reading 18（合计 400，含 config/security/common）。右栏四条硬规则：api→application→domain、infrastructure→domain、控制器不碰 Mapper、跨域只走窄化应用契约；再加两条规模治理：单文件超限必须按职责拆分、对外接口必须有中文说明。列出四个治理测试类名。
- anti_pattern: 禁止把 7 个域做成 7 张等大卡片；禁止只写「高内聚低耦合」

### P5 学习闭环
- type: content / role: supporting / rhythm: valley / layout: 上图下文，图占 62%，下方三约束
- visual: L1 `biz_flow.png` / visual_role: anchor
- density: 字数约 200 / 图 1 / 留白约 18%
- description: 图讲三段：管理员导入 Markdown 词表 → 格式校验与断词审核 → AI 语义分析；学习者选词表建计划 → 按语义相关性组候选 → 日目标均分；异步生成场景材料 → 阅读与词汇挑战 → 写逐词记录 → 进入间隔复习。下方三条实现约束：单篇 ≤50 词且篇间差 ≤1（80 词分成 2×40、120 词分成 3×40）、材料 revision 不可变（重新生成不改历史记录）、旧词可回流复习但保留原归属。
- anti_pattern: 禁止把流程图裁掉说明文字；禁止三条约束做成等宽卡

### P6 AI 网关
- type: content / role: hero / rhythm: peak / layout: 上方横向五段流程链 + 下方三条实现要点
- visual: Box/箭头自绘流程链 / visual_role: anchor
- density: 字数约 320 / 图 0 / 留白约 12%
- description: 链路：调用场景枚举 → 请求适配器（按供应商/协议拼 messages、temperature、max_tokens）→ 上下文预算（窗口 × 90% 阈值）→ 供应商（DeepSeek / Kimi）→ 响应解析器（直读 JSON，失败剥 Markdown 代码块）→ 场景编解码（字段别名归一化 + 必需根字段校验）。三条要点：模型能力全部存库不写死；固定动作不带历史会话；结构化响应缺必需字段时定点重试 2 次，网络/配额/内容安全失败不重试。
- anti_pattern: 禁止把流程做成五个等宽方块；禁止写「智能调度」这类无实现支撑的词

### P7 异步与一致性
- type: content / role: supporting / rhythm: valley / layout: 三栏不等宽（34% / 38% / 28%）
- visual: 无图，细线分区 / visual_role: none
- density: 字数约 300 / 图 0 / 留白约 15%
- description: ①任务三层记录：learning_ai_async_task / _step / _attempt，状态 pending→running→succeeded/partial/failed，失败只重试失败项；②事务边界：短事务里原子领取任务，提交后事件把外部调用交给有界 aiTaskExecutor，绝不在事务内等 HTTP；③一致性三边界：公共词汇缓存可刷新、个人快照与复习排期不可被刷新冲掉、生成历史只追加。附审计 Outbox：写库失败只丢日志、不回滚业务。
- anti_pattern: 禁止三栏完全等宽；禁止堆类名不解释作用

### P8 功能界面
- type: content / role: supporting / rhythm: valley / layout: 2×2 截图矩阵，每格左图右注
- visual: L2 四张真实截图 / visual_role: evidence
- density: 字数约 160 / 图 4 / 留白约 10%
- description: 四个真实页面各配一句可核对的事实：词汇大挑战（151/5087 词，7 月周历，每日 35–217 词）、场景文章（37 个待挑战词，译文可折叠）、语境精读（8 句原文，aboard/above 高亮，学习中等三种状态）、复习跟敲（逐字输入 a-b-i-l-i-t-y，三档释义 + 记忆反馈）。
- anti_pattern: 禁止截图拉伸变形；禁止用示意图替代真实截图

### P9 验证结果
- type: content / role: hero / rhythm: peak / layout: 左 45% KPI 三联 + 右 55% 指标表
- visual: 表格（透明底，重点行加粗）+ KPI 巨数字 / visual_role: anchor
- density: 字数约 240 / 图 0 / 留白约 15%
- description: KPI：100 并发只读接口全部成功、首屏 P95 1989 ms、编辑距离 ≤2 的 Top-1 准确率 97.17%。表格四行：缓存复用率目标 ≥95% 实测 83.80%（未达标，红字）、模型调用成功率 95.49%、自动判题正确率 97.62%、后端测试 140 项 139 通过 1 项因 Docker 不可用跳过。底部数据链：5087 词 / 82 个学习单元 / 419 条学习记录。
- anti_pattern: 禁止只报喜不报忧、禁止把未达标项藏进小字；禁止四个 KPI 平均用力

### P10 结论与边界
- type: content / role: supporting / rhythm: valley / layout: 上结论三条竖排 + 下两栏（局限 / 后续）
- visual: 无图 / visual_role: none
- density: 字数约 300 / 图 0 / 留白约 15%
- description: 结论三条：把模型调用收敛为可审计任务；缓存与个人快照分层，学习资产不被刷新冲掉；六域模块化单体在没有微服务运维成本的前提下守住了边界。局限三条：样本只有单个学习者、并发与首屏只在本地测、生成内容缺人工纠错与事实核验。后续三条：自适应间隔重复 + A/B、生成内容治理闭环、把骨架迁到听说读写其他场景。
- anti_pattern: 禁止写成「展望未来」空话；禁止把局限放在角落小字

### P11 封底
- type: ending / role: hero / rhythm: peak / layout: 居中大字
- visual: 无图 / visual_role: none
- density: 字数约 30 / 图 0 / 留白约 60%
- description: 「请各位老师批评指正」+ 答辩人 / 学号 / 日期。不放页脚。
- anti_pattern: 禁止加致谢长文、禁止放联系方式堆砌
