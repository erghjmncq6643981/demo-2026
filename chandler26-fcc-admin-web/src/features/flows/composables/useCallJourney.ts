import { executionEvidence, bindingOutcome } from '../model/journeyEvidence';
import { ref, computed, toRef, watch } from 'vue';
import { useScopeTimers } from '../../../shared/lifecycle/scopeTimers';
import { useCallExecution } from './useCallExecution';
import { collectExecutionFacts } from '../model/executionFacts';
import { executionLabels, stageLabels, type StageExecution } from '../model/stagedFlow';
import type { CallRecord } from '../../cdr/model/callRecord';

/** Owns the feature state, effects and use cases; the view only composes rendering. */
export function useCallJourney(props: Readonly<{ callId: string; cdr?: CallRecord | null }>) {
  const timers = useScopeTimers();
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
      timers.schedule(() => {
        if (copiedKey.value === key) copiedKey.value = '';
      }, 2000);
    } catch (e) {
      console.error('Copy failed', e);
    }
  };

  // 提取所有执行步骤与通话上下文中的权威运行时事实 (JSON facts)
  const allFacts = computed(() => collectExecutionFacts(executions.value));

  // 核心：精准识别当前通话绑定的流程模型类型 (不可被 INBOUND 假定覆盖)
  const isOutbound = computed(() => {
    const fCode = String(props.cdr?.flowCode || '').toUpperCase();
    if (fCode.includes('AGENT_FIRST') || fCode.includes('AGENT_ORIGINATED') || fCode.includes('OUTBOUND')) return true;
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
    const fCode = String(props.cdr?.flowCode || '').toUpperCase();
    if (fCode === 'SYSTEM_PHONE_BINDING' || (flow.value as any)?.template === 'PHONE_BINDING' || allFacts.value.runtimeTemplate === 'PHONE_BINDING' || props.cdr?.modelType === 'PHONE_BINDING') {
      return 'PHONE_BINDING';
    }
    if (fCode === 'SYSTEM_AGENT_ORIGINATED') return 'AGENT_ORIGINATED';
    if (fCode === 'SYSTEM_AGENT_FIRST') return 'AGENT_FIRST';
    if (fCode === 'SYSTEM_NOTIFICATION') return 'NOTIFICATION';
    if (isOutbound.value) {
      if ((flow.value as any)?.template === 'AGENT_ORIGINATED' || allFacts.value.runtimeTemplate === 'AGENT_ORIGINATED') {
        return 'AGENT_ORIGINATED';
      }
      return (flow.value as any)?.template || allFacts.value.runtimeTemplate || 'AGENT_FIRST';
    }
    if (fCode === 'INBOUND_IVR') return 'INBOUND';
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
      '-'
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

  const isBindingAccepted = computed(() => bindingOutcome(allFacts.value));

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
  const getStepExec = (key: string) => [...executions.value].reverse().find((e) => e.stepKey === key);

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

  const plannedCards = computed<JourneyCardItem[]>(() => {
    const cards: JourneyCardItem[] = [];
    const tpl = currentTemplate.value;

    // Notifications have no agent/IVR journey; show only their recorded stages.
    if (tpl === 'NOTIFICATION') {
      return [...new Set(executions.value.map(step => step.stepKey))].map((key, index) => ({
        key, stepNo: String(index + 1).padStart(2, '0'),
        title: stageLabels[key] || key, subTitle: '通知流程执行记录',
        highlightType: 'generic' as const, raw: getStepExec(key),
        ...executionEvidence(getStepExec(key)),
      }));
    }

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

  // Stage descriptions are navigation, not proof of business success.
  const journeyCards = computed(() => plannedCards.value.map(card => ({
    ...card, ...executionEvidence(card.raw),
  })));

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
    const recordedKey = journeyCards.value.find((card) => card.key === selected.value)?.raw?.stepKey;
    return executions.value.filter((e) => e.stepKey === selected.value || e.stepKey === recordedKey);
  });

  const currentSelectedCard = computed(() => {
    return journeyCards.value.find((c) => c.key === selected.value);
  });

  return { viewMode, selected, copiedKey, copyToClipboard, allFacts, isOutbound, currentTemplate, bindingWorkNo, bindingExtension, bindingAgentName, bindingPromptText, bindingSuccessText, isBindingAccepted, effectiveBranch, digitInfo, resolvedRoute, getStepExec, journeyCards, selectedAttempts, currentSelectedCard, flow, executions, error, loading, cursor, status, version, load };
}
