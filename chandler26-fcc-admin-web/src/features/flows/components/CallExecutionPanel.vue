<script setup lang="ts">
import { ref, computed, toRef, watch } from 'vue';
import { useCallExecution } from '../composables/useCallExecution';
import { executionLabels, stageLabels, type StageExecution } from '../model/stagedFlow';
import type { CallRecord } from '../../cdr/model/callRecord';
import FlowCanvas from './FlowCanvas.vue';
import {
  PhoneIncoming,
  Volume2,
  Users,
  PhoneCall,
  Award,
  Copy,
  Check,
  Clock,
  Sliders,
  ChevronRight,
  ShieldCheck,
  CheckCircle2,
  PhoneForwarded,
} from 'lucide-vue-next';

const props = defineProps<{
  callId: string;
  cdr?: CallRecord | null;
}>();

defineEmits<{
  'play-audio': [];
}>();

const { flow, executions, error, loading, cursor, status, version, load } = useCallExecution(
  toRef(props, 'callId'),
);

// 视图模式: 'journey' (业务旅程时间线, 推荐) | 'topology' (底层流程模型拓扑)
const viewMode = ref<'journey' | 'topology'>('journey');

// 当前选中的阶段
const selected = ref<string>('');

// 复制状态
const copiedKey = ref<string>('');
const copyToClipboard = async (text: string, key: string) => {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    copiedKey.value = key;
    setTimeout(() => {
      if (copiedKey.value === key) copiedKey.value = '';
    }, 2000);
  } catch (e) {
    console.error('Copy failed', e);
  }
};

// 提取所有执行步骤与通话上下文中的权威运行时事实 (JSON facts)
const allFacts = computed(() => {
  const facts: Record<string, any> = {};
  for (const step of executions.value) {
    if (step.output) {
      try {
        Object.assign(facts, JSON.parse(step.output));
      } catch {}
    }
    if (step.input) {
      try {
        Object.assign(facts, JSON.parse(step.input));
      } catch {}
    }
  }
  return facts;
});

// 核心：精准识别当前通话绑定的流程模型类型 (不可被 INBOUND 假定覆盖)
const isOutbound = computed(() => {
  const dir = String(props.cdr?.direction || '').toUpperCase();
  if (dir === 'OUTBOUND') return true;
  const idStr = String(props.cdr?.id || props.callId || '');
  if (idStr.startsWith('outbound-')) return true;
  const mType = String(props.cdr?.modelType || '').toUpperCase();
  if (mType.includes('OUTBOUND')) return true;
  const tpl = String((flow.value as any)?.template || allFacts.value.runtimeTemplate || '').toUpperCase();
  if (tpl === 'AGENT_FIRST' || tpl === 'AGENT_ORIGINATED' || tpl.includes('OUTBOUND')) return true;
  const ver = String(version.value || '').toUpperCase();
  if (ver.includes('AGENT_FIRST') || ver.includes('OUTBOUND')) return true;
  if (executions.value.some((e) => e.stepKey === 'DIAL_AGENT' || e.stepKey === 'DIAL_CUSTOMER' || e.actionType === 'DIAL_AGENT' || e.actionType === 'DIAL_CUSTOMER')) {
    return true;
  }
  return false;
});

const currentTemplate = computed<'INBOUND' | 'PHONE_BINDING' | 'NOTIFICATION' | 'AGENT_FIRST' | 'AGENT_ORIGINATED' | string>(() => {
  if ((flow.value as any)?.template === 'PHONE_BINDING' || allFacts.value.runtimeTemplate === 'PHONE_BINDING' || props.cdr?.modelType === 'PHONE_BINDING') {
    return 'PHONE_BINDING';
  }
  if (isOutbound.value) {
    if ((flow.value as any)?.template === 'AGENT_ORIGINATED' || allFacts.value.runtimeTemplate === 'AGENT_ORIGINATED') {
      return 'AGENT_ORIGINATED';
    }
    return (flow.value as any)?.template || allFacts.value.runtimeTemplate || 'AGENT_FIRST';
  }
  if ((flow.value as any)?.template) return (flow.value as any).template;
  if (allFacts.value.runtimeTemplate) return allFacts.value.runtimeTemplate;
  if (props.cdr?.modelType) {
    if (props.cdr.modelType === 'AUTO_DIAL_NOTIFICATION') return 'NOTIFICATION';
    return props.cdr.modelType;
  }
  return 'INBOUND';
});

// ==================== 1. 话机绑定模型 (PHONE_BINDING) 事实提取 ====================
const bindingWorkNo = computed(() => {
  if (allFacts.value.workNo) return String(allFacts.value.workNo);
  if (allFacts.value.dtmf) return String(allFacts.value.dtmf);
  if (allFacts.value.bindingWorkNo) return String(allFacts.value.bindingWorkNo);
  const branchStr = String(allFacts.value.flowBranch || '');
  if (branchStr.startsWith('workNo=')) {
    return branchStr.replace('workNo=', '');
  }
  if (props.cdr?.agentWorkNo && props.cdr.agentWorkNo !== '-') {
    return props.cdr.agentWorkNo;
  }
  return '';
});

const bindingExtension = computed(() => {
  return (
    allFacts.value.bindingExtension ||
    props.cdr?.callerPhone ||
    props.cdr?.callee ||
    '1007'
  );
});

const bindingAgentName = computed(() => {
  return (
    allFacts.value.agentName ||
    props.cdr?.agentName ||
    (bindingWorkNo.value ? `坐席 ${bindingWorkNo.value}` : '坐席')
  );
});

const bindingPromptText = computed(() => {
  return (
    (flow.value as any)?.parameters?.promptText ||
    allFacts.value.bindingPromptText ||
    '请输入您的工号完成话机绑定，以井号键结束。'
  );
});

const bindingSuccessText = computed(() => {
  return (
    (flow.value as any)?.parameters?.successText ||
    allFacts.value.bindingSuccessText ||
    '话机绑定成功，再见。'
  );
});

const isBindingAccepted = computed(() => {
  if (allFacts.value.bindingAccepted !== undefined) {
    return Boolean(allFacts.value.bindingAccepted);
  }
  return props.cdr?.status === 'ANSWERED' || status.value === 'ENDED';
});

// ==================== 2. 普通呼入进线 (INBOUND) 事实提取 ====================
const effectiveBranch = computed(() => {
  // 1. 优先从决策阶段 (BRANCH, MENU) 获取真实权威分支
  const branchExec = executions.value.find((e) => e.stepKey === 'BRANCH');
  if (branchExec?.output) {
    try {
      const b = JSON.parse(branchExec.output).flowBranch;
      if (b && String(b).startsWith('digit=')) return String(b);
    } catch {}
  }
  const menuExec = executions.value.find((e) => e.stepKey === 'MENU');
  if (menuExec?.output) {
    try {
      const b = JSON.parse(menuExec.output).flowBranch;
      if (b && String(b).startsWith('digit=')) return String(b);
    } catch {}
  }

  // 2. 检查是否有任何阶段曾记录过 digit= 按键事实 (防止后续步骤被覆盖)
  for (const step of executions.value) {
    for (const raw of [step.output, step.input]) {
      if (!raw) continue;
      try {
        const obj = JSON.parse(raw);
        if (obj.flowBranch && String(obj.flowBranch).startsWith('digit=')) {
          return String(obj.flowBranch);
        }
      } catch {}
    }
  }

  // 3. 检查是否有明确的超时或无按键
  for (const step of executions.value) {
    for (const raw of [step.output, step.input]) {
      if (!raw) continue;
      try {
        const obj = JSON.parse(raw);
        if (obj.flowBranch === 'menu.timeout' || obj.flowBranch === 'menu.no-digit') {
          return String(obj.flowBranch);
        }
      } catch {}
    }
  }

  // 4. 兜底全局 allFacts.flowBranch
  return String(allFacts.value.flowBranch || '');
});

