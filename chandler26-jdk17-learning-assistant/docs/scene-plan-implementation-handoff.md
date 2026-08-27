# Learning Assistant 交接记录

项目：
- 后端：D:\repository\github\demo-2026\chandler26-jdk17-learning-assistant
- 前端：D:\repository\github\demo-2026\chandler26-jdk17-learning-assistant-web

用户需求：
1. 计划首页和日历按需、按周/月加载轻量摘要。
2. 点击开始学习/回顾后，按 planId + unitId 加载完整文章、核心词、词卡、学习状态和 Markdown 笔记。
3. scene-plan.js 按职责拆分：
   - plan-manager.js
   - calendar-view.js
   - unit-list.js
   - study-engine.js
   - async-listener.js
4. Controller 使用注解 + AOP 输出接口 info 日志，包含业务接口名、HTTP 方法、路径、用户 ID、traceId、耗时、成功/失败、errorCode、分页元数据。
5. 开始学习接口不能返回完整计划详情，也不能调用 AI。
6. 性能基线：
   - 公共词本 10 个，每个 5000～10000 词。
   - 10 个用户，每人 5～10 个个人词本。
   - 同步接口目标 <= 400ms。
   - 页面首屏渲染和数据加载 <= 1s。
   - AI 异步任务最多 5 个并发。

已落盘的后端改动：
1. pom.xml
   - 添加 spring-boot-starter-aop。

2. 新增：
   - src/main/java/com/chandler/learning/agent/config/ApiAccessLog.java
   - src/main/java/com/chandler/learning/agent/config/ApiAccessLogAspect.java

   ApiAccessLogAspect 通过 @within(RestController) 记录 Controller 访问摘要：
   - operation
   - HTTP method
   - path
   - userId
   - traceId
   - costMs
   - success
   - errorCode
   - collection returned 数量或分页 getter 元数据

   不记录请求体、API Key、Prompt、完整 AI response。

3. LearningPlanResponseAssembler.java
   - 计划详情注释改为摘要优先。
   - 新增 toUnitSummaryResponses(List<LearningPlanUnit>)。
   - 摘要响应只包含：
     id、planId、unitNo、title、scenarioType、summary、status、
     各类词数、completedCoreCount、recommendedDate、sceneMaterialId、
     generatedTime、completedTime。
   - 摘要不会返回 learningText、translation、material、words、relatedWords 大字段。

4. LearningPlanService.java
   - detail(userId, planId) 改为 responseAssembler.toPlanResponse(plan, false)。
   - 新增：
     public LearningPlanUnitResponse unitDetail(Long userId, Long planId, Long unitId)
   - calendar() 的每一天现在返回 toUnitSummaryResponses(dateUnits)。
   - startUnit() 返回轻量计划响应，不再重新加载完整计划详情。
   - completeUnit() 返回轻量计划响应，不再重新加载完整计划详情。

5. LearningPlanController.java
   - 新增接口：
     GET /api/v1/learning/plans/{planId}/units/{unitId}
   - 该接口调用 learningPlanService.unitDetail(userId, planId, unitId)。
   - LearningPlanController 增加 @ApiAccessLog("场景词汇学习计划")。

已落盘的前端改动：
1. scene-plan/api.js
   - 新增：
     getUnit(planId, unitId)
     -> GET /api/v1/learning/plans/{planId}/units/{unitId}

2. 新增模块：
   - public/src/features/learning/scene-plan/plan-manager.js
   - public/src/features/learning/scene-plan/calendar-view.js
   - public/src/features/learning/scene-plan/unit-list.js
   - public/src/features/learning/scene-plan/study-engine.js
   - public/src/features/learning/scene-plan/async-listener.js

3. scene-plan.js 已经部分接入：
   - 导入上述新模块。
   - 创建 unitList、planManager、calendarView、studyEngine、asyncListener。
   - activeUnit() 改为委托 unitList.activeUnit()。
   - selectPlan() 不再自动加载完整场景笔记。
   - loadCalendarData() 获取日历后调用 unitList.mergeCalendarUnits()。
   - 点击场景卡片开始学习时：
     a. 调用 startUnit() 做权限和状态切换；
     b. 保留已有 units 摘要；
     c. 调用 unitList.loadDetail(plan, unitId) 加载完整单元；
     d. 再加载 Markdown 笔记；
     e. 进入学习阶段。

未完成/需要优先检查：
1. scene-plan.js 的 startLearning/showChallengeWords/startChallenge 仍保留原实现。
   新的 study-engine.js 已创建并实例化，但 facade 尚未完全委托给它。
   需要决定：
   - 保留旧函数作为兼容包装器，调用 studyEngine；
   - 或删除旧逻辑，统一通过 studyEngine。
   注意点击场景时目前已经先调用 unitList.loadDetail()，因此 startLearning() 应确保只负责进入学习界面和渲染，不重复请求。

2. plan-manager.js 的 targetPlan() 使用 state.learningPlans.find()，
   建议改成 asArray(state.learningPlans)，避免状态未初始化时异常。

3. async-listener.js 监听：
   window event: learning:ai-task-updated
   当前任务中心可能还没有 dispatch 该事件。
   需要在 features/task/task-center.js 任务刷新或状态更新成功后补：
   window.dispatchEvent(new CustomEvent('learning:ai-task-updated', { detail: task }))
   只携带任务摘要和 planId，不携带敏感内容。

4. ApiAccessLogAspect.java 需要编译检查：
   - @within(RestController) 是否能覆盖当前 Controller 代理。
   - operationName() 对 CGLIB 代理类的注解获取是否正常。
   - 分页 DTO 的 getter 是否与现有字段匹配。
   - 异常日志只能记录 errorCode 和异常类型，不记录 prompt/response/API key。
   - 如果项目已有统一异常处理器，确认 LearningAssistantException 的 errorCode 能被切面读取。

5. 当前还没有完成：
   - 全量 Controller 分页治理。
   - 词本词条、待复习词、文章历史、AI 会话、系统日志、异步任务、公共词本明细的性能核查。
   - N+1 SQL 检查和复合索引核查。
   - 5 并发 AI 任务的真实压测。
   - 首页首屏网络请求 <= 1s 的浏览器验证。

必须执行的验证：
后端项目：
mvn -q -DskipTests compile
mvn -q test

前端项目：
node --check public/src/features/learning/scene-plan/api.js
node --check public/src/features/learning/scene-plan/plan-manager.js
node --check public/src/features/learning/scene-plan/calendar-view.js
node --check public/src/features/learning/scene-plan/unit-list.js
node --check public/src/features/learning/scene-plan/study-engine.js
node --check public/src/features/learning/scene-plan/async-listener.js
node --check public/src/features/learning/scene-plan/scene-plan.js
npm run check
npm test -- --run

最后：
git diff --check

继续工作前必须先执行：
git status --short
git diff --stat
git diff -- <相关文件>

不要 reset --hard，不要覆盖其他用户改动。