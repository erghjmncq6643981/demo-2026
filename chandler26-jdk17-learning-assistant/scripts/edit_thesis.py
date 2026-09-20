# -*- coding: utf-8 -*-
"""毕业论文第二章重构与第七章扩充：外科式 OOXML 编辑（lxml 无损往返）。"""
import zipfile, shutil, lxml.etree as ET
from copy import deepcopy

PATH = 'docs/基于大模型的英语学习助手-毕业论文.docx'
NS = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
W14 = '{http://schemas.microsoft.com/office/word/2010/wordml}'

z = zipfile.ZipFile(PATH)
raw = z.read('word/document.xml')
root = ET.fromstring(raw)
body = root.find(NS + 'body')
children = list(body)

def txt(e):
    return ''.join(t.text or '' for t in e.iter(NS + 't'))

def find_p(exact):
    """按段落全文精确匹配定位 body 直接子段落。"""
    for i, c in enumerate(children):
        if c.tag == NS + 'p' and txt(c) == exact:
            return i
    raise ValueError('not found: ' + exact)

def strip_paraid(p):
    p.attrib.pop(W14 + 'paraId', None)

def make_body(text, tpl):
    p = deepcopy(tpl)
    strip_paraid(p)
    t = p.find('.//' + NS + 't')
    t.text = text
    return p

def make_heading(text, tpl):
    p = deepcopy(tpl)
    strip_paraid(p)
    for bm in p.findall(NS + 'bookmarkStart') + p.findall(NS + 'bookmarkEnd'):
        p.remove(bm)
    # also nested bookmarks
    for bm in p.iter(NS + 'bookmarkStart'):
        bm.getparent().remove(bm)
    for bm in p.iter(NS + 'bookmarkEnd'):
        bm.getparent().remove(bm)
    t = p.find('.//' + NS + 't')
    t.text = text
    return p

BODY_TPL = children[133]      # 正文模板：TNR+宋体 小四 行距460 exact 首行480 两端对齐
H3_TPL = children[132]        # 节标题模板：pStyle 3
TOC_TPL = children[55]       # 目录条目模板：tab+点引导线+页码

