// ============================================================================
// 箱箱呼叫中心双端高保真原型自动化回归测试套件 (Frontend E2E & Smoke Test Runner)
// 覆盖：PC 坐席工作台 (钱丁君主角) + 运营管理后台 (IVR Flow Studio + 通信资源中台)
// ============================================================================

const fs = require("fs");
const path = require("path");
const assert = require("assert");

const CLIENT_HTML_PATH = path.resolve(__dirname, "../../chandler26-fcc-client-web/index.html");
const ADMIN_HTML_PATH = path.resolve(__dirname, "../../chandler26-fcc-admin-web/index.html");

let passedCount = 0;
let failedCount = 0;

function runTest(testName, fn) {
  try {
    fn();
    console.log("  \x1b[32m✔ PASS:\x1b[0m " + testName);
    passedCount++;
  } catch (err) {
    console.error("  \x1b[31m✖ FAIL:\x1b[0m " + testName);
    console.error("    \x1b[33m" + err.message + "\x1b[0m");
    failedCount++;
  }
}

console.log("\n====================================================================");
console.log("🚀 [FCC Test Runner] 箱箱呼叫中心全链路前端自动化测试工程开始执行");
console.log("====================================================================\n");

// ----------------------------------------------------------------------------
// 测试套件 1: PC 坐席工作台 (chandler26-fcc-client-web)
// ----------------------------------------------------------------------------
console.log("📦 [Suite 1] PC 坐席工作台 (chandler26-fcc-client-web) 核心功能测试:");
const clientContent = fs.readFileSync(CLIENT_HTML_PATH, "utf8");

runTest("TC-01: 校验当前登录主角坐席为「钱丁君」及其大头像与班长工号", () => {
  assert(clientContent.includes("钱丁君"), "必须包含主角姓名: 钱丁君");
  assert(clientContent.includes("工号 901001"), "必须包含钱丁君工号: 901001");
  assert(clientContent.includes("保险投保组"), "必须包含钱丁君所属组: 保险投保组");
  assert(clientContent.includes("班长席"), "必须包含班长席职位说明");
  const avatarMatch = clientContent.match(/<div class="[^"]*rounded-2xl[^"]*text-xl[^"]*">[\s\S]*?钱[\s\S]*?<\/div>/);
  assert(avatarMatch, "坐席大头像必须展示「钱」字");
});

runTest("TC-02: 校验接听方式三模热切换器 (WebRTC / 硬件话机 / 随行手机)", () => {
  assert(clientContent.includes("changeAnswerType("), "必须存在接听方式切换控制函数 changeAnswerType");
  assert(clientContent.includes("WebRTC 网页耳麦 (1007)"), "必须包含 WebRTC 耳麦选项");
  assert(clientContent.includes("SIP 硬件话机 (1007)"), "必须包含 SIP 硬件话机选项");
  assert(clientContent.includes("随行手机接听"), "必须包含随行手机选项");
});

runTest("TC-03: 校验 24 小时防撞单智能雷达检测逻辑与前序录音就地试听", () => {
  assert(clientContent.includes("checkPhoneAntiCollision("), "必须存在防撞单实时校验函数 checkPhoneAntiCollision");
  assert(clientContent.includes("19166340294"), "必须包含冲突前序号码 19166340294");
  assert(clientContent.includes("陈松 (深圳港)"), "必须关联前序拨打坐席陈松");
  assert(clientContent.includes("toggleRadarAudio("), "必须支持就地试听前序通话录音 toggleRadarAudio");
});

runTest("TC-04: 校验通话卡片 VoIP 电信级网络质量 HUD 与满意度智能邀评", () => {
  assert(clientContent.includes("MOS 4.3 优"), "必须包含 MOS 网络质量指标");
  assert(clientContent.includes("22ms"), "必须包含 RTT 22ms 延迟指标");
  assert(clientContent.includes("0.0%"), "必须包含丢包率 0.0% 指标");
  assert(clientContent.includes("PCMA"), "必须包含语音编码格式 PCMA");
  assert(clientContent.includes("inviteCustomerSurvey("), "必须包含一键邀评函数 inviteCustomerSurvey");
});

runTest("TC-05: 校验挂机后话后处理 (ACW) 抽屉与 DDL 挂机责任方/满意度归因", () => {
  assert(clientContent.includes("callSummaryDrawer"), "必须包含 ACW 小结抽屉容器");
  assert(clientContent.includes("acwCountdown"), "必须包含话后倒计时计数器");
  assert(clientContent.includes("summaryHangupInitiator"), "必须包含挂机责任方选择器");
  assert(clientContent.includes("summaryEvaluation"), "必须包含客户满意度评分选择器 summaryEvaluation");
  assert(clientContent.includes("submitCallSummary("), "必须包含小结保存落库函数 submitCallSummary");
});

runTest("TC-06: 校验 F2 坐席监控大屏与班长实时话务干预 4 大指令 (监听/耳语/强插/强拆)", () => {
  assert(clientContent.includes("agentMonitorModal"), "必须包含 F2 坐席监控大屏容器");
  assert(clientContent.includes("(当前本人 / 班长主管)"), "坐席列表中钱丁君必须被标注为当前本人班长主管");
  assert(clientContent.includes("startSupervisorIntervention("), "必须包含组长发起干预函数");
  assert(clientContent.includes("SPY") && clientContent.includes("🎧 监听"), "必须支持静默监听干预");
  assert(clientContent.includes("COACH") && clientContent.includes("🗣️ 耳语"), "必须支持单向耳语干预");
  assert(clientContent.includes("BARGE") && clientContent.includes("👥 强插"), "必须支持三方强插干预");
  assert(clientContent.includes("forceKillAgentCall(") && clientContent.includes("✂️ 强拆"), "必须支持强制拆线干预");
  assert(clientContent.includes("memberCallLogsDrawer"), "必须包含组员通话流水侧滑抽屉");
});