const digitInfo = computed(() => {
  const branchStr = effectiveBranch.value;
  if (branchStr.startsWith('digit=')) {
    const d = branchStr.replace('digit=', '').trim();
    return { status: 'PRESSED' as const, digit: d, label: `按键 [ ${d} ]` };
  }
  if (branchStr === 'menu.timeout') {
    return { status: 'TIMEOUT' as const, digit: '', label: '未按键 · 超时' };
  }
  if (branchStr === 'menu.no-digit') {
    return { status: 'NO_DIGIT' as const, digit: '', label: '未按有效按键' };
  }
  if (branchStr === 'menu.enabled=false' || flow.value?.menu?.enabled === false) {
    return { status: 'SKIPPED' as const, digit: '', label: '未启用菜单 · 直通' };
  }
  if (branchStr === 'else') {
    return { status: 'ELSE' as const, digit: '', label: '未匹配分支 · 默认路由' };
  }
  return { status: 'NONE' as const, digit: '', label: '未产生按键' };
});

const resolvedRoute = computed(() => {
  const d = digitInfo.value.digit;
  if (d && flow.value?.branches) {
    const matched = flow.value.branches.find((b) => b.digit === d);
    if (matched) {
      return {
        isMatch: true,
        type: matched.targetType === 'AGENT' ? '指定坐席' : '业务技能组',
        target: allFacts.value.groupCode || matched.target || '客服技能组',
        ruleDesc: `按键 [ ${d} ] 业务映射规则`,
      };
    }
  }
  const def = flow.value?.defaultRoute;
  return {
    isMatch: false,
    type: def?.targetType === 'AGENT' ? '指定坐席' : '业务技能组',
    target: allFacts.value.groupCode || def?.target || '默认技能组',
    ruleDesc: digitInfo.value.status === 'TIMEOUT' ? '超时兜底分流规则' : '默认分流路由规则',
  };
});

// 辅助检索指定阶段的执行记录
const getStepExec = (key: string) => executions.value.find((e) => e.stepKey === key);

// ==================== 3. 动态全生命周期旅程构建器 ====================
interface JourneyCardItem {
  key: string;
  stepNo: string;
  title: string;
  subTitle: string;
  statusText: string;
  badgeClass: string;
  startedAt?: string;
  durationText?: string;
  highlightType:
    | 'binding_entry'
    | 'binding_answer'
    | 'binding_collect'
    | 'binding_verify'
    | 'binding_result'
    | 'inbound_entry'
    | 'inbound_menu'
    | 'inbound_branch'
    | 'inbound_route'
    | 'inbound_connected'
    | 'inbound_end'
    | 'outbound_entry'
    | 'outbound_dial_agent'
    | 'outbound_dial_customer'
    | 'outbound_connected'
    | 'outbound_rating'
    | 'outbound_end'
    | 'generic';
  raw?: StageExecution;
}