# ---------- 第二章内容 ----------
SEC_2_1 = [
    '在语言与运行时层面，Java 17 相较早期版本为本系统提供了多项可直接受益的特性。文本块（Text Blocks）使多行 SQL 片段与提示词模板得以保持可读的缩进结构，减少字符串拼接造成的格式错误；记录类（Record）以不可变载体承载接口传输对象，配合 Lombok 简化值对象与响应装配的样板代码；switch 表达式与模式匹配使 AI 调用场景等枚举的分发逻辑更加紧凑，降低了分支遗漏的风险。',
    'Spring Boot 在本系统中承担应用装配与横切支撑。自动配置与 Starter 依赖管理使各业务域只需声明所需能力即可获得 Web 容器、参数校验和配置绑定；Actuator 暴露健康、信息与 Prometheus 指标端点，为运行期可观测性提供统一入口。面向切面编程用于两类横切关注点：接口访问日志切面统一记录业务接口名、HTTP 方法、路径、用户标识、链路标识、耗时与成败，权限切面在方法进入前统一校验声明式权限，二者均避免在业务方法内部散落重复代码。',
    '声明式事务管理确保数据库操作的原子性边界由应用层显式划定，而非依赖运行时隐式推断。耗时外部调用不持有事务：场景材料、词卡与词本关联分析先持久化任务与业务状态，待事务提交后再通过事件与有界线程池异步执行模型调用，从而避免长事务与请求线程被外部 HTTP 阻塞。',
]
SEC_2_2 = [
    '在身份传播方面，系统遵循令牌只解析一次的原则。JWT 过滤器完成签名、签发方与过期校验后，将用户身份写入安全上下文与当前用户上下文；应用服务与审计基础设施在线程内直接获取当前用户，不再重复接收授权参数或重新查询用户，降低了凭据泄露面与重复查询开销。',
    '模型凭据保护采用应用层加密。供应商 API Key 使用 AES-GCM 算法在后端加密存储，列表接口仅返回脱敏值，连接测试以最小请求直接调用配置的供应商而非触发完整学习流程；生产与预发布环境启动时拒绝使用开发默认的签名密钥与加密密钥，防止默认凭据流入正式环境。统一异常与稳定错误码体系保证接口返回可读的业务语义，而非暴露技术堆栈。',
]
SEC_2_3 = [
    '数据库结构演进由 Flyway 统一管理。空库执行基线脚本建立完整初始结构，存量非空库以既定版本建立基线，后续迁移按版本号顺序追加，已执行的迁移意图不被重写，保证多环境结构一致且可追溯。当前完整结构、种子数据与历史升级脚本相互分离，执行顺序由目录说明文件约束。',
    '在查询与写入性能方面，系统遵循不在循环内执行 SQL 的原则。学习计划详情通过一次批量加载装配单元、材料、词条、进度与检测记录，避免逐单元产生 N+1 查询；场景完成度计算先批量读取通过记录再在内存分组。同类更新使用批量 SQL，大批次在服务层分块，分页列表接口保持响应体精简并依赖组合索引服务于日历摘要、待复习队列与任务状态分页等高频查询路径。',
]
# 新 2.4 大语言模型与多模型兼容接口
SEC_2_4 = [
    '大语言模型是本系统内容生成的核心能力来源。以 ChatGPT 为代表的大语言模型展现出文本理解、内容生成与知识问答能力，国内 DeepSeek、通义千问、Kimi、豆包与混元等模型快速发展，使垂直学习场景具备较好的技术基础。然而，通用聊天工具存在输出格式不统一、内容难以长期沉淀、无法结合个人进度安排复习等问题，本系统据此将模型生成能力置于结构化数据与可追踪任务之中。',
    '系统采用 OpenAI 兼容的对话补全协议作为统一接入层。该协议以消息角色、温度与结构化输出等通用参数描述请求，使不同供应商的模型可以通过统一接口接入并在前端切换，避免为每个供应商编写独立业务分支。当前系统已为 DeepSeek 与 Kimi 配置独立的请求适配器与响应解析链，模型管理记录调用次数、成功失败数、Token、平均延迟与最近调用时间，普通用户仅获得启用模型的最小信息。',
    'AI 网关将供应商、模型与协议视为能力元数据，按职责分离请求构造与响应处理。请求适配器依据供应商与协议构造请求体，响应解析器先解析供应商信封，再由场景编解码注册表按调用场景解包、归一化字段别名并校验必需根字段，业务服务直接消费归一化后的结构。每次模型调用必须声明调用场景枚举，枚举同时声明响应是否必须为 JSON、必须存在的根字段，以及固定动作允许进入提示词的请求变量，避免把无关对象或历史对话带入模型。',
    '上下文预算与输出约束按所选模型的真实能力计算。系统按模型的实际上下文窗口与输出上限估算输入与输出总量，在达到安全阈值前拒绝或拆分请求，防止长期学习计划的历史消息在后续独立调用中持续累积。固定生成动作只携带当前任务所需数据，仅在当前学习场景确需连续上下文时才复用会话。模型 HTTP 日志不输出完整响应正文，审计默认仅保存元数据、Token 与耗时，敏感内容脱敏且截断。',
]
# 新 2.5 模块化单体与 ArchUnit 架构治理
SEC_2_5 = [
    '系统采用按业务域组织的模块化单体架构。后端划分为身份、词汇、学习、阅读、任务与系统六个业务域，AI 代码按 Agent、模型、会话与提示词分域，供应商协议、请求适配与响应解析集中在网关层。每个业务域内部使用接口、应用、领域与基础设施四层分层：接口层拆分为控制器、请求与响应，应用层承载用例服务、事务边界与编排，领域层放置实体、聚合、枚举与常量，基础设施层放置 Mapper 接口。',
    '依赖方向由架构测试持续约束。接口层依赖应用层，应用层依赖领域层，基础设施层依赖领域层；控制器不得直接访问 Mapper，领域代码不得依赖接口或基础设施，应用类仅可访问自身业务域的基础设施，跨域访问通过窄化的应用契约完成。架构测试同时阻止重新创建横向的控制器、服务与 Mapper 根级目录，强制实体驻留领域实体包、枚举驻留领域枚举包、Mapper 驻留基础设施 Mapper 包。',
    '除依赖方向外，工程治理还包含源码规模与文档约束。单文件超过既定行数必须按渲染、状态、策略或持久化职责拆分后方可合并；中文文档治理测试约束公共字段与复杂边界的注释要求。这些测试随每次提交执行，使代码边界在规模增长时仍能保持清晰，避免模块化单体退化为无序的大泥球。',
]
# 新 2.6 前端 ES Modules 与模块化组织
SEC_2_6 = [
    '前端采用 HTML、CSS 与原生 ES Modules 实现，不依赖编译构建步骤。模块按身份、词汇、学习、阅读、AI、任务与系统业务域拆分，场景计划与语境精读等复杂功能进一步拆出纯业务模型、预览数据与 API 网关，入口文件只负责装配而不承载业务逻辑。',
    '共享层统一提供跨域通用能力，包括 API 请求封装、弹窗、标识符字符串化、Markdown 渲染与本地偏好存储。后端将长整型标识统一序列化为字符串，前端把所有标识视为不透明字符串处理，规避 JavaScript 数值精度丢失。学习页与复习页共用同一份个人 Markdown 笔记，保证内容在两个场景间实时同步。',
    '为支持无数据库环境下的体验与演示，前端提供预览模式，在本机缺少数据库时可走通主要产品流程。性能方面，计划首页与日历先加载轻量摘要，点击开始学习或回顾时再按计划与单元标识加载完整文章、核心词、词卡与学习状态，避免首屏一次性拉取大字段。',
]
# 新 2.8 缓存与异步任务执行机制
SEC_2_8 = [
    '系统使用多级缓存降低重复调用成本。公共词卡按归一化词条缓存，普通查询优先复用缓存；场景生成时先过滤不需词卡与已命中缓存的词，仅将缺失词按批次交给模型生成，并支持失败项重试。词本关联分析按标准化单词复用历史有效结果，仅缺失词调用模型，部分结果立即落库，重试只处理未完成词。',
    '公共缓存与个人数据严格分离。公共词卡是共享缓存数据，用户将词汇加入单词本时保存个人词卡快照，公共缓存后续被重新生成不会覆盖用户已有的学习内容、笔记与复习进度。单词本词条保留个人学习状态，相关词按同义、反义与词族组织，搭配独立存放，避免共享与个人数据相互污染。',
    '异步任务执行采用有界线程池与可重试任务模型。AI 线程池大小、队列、存活时间与停机等待时间均可配置，拒绝策略不会将昂贵的模型任务退回请求线程执行。可重试工作具有原子领取、条目状态、幂等写入、终态判定与失败项独立重试等特征，运行中任务取消后保留已完成结果。',
    '异步日志采用日志表外置与事件驱动落库。系统日志服务只负责写入日志外置表并发布事件，提交后异步监听器批量、幂等地将日志落库，恢复调度器重试未消费的外置记录；日志写入、监听、持久化或执行器拒绝异常被隔离，不回滚已成功的业务动作或改变接口业务结果。异步日志与异步任务通过统一执行器传播请求与链路上下文，保证跨线程可追溯。',
]