// ----------------------------------------------------------------------------
// 测试套件 2: 运营管理与调度中台 (chandler26-fcc-admin-web)
// ----------------------------------------------------------------------------
console.log("\n📊 [Suite 2] 运营管理与调度中台 (chandler26-fcc-admin-web) 核心功能测试:");
const adminContent = fs.readFileSync(ADMIN_HTML_PATH, "utf8");

runTest("TC-07: 校验 IVR 流程可视化编排画布 (Flow Studio) 5 节点连线与版本管理", () => {
  assert(adminContent.includes("module-flows"), "必须包含 IVR 流程编排主模块");
  assert(adminContent.includes("nav-btn-flows"), "导航栏必须包含 IVR 流程编排按钮");
  assert(adminContent.includes("呼入事件触发 (TRIGGER)"), "必须包含入口触发器节点");
  assert(adminContent.includes("放音欢迎语 (ACTION_PLAY)"), "必须包含欢迎放音节点");
  assert(adminContent.includes("按键收号与分流 (ACTION_GET_DIGITS)"), "必须包含按键采集节点");
  assert(adminContent.includes("技能组排队 (ROUTE_GROUP)"), "必须包含技能组排队路由节点");
  assert(adminContent.includes("排队超时溢出 (OVERFLOW_TASK)"), "必须包含溢出兜底任务节点");
});

runTest("TC-08: 校验节点参数抽屉 (openNodeConfig) 支持全部 5 类节点动态表单与试听", () => {
  assert(adminContent.includes("flowNodeConfigDrawer"), "必须包含节点参数配置抽屉容器");
  assert(adminContent.includes("openNodeConfig("), "必须包含打开节点配置抽屉函数");
  assert(adminContent.includes("welcome_boxbox_v2.wav"), "放音节点配置必须包含音频文件试听");
  assert(adminContent.includes("最长空闲坐席优先 (LONGEST_IDLE)"), "排队节点必须支持 LONGEST_IDLE 调度策略");
  assert(adminContent.includes("fcc_callback_task"), "溢出节点必须对齐落库 fcc_callback_task");
});

runTest("TC-09: 校验 IVR 沙箱测试模拟器 (openFlowSimulator) 单步推演与 Trace 日志", () => {
  assert(adminContent.includes("flowSimulatorModal"), "必须包含沙箱模拟器弹窗");
  assert(adminContent.includes("startSimulatorCall("), "必须包含发起模拟呼叫函数");
  assert(adminContent.includes("pressSimDigit("), "必须包含 DTMF 虚拟按键发送函数");
  assert(adminContent.includes("simLogContainer"), "必须包含 FreeSWITCH 实时日志输出控制台");
});

runTest("TC-10: 校验通信资源中台三大子标签 (分机与终端/SIP中继网关/外呼防封号码池)", () => {
  assert(adminContent.includes("switchTelephonySubTab("), "必须包含通信资源子标签切换函数");
  assert(adminContent.includes("tele-view-ext"), "必须包含分机终端视图");
  assert(adminContent.includes("tele-view-trunk"), "必须包含 SIP 中继网关视图");
  assert(adminContent.includes("tele-view-pool"), "必须包含外呼防封号码池视图");
});

runTest("TC-11: 校验 FreeSWITCH 集群健康与平滑排水模式 (Draining Mode) 切换", () => {
  assert(adminContent.includes("clusterHealthModal"), "必须包含集群健康模态弹窗");
  assert(adminContent.includes("toggleNodeDrain("), "必须包含平滑排水状态切换函数");
  assert(adminContent.includes("DRAINING"), "必须对齐 DDL DRAINING 状态值");
  assert(adminContent.includes("fs-node-sh-01") && adminContent.includes("fs-node-sh-02"), "必须覆盖双机热备节点");
});

runTest("TC-12: 校验全网人名纯洁性白名单 (9 人白名单，0 违规废弃人名)", () => {
  const forbiddenNames = ["鸭嘴兽", "鲁玉", "刘淇", "苏丹", "邵莉莉", "公维亮", "门现虎", "弓恒飞", "刘梦思", "彭佳庆", "艾四苹", "邹林林", "朱娇娟", "胡训芳"];
  
  forbiddenNames.forEach(name => {
    assert(!clientContent.includes(name), `PC 客户端严禁包含非法旧人名: ${name}`);
    assert(!adminContent.includes(name), `运营管理后台严禁包含非法旧人名: ${name}`);
  });
});

console.log("\n====================================================================");
console.log(`🎉 [FCC Test Runner] 执行完毕! 共执行: ${passedCount + failedCount} 项, 成功: \x1b[32m${passedCount}\x1b[0m, 失败: \x1b[31m${failedCount}\x1b[0m`);
console.log("====================================================================\n");

if (failedCount > 0) {
  process.exit(1);
} else {
  process.exit(0);
}