const journeyCards = computed<JourneyCardItem[]>(() => {
  const cards: JourneyCardItem[] = [];
  const tpl = currentTemplate.value;

  // ---------------- CASE A: 坐席话机自助绑定流程 (PHONE_BINDING) ----------------
  if (tpl === 'PHONE_BINDING') {
    const eEntry = getStepExec('ENTRY');
    const eAnswer = getStepExec('ANSWER');
    const eCollect = getStepExec('COLLECT_CODE');
    const eVerify = getStepExec('VERIFY_BINDING');
    const eResult = getStepExec('RESULT');
    const eEnd = getStepExec('END');

    cards.push({
      key: 'ENTRY',
      stepNo: '01',
      title: '识别绑定话机',
      subTitle: '已认证 SIP 话机拨入专用 0000 绑定通道',
      statusText: '已验证',
      badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200',
      startedAt: eEntry?.startedAt || props.cdr?.startTime || '-',
      durationText: eEntry?.durationMs != null ? `${eEntry.durationMs} ms` : '即时',
      highlightType: 'binding_entry',
      raw: eEntry,
    });

    cards.push({
      key: 'ANSWER',
      stepNo: '02',
      title: '应答绑定话道',
      subTitle: '话务引擎自动接管并准备语音收号',
      statusText: '已应答',
      badgeClass: 'bg-blue-50 text-blue-700 border-blue-200',
      startedAt: eAnswer?.startedAt || '-',
      durationText: eAnswer?.durationMs != null ? `${eAnswer.durationMs} ms` : '完成',
      highlightType: 'binding_answer',
      raw: eAnswer,
    });

    cards.push({
      key: 'COLLECT_CODE',
      stepNo: '03',
      title: '收取坐席工号',
      subTitle: '播放引导提示音并采集用户 DTMF 工号按键',
      statusText: eCollect ? '收号完成' : '进行中',
      badgeClass: 'bg-amber-50 text-amber-700 border-amber-200',
      startedAt: eCollect?.startedAt || '-',
      durationText: eCollect?.durationMs != null ? `${(eCollect.durationMs / 1000).toFixed(1)} 秒` : '4.0 秒',
      highlightType: 'binding_collect',
      raw: eCollect,
    });

    cards.push({
      key: 'VERIFY_BINDING',
      stepNo: '04',
      title: '校验工号并原子换绑',
      subTitle: '同一数据库事务锁定分机并更新绑定关系',
      statusText: isBindingAccepted.value ? '换绑成功' : '换绑失败',
      badgeClass: isBindingAccepted.value ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-rose-50 text-rose-700 border-rose-200',
      startedAt: eVerify?.startedAt || '-',
      durationText: eVerify?.durationMs != null ? `${eVerify.durationMs} ms` : '瞬时',
      highlightType: 'binding_verify',
      raw: eVerify,
    });

    cards.push({
      key: 'RESULT',
      stepNo: '05',
      title: '播报绑定结果',
      subTitle: '向话机通道播报成功或失败语音反馈',
      statusText: '播报完成',
      badgeClass: 'bg-indigo-50 text-indigo-700 border-indigo-200',
      startedAt: eResult?.startedAt || '-',
      durationText: eResult?.durationMs != null ? `${(eResult.durationMs / 1000).toFixed(1)} 秒` : '完成',
      highlightType: 'binding_result',
      raw: eResult,
    });

    cards.push({
      key: 'END',
      stepNo: '06',
      title: '结束绑定通话',
      subTitle: '话机话道释放与会话结案',
      statusText: '已释放',
      badgeClass: 'bg-slate-100 text-slate-700 border-slate-300',
      startedAt: props.cdr?.endTime || eEnd?.endedAt || '-',
      durationText: props.cdr?.hangupCause || allFacts.value.cause || 'NORMAL_CLEARING',
      highlightType: 'inbound_end',
      raw: eEnd,
    });

    return cards;
  }

  // ---------------- CASE B: 双向外呼流程 (AGENT_FIRST / AGENT_ORIGINATED) ----------------
  if (isOutbound.value) {
    const isOriginated = currentTemplate.value === 'AGENT_ORIGINATED';
    const eEntry = getStepExec('ENTRY');
    const eDialAgent = getStepExec('DIAL_AGENT') || (isOriginated ? eEntry : undefined);
    const eDialCustomer = getStepExec('DIAL_CUSTOMER');
    const eConnected = getStepExec('CONNECTED') || getStepExec('BRIDGE');
    const eRating =
      getStepExec('RATING') ||
      getStepExec('RATING_SAVE') ||
      getStepExec('CLOSING') ||
      getStepExec('RECORD_STOP');
    const eEnd = getStepExec('END');
    const isAnswered = props.cdr?.status === 'ANSWERED' || Boolean(eConnected);
    const hasRating = Boolean(props.cdr?.satisfactionScore || allFacts.value.evaluationScore);

    cards.push({
      key: 'ENTRY',
      stepNo: '01',
      title: isOriginated ? '坐席话机直接外呼接入' : '外呼任务创建与调度',
      subTitle: isOriginated ? '话务引擎识别已认证坐席话机直呼' : '坐席工作台发起双向外呼调度',
      statusText: '已受理',
      badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200',
      startedAt: eEntry?.startedAt || props.cdr?.startTime || '-',
      durationText: eEntry?.durationMs != null ? `${eEntry.durationMs} ms` : '实时',
      highlightType: 'outbound_entry',
      raw: eEntry,
    });

    cards.push({
      key: 'DIAL_AGENT',
      stepNo: '02',
      title: isOriginated ? '坐席话机话道接管' : '呼叫业务坐席',
      subTitle: isOriginated ? 'FreeSWITCH 话务引擎应答并进入驻留池' : '话务引擎向坐席分机发起振铃与握手',
      statusText: '坐席已应答',
      badgeClass: 'bg-blue-50 text-blue-700 border-blue-200',
      startedAt: eDialAgent?.startedAt || eEntry?.startedAt || '-',
      durationText: eDialAgent?.durationMs != null ? `${eDialAgent.durationMs} ms` : '已接通',
      highlightType: 'outbound_dial_agent',
      raw: eDialAgent,
    });

    cards.push({
      key: 'DIAL_CUSTOMER',
      stepNo: '03',
      title: '呼叫目标客户',
      subTitle: '话务引擎向客户真实号码发起外呼',
      statusText: isAnswered ? '客户已接听' : (eDialCustomer ? '呼叫完成' : '呼叫中'),
      badgeClass: isAnswered ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-amber-50 text-amber-700 border-amber-200',
      startedAt: eDialCustomer?.startedAt || '-',
      durationText: props.cdr?.ringDuration || (eDialCustomer?.durationMs != null ? `${Math.round(eDialCustomer.durationMs / 1000)} 秒` : '-'),
      highlightType: 'outbound_dial_customer',
      raw: eDialCustomer,
    });

    cards.push({
      key: 'CONNECTED',
      stepNo: '04',
      title: '双向通话与全程录音',
      subTitle: '坐席与客户双方话道桥接 (Bridge) 并开启录音',
      statusText: isAnswered ? '双向已接通' : '未接通',
      badgeClass: isAnswered ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-rose-50 text-rose-700 border-rose-200',
      startedAt: eConnected?.startedAt || props.cdr?.startTime || '-',
      durationText: props.cdr?.audioDuration || (props.cdr?.talkDurationMs ? `${Math.round(props.cdr.talkDurationMs / 1000)} 秒` : '0 秒'),
      highlightType: 'outbound_connected',
      raw: eConnected,
    });

    cards.push({
      key: 'RATING',
      stepNo: '05',
      title: '坐席挂机与服务评价',
      subTitle: '坐席挂机，客户话道保留并播放满意度提示语音',
      statusText: hasRating ? `已评价 (${props.cdr?.satisfactionScore || allFacts.value.evaluationScore} 星)` : '未评价 / 挂机退出',
      badgeClass: hasRating ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-slate-50 text-slate-500 border-slate-200',
      startedAt: eRating?.startedAt || '-',
      durationText: eRating?.durationMs != null ? `${(eRating.durationMs / 1000).toFixed(1)} 秒` : '-',
      highlightType: 'outbound_rating',
      raw: eRating,
    });

    cards.push({
      key: 'END',
      stepNo: '06',
      title: '客户挂机与通话归档',
      subTitle: '信令释放，生成完整 CDR 话单与录音归档',
      statusText: '已结案',
      badgeClass: 'bg-slate-100 text-slate-700 border-slate-300',
      startedAt: props.cdr?.endTime || eEnd?.endedAt || '-',
      durationText: props.cdr?.hangupCause || allFacts.value.cause || 'NORMAL_CLEARING',
      highlightType: 'outbound_end',
      raw: eEnd,
    });

    return cards;
  }

  // ---------------- CASE C: 普通呼入 IVR 流程 (INBOUND) ----------------
  const eEntry = getStepExec('ENTRY');
  const eMenu = getStepExec('MENU');
  const eBranch = getStepExec('BRANCH');
  const eRoute = getStepExec('ROUTE') || getStepExec('DIAL_AGENT');
  const eConnected = getStepExec('CONNECTED') || getStepExec('BRIDGE');
  const eEnd = getStepExec('END') || getStepExec('RATING');

  cards.push({
    key: 'ENTRY',
    stepNo: '01',
    title: '进线接入与 DID 应答接管',
    subTitle: '专线匹配与话务引擎接管',
    statusText: eEntry ? '已接管' : '进行中',
    badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    startedAt: eEntry?.startedAt || props.cdr?.startTime || '-',
    durationText: eEntry?.durationMs != null ? `${eEntry.durationMs} ms` : '实时',
    highlightType: 'inbound_entry',
    raw: eEntry,
  });

  cards.push({
    key: 'MENU',
    stepNo: '02',
    title: '欢迎语播放与按键收号',
    subTitle: 'IVR 语音播报与 DTMF 监听',
    statusText: eMenu ? '收号完成' : '未触发',
    badgeClass: eMenu ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-slate-50 text-slate-400 border-slate-200',
    startedAt: eMenu?.startedAt || '-',
    durationText: eMenu?.durationMs != null ? `${(eMenu.durationMs / 1000).toFixed(1)} 秒` : (flow.value?.menu?.timeoutSeconds ? `上限 ${flow.value.menu.timeoutSeconds} 秒` : '-'),
    highlightType: 'inbound_menu',
    raw: eMenu,
  });

  cards.push({
    key: 'BRANCH',
    stepNo: '03',
    title: '按键决策与路由分流',
    subTitle: '单一确定性执行过程 · 无分支歧义',
    statusText: eBranch ? '已命中分流' : '未触发',
    badgeClass: eBranch ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'bg-slate-50 text-slate-400 border-slate-200',
    startedAt: eBranch?.startedAt || '-',
    durationText: eBranch?.durationMs != null ? `${eBranch.durationMs} ms` : '瞬时',
    highlightType: 'inbound_branch',
    raw: eBranch,
  });

  cards.push({
    key: 'ROUTE',
    stepNo: '04',
    title: '技能组排队与坐席分配',
    subTitle: '话务路由与坐席分机振铃',
    statusText: eRoute ? '已分发' : '未触发',
    badgeClass: eRoute ? 'bg-cyan-50 text-cyan-700 border-cyan-200' : 'bg-slate-50 text-slate-400 border-slate-200',
    startedAt: eRoute?.startedAt || '-',
    durationText: props.cdr?.ringDuration || (eRoute?.durationMs != null ? `${Math.round(eRoute.durationMs / 1000)} 秒` : '-'),
    highlightType: 'inbound_route',
    raw: eRoute,
  });

  const isAnswered = props.cdr?.status === 'ANSWERED';
  cards.push({
    key: 'CONNECTED',
    stepNo: '05',
    title: '双向通话与全程录音',
    subTitle: '话道桥接与高保真录音存档',
    statusText: isAnswered ? '双向已接通' : '未接通',
    badgeClass: isAnswered ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-rose-50 text-rose-700 border-rose-200',
    startedAt: eConnected?.startedAt || props.cdr?.startTime || '-',
    durationText: props.cdr?.audioDuration || (props.cdr?.talkDurationMs ? `${Math.round(props.cdr.talkDurationMs / 1000)} 秒` : '0 秒'),
    highlightType: 'inbound_connected',
    raw: eConnected,
  });

  cards.push({
    key: 'END',
    stepNo: '06',
    title: '通话挂机与服务评价',
    subTitle: '信令释放与满意度归档',
    statusText: '已结案',
    badgeClass: 'bg-slate-100 text-slate-700 border-slate-300',
    startedAt: props.cdr?.endTime || eEnd?.endedAt || '-',
    durationText: props.cdr?.hangupCause || allFacts.value.cause || 'NORMAL_CLEARING',
    highlightType: 'inbound_end',
    raw: eEnd,
  });

  return cards;
});

// 默认选中第一个或最核心阶段
watch(
  [journeyCards, isOutbound, currentTemplate],
  ([cards, outbound, tpl]) => {
    if (!cards.length) return;
    if (!selected.value || !cards.some((c) => c.key === selected.value)) {
      if (tpl === 'PHONE_BINDING') {
        selected.value = 'COLLECT_CODE';
      } else if (outbound) {
        selected.value = 'CONNECTED';
      } else {
        selected.value = 'MENU';
      }
    }
  },
  { immediate: true },
);

// 当前选中的阶段尝试详情
const selectedAttempts = computed(() => {
  return executions.value.filter((e) => e.stepKey === selected.value);
});

const currentSelectedCard = computed(() => {
  return journeyCards.value.find((c) => c.key === selected.value);
});
</script>