# ---------- 第七章内容 ----------
SEC_7_1 = [
    '在工程贡献方面，系统以模块化单体与架构测试持续约束业务域与分层边界，以 AI 网关的请求适配器与响应解析器分离供应商差异，以调用场景枚举约束固定动作的输入与结构化输出，以日志外置与事件驱动落库实现不影响业务结果的异步审计。声明式权限、统一异常与稳定错误码、标识符字符串化与开发默认凭据拒绝等机制，共同支撑了系统在规模增长下的可维护性与安全性。',
    '在验证方面，系统在本地真实数据、接口并发、浏览器渲染与拼写容错层面获得证据。100 并发只读接口均成功返回，业务数据就绪时首屏渲染 P95 为 1989 毫秒；编辑距离不大于 2 的样本 Top-1 准确率为 97.17%；稳定版本缓存复用率为 83.80%，模型调用成功率为 95.49%。本地数据库已形成从词表导入、学习计划、场景材料、词汇检查到复习记录的完整数据链，包含 5087 词公共词表、82 个学习单元与 419 条学习记录，自动判题正确率为 97.62%。',
]
SEC_7_2 = [
    '本课题已实现面向英语学习的主要业务闭环，但受限于研究条件与样本规模，仍存在若干不足，后续可从以下方向改进。',
    '在数据样本方面，真实学习数据主要来自单个学习者，尚未形成跨用户的学习效果对照，复习正确率与掌握度变化更适合用于验证数据闭环与功能可用性，而非推断普遍学习效果。',
    '在缓存效果方面，稳定版本有效样本的缓存复用率为 83.80%，尚未达到任务书不低于 95% 的目标，主要因当前样本仍存在大量首次学习词；随着词表覆盖率提升与用户规模增长，该指标需持续监测。',
    '在模型质量方面，生成内容仍依赖供应商稳定性，场景材料的后台生成平均耗时较高，且尚未对模型生成内容引入人工纠错、质量评分、事实核验与版本比较机制。',
    '在部署验证方面，并发与首屏性能基于本地环境测得，尚未在独立服务器与真实多机网络条件下完成验证，生产环境下的网络波动与资源约束可能带来不同表现。',
    '在复习算法方面，当前采用固定间隔序列，未根据实际回忆质量、间隔与词汇难度进行自适应调整，难以针对不同学习者的遗忘曲线实现精细化调度。',
    '针对上述不足，后续工作可从五个方向展开。',
    '第一，引入基于实际回忆质量、间隔与词汇难度的自适应间隔重复算法，并以 A/B 测试评估长期留存效果，使复习调度从固定序列走向个性化。',
    '第二，为模型生成内容建立人工纠错、质量评分、事实核验与版本比较闭环，在保留生成效率的同时提升内容的可信度与可追溯性。',
    '第三，将阅读检测从四选一扩展为细节定位、推理、开放题与错因诊断，覆盖更高层级的阅读理解能力，并为错题建立独立管理。',
    '第四，增加跟读功能与发音反馈，引入单词与文章相关图片提升视觉学习效果，丰富多通道学习体验。',
    '第五，将场景框架扩展到多模态听说、专业文献阅读与写作训练，验证该工程骨架在更多学习场景中的可迁移性。',
    '大语言模型的教育价值不在于生成更多文字，而在于能否被组织到清晰、可验证、可持续的学习过程之中。本课题通过缓存、快照、复习和审计建立了这一过程的工程骨架，为后续开展真实用户研究和长期学习效果评估提供了基础。',
]

# ========== 执行编辑 ==========
# --- 第二章：替换旧 2.4+2.5 块为 2.4/2.5/2.6/2.7/2.8 ---
i_24 = find_p('2.4 ES Modules与大模型兼容接口')
i_25 = find_p('2.5 HTML5音频与语音合成技术')
i_ch3 = find_p('第三章 系统需求分析')
# 旧块为 [i_24 .. i_ch3-1]，替换为新块
new_block = []
new_block.append(make_heading('2.4 大语言模型与多模型兼容接口', H3_TPL))
new_block += [make_body(t, BODY_TPL) for t in SEC_2_4]
new_block.append(make_heading('2.5 模块化单体与ArchUnit架构治理', H3_TPL))
new_block += [make_body(t, BODY_TPL) for t in SEC_2_5]
new_block.append(make_heading('2.6 前端ES Modules与模块化组织', H3_TPL))
new_block += [make_body(t, BODY_TPL) for t in SEC_2_6]
# 2.7 = 原 2.5 标题与正文（保留）：先记下原块中 2.5 标题及之后到 i_ch3-1 的元素
old_25_block = children[i_25:i_ch3]   # 含 2.5 标题及 3 段正文
new_block += old_25_block
new_block.append(make_heading('2.8 缓存与异步任务执行机制', H3_TPL))
new_block += [make_body(t, BODY_TPL) for t in SEC_2_8]
# 用新块替换旧块 [i_24:i_ch3]
# 先移除旧块
for idx in range(i_ch3 - 1, i_24 - 1, -1):
    body.remove(children[idx])