<template>
  <div class="call-execution-panel flex flex-col bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-xs">
    <!-- 顶部状态栏与模式切换 -->
    <header class="px-5 py-3 bg-slate-50/90 border-b border-slate-200 flex items-center justify-between gap-4 flex-wrap shrink-0">
      <div class="flex items-center gap-2.5">
        <div class="w-2.5 h-2.5 rounded-full" :class="status === 'ENDED' ? 'bg-emerald-500 ring-2 ring-emerald-100' : 'bg-blue-500 animate-pulse'" />
        <span class="font-extrabold text-slate-900 text-sm">
          {{ version || (currentTemplate === 'PHONE_BINDING' ? 'SYSTEM_PHONE_BINDING · v1 (坐席工号绑定)' : (isOutbound ? `OUTBOUND_TWO_WAY_CALL · v1 (${currentTemplate === 'AGENT_ORIGINATED' ? '坐席话机直接外呼' : '坐席优先双呼外呼'})` : (flow?.template || '通话流程'))) }}
        </span>
        <span class="text-xs px-2.5 py-0.5 rounded-full font-bold font-mono border"
          :class="cdr?.status === 'ANSWERED' || isBindingAccepted ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-slate-100 text-slate-600 border-slate-200'">
          {{ currentTemplate === 'PHONE_BINDING' ? (isBindingAccepted ? '绑定完成' : '已结束') : (cdr?.status === 'ANSWERED' ? '双向已接通' : '通话已结束') }}
        </span>
      </div>

      <div class="flex items-center gap-2">
        <!-- 视图切换: 业务旅程 vs 技术拓扑 -->
        <div class="bg-slate-200/70 p-0.5 rounded-lg flex items-center text-xs font-bold text-slate-600">
          <button
            type="button"
            class="px-3 py-1 rounded-md transition-all cursor-pointer flex items-center gap-1.5"
            :class="viewMode === 'journey' ? 'bg-white text-indigo-700 shadow-2xs' : 'hover:text-slate-900'"
            @click="viewMode = 'journey'"
          >
            <Clock class="w-3.5 h-3.5" />
            <span>生命周期旅程 (推荐)</span>
          </button>
          <button
            type="button"
            class="px-3 py-1 rounded-md transition-all cursor-pointer flex items-center gap-1.5"
            :class="viewMode === 'topology' ? 'bg-white text-indigo-700 shadow-2xs' : 'hover:text-slate-900'"
            @click="viewMode = 'topology'"
          >
            <Sliders class="w-3.5 h-3.5" />
            <span>底层模型拓扑</span>
          </button>
        </div>

        <el-button size="small" :loading="loading" @click="load()">刷新事实</el-button>
      </div>
    </header>

    <!-- ==================== 🌟 管理员高亮速览横幅 (按流程模型动态呈现) ==================== -->

    <!-- MODEL 1: 坐席话机自助绑定专属横幅 (PHONE_BINDING) -->
    <div
      v-if="currentTemplate === 'PHONE_BINDING'"
      class="bg-gradient-to-r from-emerald-50/70 via-slate-50/80 to-indigo-50/60 px-5 py-3 border-b border-emerald-100 grid grid-cols-2 md:grid-cols-5 gap-3 shrink-0 text-xs"
    >
      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <PhoneIncoming class="w-3 h-3 text-emerald-600" />
          <span>绑定入口 / 话道</span>
        </span>
        <div class="font-mono font-black text-slate-900 text-sm">
          0000 <span class="text-xs font-normal text-slate-400">(话机自助绑定)</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <ShieldCheck class="w-3 h-3 text-indigo-600" />
          <span>发起分机 (SIP)</span>
        </span>
        <div class="font-mono font-black text-indigo-700 text-sm">
          {{ bindingExtension }}
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Volume2 class="w-3 h-3 text-amber-600" />
          <span>实际按键 / 输入工号</span>
        </span>
        <div>
          <span v-if="bindingWorkNo" class="px-2.5 py-0.5 bg-emerald-600 text-white font-mono font-black text-xs rounded-md shadow-2xs">
            工号 [ {{ bindingWorkNo }}# ]
          </span>
          <span v-else class="text-slate-400 font-mono text-xs">
            未收到输入
          </span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Users class="w-3 h-3 text-cyan-600" />
          <span>换绑坐席事实</span>
        </span>
        <div class="font-bold text-slate-900 truncate">
          {{ bindingAgentName }}
          <span class="text-slate-400 font-mono text-[11px] font-normal" v-if="bindingWorkNo">({{ bindingWorkNo }})</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <CheckCircle2 class="w-3 h-3 text-emerald-600" />
          <span>换绑事务结果</span>
        </span>
        <div>
          <span v-if="isBindingAccepted" class="text-emerald-700 font-black font-mono flex items-center gap-1">
            <span>✓ 话机换绑成功</span>
          </span>
          <span v-else class="text-rose-600 font-black font-mono">
            ✗ 绑定失败 / 异常
          </span>
        </div>
      </div>
    </div>

    <!-- MODEL 2: 双向外呼专属横幅 (OUTBOUND) -->
    <div
      v-else-if="isOutbound"
      class="bg-gradient-to-r from-blue-50/70 via-indigo-50/60 to-emerald-50/50 px-5 py-3 border-b border-blue-100 grid grid-cols-2 md:grid-cols-5 gap-3 shrink-0 text-xs"
    >
      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <PhoneForwarded class="w-3 h-3 text-blue-600" />
          <span>外呼模式 / 渠道</span>
        </span>
        <div class="font-mono font-black text-blue-900 text-sm">
          {{ currentTemplate === 'AGENT_ORIGINATED' ? '话机直接外呼' : '坐席优先双呼' }}
          <span class="text-xs font-normal text-slate-400">({{ currentTemplate === 'AGENT_ORIGINATED' ? '话机发起' : '工作台发起' }})</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Users class="w-3 h-3 text-indigo-600" />
          <span>主叫坐席 / 分机</span>
        </span>
        <div class="font-bold text-slate-900 truncate">
          {{ cdr?.agentName || '业务坐席' }}
          <span class="text-indigo-700 font-mono text-[11px] font-bold">({{ allFacts.agentExt || cdr?.agentWorkNo || cdr?.callerPhone || '1007' }})</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <PhoneCall class="w-3 h-3 text-purple-600" />
          <span>目标客户号码</span>
        </span>
        <div class="font-mono font-black text-purple-900 text-sm">
          {{ cdr?.callee || allFacts.guestNumber || '-' }}
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Clock class="w-3 h-3 text-emerald-600" />
          <span>振铃 / 通话时长</span>
        </span>
        <div class="font-mono text-slate-900 font-bold">
          <span>{{ cdr?.ringDuration || '0秒' }}</span>
          <span class="text-slate-300 mx-1">/</span>
          <span class="text-emerald-700 font-black">{{ cdr?.audioDuration || '0秒' }}</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Award class="w-3 h-3 text-amber-500" />
          <span>服务满意度评价</span>
        </span>
        <div class="font-bold">
          <span v-if="cdr?.satisfactionScore || allFacts.evaluationScore" class="text-amber-600 font-black font-mono">
            ★ {{ cdr?.satisfactionScore || allFacts.evaluationScore }} 星好评
          </span>
          <span v-else class="text-slate-400 text-xs">
            未评价 / 挂机退出
          </span>
        </div>
      </div>
    </div>

    <!-- MODEL 3: 普通呼入 IVR 专属横幅 (INBOUND) -->
    <div
      v-else
      class="bg-gradient-to-r from-indigo-50/60 via-slate-50/80 to-blue-50/50 px-5 py-3 border-b border-indigo-100/70 grid grid-cols-2 md:grid-cols-5 gap-3 shrink-0 text-xs"
    >
      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <PhoneIncoming class="w-3 h-3 text-indigo-500" />
          <span>接入 DID 专线</span>
        </span>
        <div class="font-mono font-black text-indigo-700 text-sm tracking-tight truncate" :title="cdr?.didNumber || cdr?.callee">
          {{ cdr?.didNumber || cdr?.callee || '021-99998888' }}
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Volume2 class="w-3 h-3 text-amber-500" />
          <span>客户实际按键</span>
        </span>
        <div>
          <span
            v-if="digitInfo.status === 'PRESSED'"
            class="px-2 py-0.5 bg-emerald-600 text-white font-mono font-black text-xs rounded-md shadow-2xs"
          >
            按键 [ {{ digitInfo.digit }} ]
          </span>
          <span
            v-else-if="digitInfo.status === 'TIMEOUT'"
            class="px-2 py-0.5 bg-amber-100 text-amber-800 font-mono font-bold text-xs rounded-md border border-amber-300"
          >
            未按键 · 超时
          </span>
          <span
            v-else-if="digitInfo.status === 'SKIPPED'"
            class="px-2 py-0.5 bg-slate-100 text-slate-600 font-mono text-xs rounded-md border border-slate-200"
          >
            无菜单 · 直通
          </span>
          <span v-else class="text-slate-500 font-mono font-bold text-xs">
            {{ digitInfo.label }}
          </span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Users class="w-3 h-3 text-cyan-600" />
          <span>接待坐席 / 工号</span>
        </span>
        <div class="font-bold text-slate-900 truncate">
          {{ cdr?.agentName || '坐席分配中' }}
          <span class="text-slate-400 font-mono text-[11px] font-normal">({{ cdr?.agentWorkNo || '-' }})</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <PhoneCall class="w-3 h-3 text-emerald-600" />
          <span>振铃 / 通话时长</span>
        </span>
        <div class="font-mono text-slate-900 font-bold">
          <span>{{ cdr?.ringDuration || '0秒' }}</span>
          <span class="text-slate-300 mx-1">/</span>
          <span class="text-emerald-700 font-black">{{ cdr?.audioDuration || '0秒' }}</span>
        </div>
      </div>

      <div class="flex flex-col gap-0.5">
        <span class="text-[11px] font-bold text-slate-400 flex items-center gap-1">
          <Award class="w-3 h-3 text-amber-500" />
          <span>服务满意度评价</span>
        </span>
        <div class="font-bold">
          <span v-if="cdr?.satisfactionScore" class="text-amber-600 font-black font-mono">
            ★ {{ cdr.satisfactionScore }} 星好评
          </span>
          <span v-else class="text-slate-400 text-xs">
            未评价 / 挂机退出
          </span>
        </div>
      </div>
    </div>

    <!-- 错误或未加载提示 -->
    <div v-if="error" class="p-6 text-center text-rose-600 text-sm space-y-2">
      <p>{{ error }}</p>
      <el-button size="small" @click="load()">重新加载</el-button>
    </div>

    <!-- 主展示区 -->
    <div v-else class="journey-body flex-1 flex overflow-hidden min-h-0 bg-slate-50/40">
      <!-- ================= 模式 1: 业务旅程时间线 (呼叫中心管理员专属，动态匹配模型) ================= -->
      <div v-if="viewMode === 'journey'" class="flex-1 flex overflow-hidden">
        <!-- 左侧时间线列表 -->
        <div class="flex-1 overflow-y-auto p-4 md:p-6 space-y-3.5">
          <article
            v-for="card in journeyCards"
            :key="card.key"
            class="journey-card rounded-2xl p-4 bg-white border transition-all cursor-pointer shadow-2xs hover:shadow-card"
            :class="selected === card.key ? 'border-indigo-500 ring-2 ring-indigo-100 bg-indigo-50/20' : 'border-slate-200/90'"
            @click="selected = card.key"
          >
            <!-- 头部 -->
            <div class="flex items-start justify-between gap-3 mb-2.5">
              <div class="flex items-center gap-2.5">
                <span class="w-7 h-7 rounded-xl bg-slate-800 text-white font-mono font-black text-xs flex items-center justify-center shadow-2xs">
                  {{ card.stepNo }}
                </span>
                <div>
                  <h4 class="font-bold text-slate-900 text-sm">{{ card.title }}</h4>
                  <p class="text-[11px] text-slate-400">{{ card.subTitle }}</p>
                </div>
              </div>
              <span class="px-2 py-0.5 rounded-full text-xs font-bold border" :class="card.badgeClass">
                {{ card.statusText }}
              </span>
            </div>

            <!-- ================= 各阶段定制业务高亮区 ================= -->

            <!-- 1. 话机绑定：识别绑定话机 (ENTRY) -->
            <div v-if="card.highlightType === 'binding_entry'" class="p-3 bg-emerald-50/60 rounded-xl border border-emerald-100 flex flex-wrap items-center justify-between gap-3 text-xs">
              <div class="flex items-center gap-2.5">
                <span class="font-bold text-emerald-900">📱 认证 SIP 话机分机:</span>
                <span class="font-mono font-black text-emerald-800 text-base bg-white px-3 py-0.5 rounded-lg border border-emerald-200 shadow-2xs">
                  {{ bindingExtension }}
                </span>
              </div>
              <div class="text-slate-600">
                接入入口号码: <strong class="font-mono text-slate-900">0000</strong> (专用话机绑定 dialplan)
              </div>
            </div>

            <!-- 2. 话机绑定：应答绑定话道 (ANSWER) -->
            <div v-else-if="card.highlightType === 'binding_answer'" class="p-3 bg-blue-50/60 rounded-xl border border-blue-100 text-xs text-blue-900 space-y-1">
              <div class="font-bold">✓ FreeSWITCH 已应答话机通道并完成握手</div>
              <div class="text-slate-500 text-[11px]">准备发送 TTS 语音并开启 DTMF 坐席工号收号状态机</div>
            </div>

            <!-- 3. 话机绑定：收取坐席工号 (COLLECT_CODE) -->
            <div v-else-if="card.highlightType === 'binding_collect'" class="space-y-2.5">
              <div class="p-3 bg-amber-50/70 border border-amber-200/80 rounded-xl space-y-1">
                <div class="text-[11px] font-bold text-amber-900 flex items-center gap-1.5">
                  <Volume2 class="w-3.5 h-3.5 text-amber-600" />
                  <span>播报引导音频提示词</span>
                </div>
                <div class="text-xs text-slate-800 font-medium pl-5 leading-relaxed">
                  “{{ bindingPromptText }}”
                </div>
              </div>

              <!-- 🌟 核心高亮：用户实际按键输入的工号 -->
              <div class="p-3 bg-slate-50 rounded-xl border border-slate-100 flex items-center justify-between gap-3 flex-wrap text-xs">
                <div class="flex items-center gap-2.5">
                  <span class="font-bold text-slate-700">⌨️ 话机实际输入工号:</span>
                  <template v-if="bindingWorkNo">
                    <span class="px-3.5 py-1 bg-emerald-600 text-white font-mono font-black text-sm rounded-lg shadow-2xs tracking-wide">
                      工号 [ {{ bindingWorkNo }}# ]
                    </span>
                    <span class="text-xs text-emerald-700 font-bold font-mono">成功接收 DTMF 信号 (以 # 结束)</span>
                  </template>
                  <template v-else>
                    <span class="px-3 py-1 bg-amber-100 text-amber-800 font-mono font-bold text-xs rounded-lg border border-amber-300">
                      未收到完整工号
                    </span>
                  </template>
                </div>
                <div class="text-slate-400">
                  收号耗时: <strong class="text-slate-700 font-mono">{{ card.durationText }}</strong>
                </div>
              </div>
            </div>

            <!-- 4. 话机绑定：校验工号并原子换绑 (VERIFY_BINDING) -->
            <div v-else-if="card.highlightType === 'binding_verify'" class="p-3 bg-indigo-50/70 border border-indigo-200 rounded-xl space-y-2 text-xs">
              <div class="flex items-center gap-2">
                <span class="text-indigo-700 font-bold">🎯 换绑事务执行事实:</span>
                <span class="font-mono font-black text-slate-900">
                  分机 [ {{ bindingExtension }} ] ⇄ 坐席工号 [ {{ bindingWorkNo }} ]
                </span>
              </div>
              <div class="flex items-center gap-2 pt-1 border-t border-indigo-100 text-slate-700">
                <span>绑定坐席姓名:</span>
                <span class="font-bold text-indigo-900 bg-white px-2 py-0.5 rounded border border-indigo-200">{{ bindingAgentName }}</span>
                <span class="text-emerald-700 font-bold ml-auto">✓ 事务锁定并换绑成功</span>
              </div>
            </div>

            <!-- 5. 话机绑定：播报绑定结果 (RESULT) -->
            <div v-else-if="card.highlightType === 'binding_result'" class="p-3 bg-slate-50 border border-slate-100 rounded-xl text-xs space-y-1">
              <div class="font-bold text-slate-700 flex items-center gap-1.5">
                <Volume2 class="w-3.5 h-3.5 text-indigo-600" />
                <span>播报换绑结果语音</span>
              </div>
              <div class="text-slate-800 font-medium pl-5 leading-relaxed">
                “{{ isBindingAccepted ? bindingSuccessText : '话机绑定失败，请确认工号后重试。' }}”
              </div>
            </div>

            <!-- 6. 普通呼入：DID 专线接管 (inbound_entry) -->
            <div v-else-if="card.highlightType === 'inbound_entry'" class="p-3 bg-indigo-50/60 rounded-xl border border-indigo-100 flex flex-wrap items-center justify-between gap-3 text-xs">
              <div class="flex items-center gap-2.5">
                <span class="font-bold text-indigo-900">🎯 接入 DID 专线号码:</span>
                <span class="font-mono font-black text-indigo-700 text-base bg-white px-3 py-0.5 rounded-lg border border-indigo-200 shadow-2xs">
                  {{ cdr?.didNumber || cdr?.callee || '021-99998888' }}
                </span>
              </div>
              <div class="text-slate-600">
                主叫客户: <strong class="font-mono text-slate-900">{{ cdr?.callerPhone }}</strong>
                <span class="text-slate-400 text-[11px] ml-1">({{ cdr?.carrier || '本地' }})</span>
              </div>
            </div>

            <!-- 7. 普通呼入：欢迎语与按键 (inbound_menu) -->
            <div v-else-if="card.highlightType === 'inbound_menu'" class="space-y-2.5 text-xs">
              <div class="p-3 bg-amber-50/70 border border-amber-200/80 rounded-xl space-y-1">
                <div class="text-[11px] font-bold text-amber-900 flex items-center gap-1.5">
                  <Volume2 class="w-3.5 h-3.5 text-amber-600" />
                  <span>播报欢迎语音频内容</span>
                </div>
                <div class="text-slate-800 font-medium pl-5 leading-relaxed">
                  “{{ flow?.menu?.prompt || '您好，欢迎致电客服中心！人工服务请按1，其他请按0。' }}”
                </div>
              </div>
              <div class="p-3 bg-slate-50 rounded-xl border border-slate-100 flex items-center justify-between gap-3 flex-wrap">
                <div class="flex items-center gap-2.5">
                  <span class="font-bold text-slate-700">⌨️ 客户实际按键:</span>
                  <span v-if="digitInfo.status === 'PRESSED'" class="px-3.5 py-1 bg-emerald-600 text-white font-mono font-black text-sm rounded-lg shadow-2xs">
                    按键 [ {{ digitInfo.digit }} ]
                  </span>
                  <span v-else class="px-2.5 py-1 bg-amber-100 text-amber-800 font-mono font-bold text-xs rounded-lg border border-amber-300">
                    {{ digitInfo.label }}
                  </span>
                </div>
                <div class="text-slate-400">收号耗时: <strong class="text-slate-700 font-mono">{{ card.durationText }}</strong></div>
              </div>
            </div>

            <!-- 8. 普通呼入：路由分流 (inbound_branch) -->
            <div v-else-if="card.highlightType === 'inbound_branch'" class="p-3 bg-blue-50/70 border border-blue-200 rounded-xl space-y-2 text-xs">
              <div class="flex items-center gap-2">
                <span class="text-blue-700 font-bold">🎯 实际命中路由规则:</span>
                <span class="font-mono font-black text-slate-900">{{ resolvedRoute.ruleDesc }}</span>
              </div>
              <div class="flex items-center gap-2 pt-1 border-t border-blue-100 text-slate-600">
                <span>分流目标:</span>
                <span class="px-2.5 py-1 bg-white border border-blue-200 rounded-lg font-mono font-black text-blue-900 text-xs shadow-2xs">
                  {{ resolvedRoute.type }} · {{ resolvedRoute.target }}
                </span>
                <span class="text-slate-400 text-[11px] ml-auto">决策耗时: <strong class="text-slate-700 font-mono">{{ card.durationText }}</strong></span>
              </div>
            </div>

            <!-- 9. 普通呼入：技能组排队与坐席分配 (inbound_route) -->
            <div v-else-if="card.highlightType === 'inbound_route'" class="p-3 bg-slate-50 border border-slate-100 rounded-xl grid grid-cols-1 md:grid-cols-3 gap-2.5 text-xs">
              <div>
                <span class="text-slate-400 text-[11px]">目标技能组:</span>
                <div class="font-bold text-slate-800 font-mono mt-0.5">{{ allFacts.groupCode || resolvedRoute.target }}</div>
              </div>
              <div>
                <span class="text-slate-400 text-[11px]">分配坐席:</span>
                <div class="font-bold text-slate-900 mt-0.5 flex items-center gap-1.5">
                  <span>{{ cdr?.agentName || '坐席分配中' }}</span>
                  <span class="text-indigo-600 font-mono text-[11px]">({{ cdr?.agentWorkNo || '-' }})</span>
                </div>
              </div>
              <div>
                <span class="text-slate-400 text-[11px]">排队与振铃耗时:</span>
                <div class="font-bold text-amber-600 font-mono mt-0.5">{{ card.durationText }}</div>
              </div>
            </div>

            <!-- 10. 普通呼入：双向通话 (inbound_connected) -->
            <div v-else-if="card.highlightType === 'inbound_connected'" class="p-3 bg-emerald-50/50 border border-emerald-100 rounded-xl flex items-center justify-between gap-3 flex-wrap text-xs">
              <div class="flex items-center gap-3">
                <span class="text-slate-700">通话时长: <strong class="font-mono font-black text-emerald-800 text-sm ml-1">{{ card.durationText }}</strong></span>
                <span class="text-slate-300">|</span>
                <span class="text-slate-500 font-medium">双轨高保真录音已归档</span>
              </div>
              <button
                v-if="cdr?.recordingUrl"
                type="button"
                class="px-3 py-1 bg-white hover:bg-emerald-600 text-emerald-700 hover:text-white rounded-lg text-xs font-bold border border-emerald-300 transition-all cursor-pointer flex items-center gap-1.5 shadow-2xs"
                @click.stop="$emit('play-audio')"
              >
                <span>▶ 试听录音 ({{ cdr.audioDuration || '播放' }})</span>
              </button>
            </div>

            <!-- 11. 通话结案 (inbound_end) -->
            <div v-else-if="card.highlightType === 'inbound_end'" class="p-3 bg-slate-50 border border-slate-100 rounded-xl flex items-center justify-between gap-3 flex-wrap text-xs">
              <div v-if="currentTemplate !== 'PHONE_BINDING'" class="flex items-center gap-2">
                <span class="text-slate-500 font-bold">客户评价:</span>
                <span v-if="cdr?.satisfactionScore" class="font-mono font-black text-amber-600 text-sm">★ {{ cdr.satisfactionScore }} 星好评</span>
                <span v-else class="text-slate-400">未评价 / 挂机退出</span>
              </div>
              <div class="text-slate-500">
                挂机原因: <strong class="font-mono text-slate-800">{{ cdr?.hangupCause || allFacts.cause || 'NORMAL_CLEARING' }}</strong>
              </div>
              <div class="text-slate-400 text-[11px]">结束时间: {{ card.startedAt }}</div>
            </div>

            <!-- 12. 外呼流程：外呼任务模式 (outbound_entry) -->
            <div v-else-if="card.highlightType === 'outbound_entry'" class="p-3 bg-blue-50/60 rounded-xl border border-blue-100 flex flex-wrap items-center justify-between gap-3 text-xs">
              <div class="flex items-center gap-2.5">
                <span class="font-bold text-blue-900">🚀 外呼任务模式:</span>
                <span class="font-mono font-black text-blue-800 text-base bg-white px-3 py-0.5 rounded-lg border border-blue-200 shadow-2xs">
                  {{ currentTemplate === 'AGENT_ORIGINATED' ? '坐席话机直接外呼 (AGENT_ORIGINATED)' : '坐席优先双呼外呼 (AGENT_FIRST)' }}
                </span>
              </div>
              <div class="text-slate-600">
                目标客户号码: <strong class="font-mono text-slate-900">{{ cdr?.callee || allFacts.guestNumber || '-' }}</strong>
              </div>
            </div>

            <!-- 13. 外呼流程：呼叫坐席 (outbound_dial_agent) -->
            <div v-else-if="card.highlightType === 'outbound_dial_agent'" class="p-3 bg-indigo-50/70 border border-indigo-200 rounded-xl space-y-2 text-xs">
              <div class="flex items-center gap-2">
                <span class="text-indigo-700 font-bold">👤 业务坐席通道:</span>
                <span class="font-mono font-black text-slate-900">
                  坐席分机 [ {{ allFacts.agentExt || cdr?.agentWorkNo || cdr?.callerPhone || '1007' }} ] · {{ cdr?.agentName || '业务坐席' }}
                </span>
              </div>
              <div class="flex items-center gap-2 pt-1 border-t border-indigo-100 text-slate-600">
                <span>状态:</span>
                <span class="text-emerald-700 font-bold">✓ 坐席话道已应答接管</span>
                <span class="text-slate-400 text-[11px] ml-auto">{{ currentTemplate === 'AGENT_ORIGINATED' ? '话机直拨直接应答' : '话务引擎完成分机振铃握手' }}</span>
              </div>
            </div>

            <!-- 14. 外呼流程：呼叫客户 (outbound_dial_customer) -->
            <div v-else-if="card.highlightType === 'outbound_dial_customer'" class="p-3 bg-purple-50/70 border border-purple-200 rounded-xl space-y-2 text-xs">
              <div class="flex items-center gap-2">
                <span class="text-purple-700 font-bold">📞 目标被叫客户:</span>
                <span class="font-mono font-black text-slate-900 text-sm">
                  {{ cdr?.callee || allFacts.guestNumber || '-' }}
                </span>
              </div>
              <div class="flex items-center gap-2 pt-1 border-t border-purple-100 text-slate-600">
                <span>客户接听状态:</span>
                <span class="font-bold" :class="cdr?.status === 'ANSWERED' ? 'text-emerald-700' : 'text-amber-600'">
                  {{ cdr?.status === 'ANSWERED' ? '✓ 客户已成功接听' : '振铃中 / 呼叫完成' }}
                </span>
                <span class="text-slate-400 text-[11px] ml-auto">客户振铃耗时: <strong class="text-slate-700 font-mono">{{ card.durationText }}</strong></span>
              </div>
            </div>

            <!-- 15. 外呼流程：双向通话 (outbound_connected) -->
            <div v-else-if="card.highlightType === 'outbound_connected'" class="p-3 bg-emerald-50/50 border border-emerald-100 rounded-xl flex items-center justify-between gap-3 flex-wrap text-xs">
              <div class="flex items-center gap-3">
                <span class="text-slate-700">双方通话时长: <strong class="font-mono font-black text-emerald-800 text-sm ml-1">{{ card.durationText }}</strong></span>
                <span class="text-slate-300">|</span>
                <span class="text-slate-500 font-medium">双向桥接 (Bridge) · 双轨高保真录音已存档</span>
              </div>
              <button
                v-if="cdr?.recordingUrl"
                type="button"
                class="px-3 py-1 bg-white hover:bg-emerald-600 text-emerald-700 hover:text-white rounded-lg text-xs font-bold border border-emerald-300 transition-all cursor-pointer flex items-center gap-1.5 shadow-2xs"
                @click.stop="$emit('play-audio')"
              >
                <span>▶ 试听录音 ({{ cdr.audioDuration || '播放' }})</span>
              </button>
            </div>

            <!-- 16. 外呼流程：服务评价 (outbound_rating) -->
            <div v-else-if="card.highlightType === 'outbound_rating'" class="space-y-2.5 text-xs">
              <div class="p-3 bg-amber-50/70 border border-amber-200/80 rounded-xl space-y-1">
                <div class="text-[11px] font-bold text-amber-900 flex items-center gap-1.5">
                  <Volume2 class="w-3.5 h-3.5 text-amber-600" />
                  <span>播报服务满意度评价语音</span>
                </div>
                <div class="text-slate-800 font-medium pl-5 leading-relaxed">
                  “请在听到滴声后对本次服务进行评价，非常满意请按1，满意请按2，不满意请按3。”
                </div>
              </div>
              <div class="p-3 bg-slate-50 rounded-xl border border-slate-100 flex items-center justify-between gap-3 flex-wrap">
                <div class="flex items-center gap-2.5">
                  <span class="font-bold text-slate-700">⌨️ 客户实际打分按键:</span>
                  <span v-if="cdr?.satisfactionScore || allFacts.evaluationScore" class="px-3.5 py-1 bg-amber-500 text-white font-mono font-black text-sm rounded-lg shadow-2xs">
                    ★ {{ cdr?.satisfactionScore || allFacts.evaluationScore }} 星 (按键 {{ cdr?.satisfactionScore || allFacts.evaluationScore }})
                  </span>
                  <span v-else class="px-2.5 py-1 bg-slate-100 text-slate-500 font-mono font-bold text-xs rounded-lg border border-slate-300">
                    未打分 / 挂机退出
                  </span>
                </div>
                <div class="text-slate-400">评价阶段耗时: <strong class="text-slate-700 font-mono">{{ card.durationText }}</strong></div>
              </div>
            </div>

            <!-- 17. 外呼流程：结案归档 (outbound_end) -->
            <div v-else-if="card.highlightType === 'outbound_end'" class="p-3 bg-slate-50 border border-slate-100 rounded-xl flex items-center justify-between gap-3 flex-wrap text-xs">
              <div class="flex items-center gap-2">
                <span class="text-slate-500 font-bold">最终服务评价:</span>
                <span v-if="cdr?.satisfactionScore || allFacts.evaluationScore" class="font-mono font-black text-amber-600 text-sm">★ {{ cdr?.satisfactionScore || allFacts.evaluationScore }} 星</span>
                <span v-else class="text-slate-400">未评价</span>
              </div>
              <div class="text-slate-500">
                挂机原因: <strong class="font-mono text-slate-800">{{ cdr?.hangupCause || allFacts.cause || 'NORMAL_CLEARING' }}</strong>
              </div>
              <div class="text-slate-400 text-[11px]">结束时间: {{ card.startedAt }}</div>
            </div>

            <!-- 底部辅助时间戳 -->
            <div class="mt-2.5 flex items-center justify-between text-xs text-slate-400 pt-1.5 border-t border-slate-100">
              <span>时间: {{ card.startedAt }}</span>
              <span>执行耗时: <strong class="text-slate-700 font-mono">{{ card.durationText }}</strong></span>
            </div>
          </article>
        </div>

        <!-- 右侧阶段深度抽屉 (Inspector Drawer) -->
        <aside class="w-80 border-l border-slate-200 bg-white p-4 overflow-y-auto shrink-0 flex flex-col justify-between">
          <div class="space-y-4">
            <div class="border-b border-slate-100 pb-3">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-slate-400">阶段详情检查</span>
                <span class="px-2 py-0.5 text-[11px] font-bold rounded-md bg-indigo-50 text-indigo-700 font-mono">
                  {{ selected }}
                </span>
              </div>
              <h3 class="font-black text-slate-900 text-base mt-1">
                {{ stageLabels[selected] || currentSelectedCard?.title || selected }}
              </h3>
            </div>

            <!-- 结构化业务事实参数清单 -->
            <div class="space-y-2.5 text-xs">
              <div class="font-bold text-slate-700 text-xs flex items-center gap-1">
                <span>📋 业务事实快照</span>
              </div>

              <!-- 话机绑定模式专属属性表 -->
              <div v-if="currentTemplate === 'PHONE_BINDING'" class="bg-slate-50 rounded-xl p-3 border border-slate-100 space-y-2">
                <div class="flex justify-between">
                  <span class="text-slate-400">绑定通道</span>
                  <span class="font-mono font-bold text-slate-900">0000 (自助入口)</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">话机分机</span>
                  <span class="font-mono font-bold text-indigo-700">{{ bindingExtension }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">输入工号</span>
                  <span class="font-mono font-bold text-emerald-600">{{ bindingWorkNo ? `${bindingWorkNo}#` : '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">换绑坐席</span>
                  <span class="font-bold text-slate-900">{{ bindingAgentName }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">事务结果</span>
                  <span class="font-bold" :class="isBindingAccepted ? 'text-emerald-700' : 'text-rose-600'">
                    {{ isBindingAccepted ? '换绑成功' : '换绑失败' }}
                  </span>
                </div>
              </div>

              <!-- 双向外呼专属属性表 -->
              <div v-else-if="isOutbound" class="bg-slate-50 rounded-xl p-3 border border-slate-100 space-y-2">
                <div class="flex justify-between">
                  <span class="text-slate-400">呼叫模式</span>
                  <span class="font-mono font-bold text-blue-700">{{ currentTemplate === 'AGENT_ORIGINATED' ? '坐席话机直接外呼' : '坐席优先双呼外呼' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">业务坐席</span>
                  <span class="font-bold text-slate-900">{{ cdr?.agentName || '-' }} ({{ cdr?.agentWorkNo || '-' }})</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">坐席分机</span>
                  <span class="font-mono font-bold text-indigo-700">{{ allFacts.agentExt || cdr?.callerPhone || '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">被叫客户</span>
                  <span class="font-mono font-bold text-purple-700">{{ cdr?.callee || allFacts.guestNumber || '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">服务评价</span>
                  <span class="font-bold text-amber-600">{{ (cdr?.satisfactionScore || allFacts.evaluationScore) ? `${cdr?.satisfactionScore || allFacts.evaluationScore} 星` : '未评价' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">通话结果</span>
                  <span class="font-bold" :class="cdr?.status === 'ANSWERED' ? 'text-emerald-700' : 'text-slate-600'">
                    {{ cdr?.status === 'ANSWERED' ? '双向已接通' : '未接通' }}
                  </span>
                </div>
              </div>

              <!-- 呼入 IVR 模式专属属性表 -->
              <div v-else class="bg-slate-50 rounded-xl p-3 border border-slate-100 space-y-2">
                <div class="flex justify-between">
                  <span class="text-slate-400">专线 DID</span>
                  <span class="font-mono font-bold text-indigo-700">{{ cdr?.didNumber || cdr?.callee || '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">主叫号码</span>
                  <span class="font-mono font-bold text-slate-900">{{ cdr?.callerPhone || '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">实际按键</span>
                  <span class="font-mono font-bold" :class="digitInfo.digit ? 'text-emerald-600' : 'text-slate-500'">
                    {{ digitInfo.label }}
                  </span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">分流目标</span>
                  <span class="font-mono font-bold text-blue-700">{{ resolvedRoute.target }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">接待坐席</span>
                  <span class="font-bold text-slate-900">{{ cdr?.agentName || '-' }} ({{ cdr?.agentWorkNo || '-' }})</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400">服务评价</span>
                  <span class="font-bold text-amber-600">{{ cdr?.satisfactionScore ? `${cdr.satisfactionScore} 星` : '未评价' }}</span>
                </div>
              </div>
            </div>

            <!-- 执行时间与耗时 -->
            <div class="space-y-2 text-xs">
              <div class="font-bold text-slate-700 flex items-center gap-1">
                <span>⏱️ 时间与延迟</span>
              </div>
              <div class="bg-slate-50 rounded-xl p-3 border border-slate-100 space-y-1.5 font-mono text-[11px]">
                <div class="flex justify-between">
                  <span class="text-slate-400 font-sans">开始时间</span>
                  <span class="text-slate-700">{{ currentSelectedCard?.startedAt || '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-slate-400 font-sans">耗时</span>
                  <span class="font-bold text-slate-900">{{ currentSelectedCard?.durationText || '-' }}</span>
                </div>
              </div>
            </div>

            <!-- 折叠技术审计事件 (默认折叠，不干扰日常呼叫中心管理员) -->
            <details class="text-xs group border border-slate-200 rounded-xl overflow-hidden bg-slate-50/50">
              <summary class="px-3 py-2.5 font-bold text-slate-600 cursor-pointer select-none hover:bg-slate-100 flex items-center justify-between">
                <span>🔧 原始技术事件与协议报文</span>
                <ChevronRight class="w-3.5 h-3.5 transition-transform group-open:rotate-90 text-slate-400" />
              </summary>
              <div class="p-3 bg-white border-t border-slate-200 space-y-3">
                <div v-for="attempt in selectedAttempts" :key="attempt.id" class="border-b border-slate-100 pb-2.5 last:border-0 last:pb-0">
                  <div class="font-bold text-slate-800 text-[11px] mb-1">
                    第 {{ attempt.attemptNo }} 次尝试 · {{ executionLabels[attempt.status] || attempt.status }}
                  </div>
                  <div v-if="attempt.eventId" class="flex items-center justify-between text-[11px] font-mono text-slate-500 mb-1">
                    <span class="truncate" :title="attempt.eventId">事件: {{ attempt.eventId }}</span>
                    <button
                      type="button"
                      class="text-indigo-600 hover:text-indigo-800 ml-1 cursor-pointer shrink-0"
                      @click="copyToClipboard(attempt.eventId, attempt.id + 'event')"
                    >
                      <Check v-if="copiedKey === attempt.id + 'event'" class="w-3 h-3 text-emerald-600 inline" />
                      <Copy v-else class="w-3 h-3 inline" />
                    </button>
                  </div>
                  <pre class="bg-slate-900 text-slate-100 p-2 rounded-lg text-[10px] font-mono overflow-x-auto max-h-36 leading-tight">{{ attempt.output || attempt.input || '{}' }}</pre>
                </div>
                <div v-if="!selectedAttempts.length" class="text-slate-400 text-center py-2 text-[11px]">
                  此阶段无独立底层尝试记录
                </div>
              </div>
            </details>
          </div>

          <div v-if="cursor" class="pt-3 border-t border-slate-100">
            <el-button class="w-full" size="small" :loading="loading" @click="load(true)">
              加载更多阶段记录
            </el-button>
          </div>
        </aside>
      </div>

      <!-- ================= 模式 2: 底层流程模型拓扑 (供系统架构/运维人员对照流程模板) ================= -->
      <div v-else class="flex-1 flex overflow-hidden">
        <FlowCanvas
          v-if="flow"
          :flow="flow"
          :executions="executions"
          :selected="selected"
          readonly
          class="flex-1 min-w-0"
          @select="selected = $event"
        />
        <aside v-if="selected" class="w-80 border-l border-slate-200 bg-white p-4 overflow-y-auto shrink-0 text-xs">
          <h3 class="font-bold text-slate-900 text-sm mb-2">{{ selected }} · 执行尝试</h3>
          <p v-if="!selectedAttempts.length" class="text-slate-400 text-xs py-4 text-center">
            未经过此阶段，或记录尚未加载。
          </p>
          <article v-for="attempt in selectedAttempts" :key="attempt.id" class="py-2.5 border-b border-slate-100 last:border-0">
            <strong class="text-slate-800">
              第 {{ attempt.attemptNo }} 次 · {{ executionLabels[attempt.status] || attempt.status }}
            </strong>
            <dl class="mt-2 text-slate-600 space-y-1">
              <div class="flex justify-between"><dt class="text-slate-400">开始</dt><dd class="font-mono">{{ attempt.startedAt }}</dd></div>
              <div class="flex justify-between"><dt class="text-slate-400">耗时</dt><dd class="font-mono">{{ attempt.durationMs == null ? '—' : `${attempt.durationMs} ms` }}</dd></div>
              <div class="flex justify-between"><dt class="text-slate-400">事件</dt><dd class="font-mono truncate max-w-[160px]">{{ attempt.eventId || '内部推进' }}</dd></div>
            </dl>
            <pre class="mt-2 bg-slate-900 text-slate-100 p-2 rounded-lg text-[10px] font-mono overflow-x-auto max-h-32">{{ attempt.output || attempt.input || '{}' }}</pre>
          </article>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.call-execution-panel {
  min-height: 480px;
  max-height: 62vh;
}
.journey-card {
  border-left-width: 4px;
}
.journey-card:hover {
  transform: translateY(-1px);
}
</style>