# 在 i_24 位置插入新块
for off, el in enumerate(new_block):
    body.insert(i_24 + off, el)

# 重新拉取 children（索引已变化）
children = list(body)

# --- 第二章：扩充 2.1/2.2/2.3 ---
def insert_before(anchor_exact, paras):
    global children
    children = list(body)
    j = find_p(anchor_exact)
    for off, t in enumerate(paras):
        body.insert(j + off, make_body(t, BODY_TPL))

insert_before('表2-1 系统技术栈', SEC_2_1)            # 2.1 扩充，置于表前
insert_before('2.3 MyBatis-Plus、MyBatis与MySQL', SEC_2_2)  # 2.2 扩充
insert_before('2.4 大语言模型与多模型兼容接口', SEC_2_3)   # 2.3 扩充

# --- 第七章：扩充 7.1 ---
children = list(body)
insert_before('7.2 不足与展望', SEC_7_1)

# --- 第七章：重构 7.2 块 ---
children = list(body)
j_72 = find_p('7.2 不足与展望')
j_thanks = find_p('致 谢')
# 旧 7.2 块 [j_72 .. j_thanks-1]
new_72 = [make_heading('7.2 不足与展望', H3_TPL)]
new_72 += [make_body(t, BODY_TPL) for t in SEC_7_2]
for idx in range(j_thanks - 1, j_72 - 1, -1):
    body.remove(children[idx])
for off, el in enumerate(new_72):
    body.insert(j_72 + off, el)

# ========== 目录同步 ==========
children = list(body)

def make_toc(title, page, tpl=TOC_TPL):
    p = deepcopy(tpl)
    strip_paraid(p)
    ts = p.findall('.//' + NS + 't')
    ts[0].text = title     # 第一个 w:t = 标题
    ts[-1].text = str(page)  # 最后一个 w:t = 页码
    return p

# 目录中第二章小节条目：定位首条 '2.1 Java 17与Spring Boot'（目录版含页码后缀）
def find_toc(prefix):
    for i, c in enumerate(children):
        if c.tag == NS + 'p' and txt(c).startswith(prefix):
            return i
    raise ValueError('toc not found: ' + prefix)

t_21 = find_toc('2.1 Java 17与Spring Boot')      # 目录第一条 2.1
t_25 = find_toc('2.5 HTML5音频与语音合成技术')     # 目录最后一条旧 2.5
t_ch3 = find_toc('第三章 系统需求分析')           # 目录第三章

new_toc = [
    make_toc('2.1 Java 17与Spring Boot', 12),
    make_toc('2.2 Spring Security与JWT', 13),
    make_toc('2.3 MyBatis-Plus、MyBatis与MySQL', 14),
    make_toc('2.4 大语言模型与多模型兼容接口', 15),
    make_toc('2.5 模块化单体与ArchUnit架构治理', 16),
    make_toc('2.6 前端ES Modules与模块化组织', 17),
    make_toc('2.7 HTML5音频与语音合成技术', 17),
    make_toc('2.8 缓存与异步任务执行机制', 18),
]
# 移除旧目录条目 [t_21 .. t_25]
for idx in range(t_25, t_21 - 1, -1):
    body.remove(children[idx])
for off, el in enumerate(new_toc):
    body.insert(t_21 + off, el)

# 更新目录第三章页码（估算）
children = list(body)
t_ch3 = find_toc('第三章 系统需求分析')
ts = children[t_ch3].findall('.//' + NS + 't')
ts[-1].text = '19'

# ========== 写回 docx ==========
new_xml = ET.tostring(root, xml_declaration=True, encoding='UTF-8', standalone=True)
# 重写 zip 中 word/document.xml，保留其余条目
tmp = PATH + '.tmp'
with zipfile.ZipFile(PATH, 'r') as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'word/document.xml':
            data = new_xml
        zout.writestr(item, data)
shutil.move(tmp, PATH)
print('EDIT DONE')
# 验证段落总数
z2 = zipfile.ZipFile(PATH)
r2 = ET.fromstring(z2.read('word/document.xml'))
print('total paras now', len(list(r2.iter(NS + 'p'))))
