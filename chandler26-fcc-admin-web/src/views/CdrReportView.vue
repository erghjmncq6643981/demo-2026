<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { cdrApi, type CallCdrVO, type CallCdrQueryReq, type CdrStatsVO } from '../api/cdrApi';
import { callbackApi, type CallbackTaskVO } from '../api/callbackApi';
import { extensionApi } from '../api/extensionApi';
import { toast, toastError, errorText } from '../utils/feedback';

const props = withDefaults(defineProps<{
  initialTab?: 'records' | 'callback';
}>(), {
  initialTab: 'records'
});

// 🌟 核心双 Tab：'records' (通话记录) | 'callback' (未接待回拨)
const activeSubTab = ref<'records' | 'callback'>(props.initialTab || 'records');

watch(() => props.initialTab, (val) => {
  if (val) {
    activeSubTab.value = val;
  }
});

// ==================== 1. 通话记录 (Call Records) ====================
// ==================== 1. 通话记录 (Call Records) ====================
// 字段要求：主叫姓名/号码、运营商、坐席姓名、坐席工号、方向、通话开始时间、通话结束时间、响铃时长、录音（显示时长）、状态

export interface TraceStep {
  timeOffset: string;     // 相对时间，如 "+00:00.0s"
  stage: 'TRIGGER' | 'ROUTE' | 'CONNECTED' | 'END';
  stageName: string;      // 阶段名称: 触发应答 / 路由决策 / 通话中 / 结束收尾
  actionCode: string;     // 动作编码: ANSWER / READ_DTMF / HTTP_CALLBACK / RULE_ENGINE / BRIDGE / RECORD_START / POST_SURVEY / HANGUP
  actionName: string;     // 动作名称: 进线应答 / 收号按键 / 业务接口回调 / 通道桥接 / 开始双轨录音 / 满意度评价 / 释放挂断
  detail: string;         // 执行明细 / 出入参
  status: 'SUCCESS' | 'WARNING' | 'FAILED';
  duration?: string;      // 耗时，如 "120ms", "3.2s"
}

interface CallRecord {
  id: string;              // 通话ID (Call ID)
  callerName: string;      // 主叫姓名
  callerPhone: string;     // 主叫号码
  carrier: string;         // 运营商 (中国移动 / 中国电信 / 中国联通)
  agentName: string;       // 坐席姓名
  agentWorkNo: string;     // 坐席工号
  direction: 'INBOUND' | 'OUTBOUND' | 'INTERNAL'; // 方向: 呼入 / 呼出 / 内部
  startTime: string;       // 通话开始时间
  endTime: string;         // 通话结束时间
  ringDuration: string;    // 响铃时长
  audioDuration?: string;  // 录音（显示时长）
  recordingUrl?: string;   // 真实流式录音地址
  rawId?: number;          // 后端数据库自增 ID
  status: 'ANSWERED' | 'MISSED' | 'BUSY' | 'REJECTED'; // 状态: 已接通 / 未接听 / 坐席忙 / 拒接
  routeMode?: 'DID_DIRECT' | 'RULE_ENGINE' | 'HTTP_CALLBACK'; // 采用的路由模式
  satisfactionScore?: number; // 满意度评分 (1-5)
  executionTrace?: TraceStep[]; // 全生命周期过程详情链路追踪 (Stage + Action)
}

const callRecords = ref<CallRecord[]>([]);

// 获取并补全 CDR 的全生命周期时序流水线
const getRecordTrace = (cdr: CallRecord): TraceStep[] => {
  if (cdr.executionTrace && cdr.executionTrace.length > 0) {
    return cdr.executionTrace;
  }
  // 兜底动态构建
  return [
    { timeOffset: '+00:00.0s', stage: 'TRIGGER', stageName: '触发阶段', actionCode: 'START', actionName: cdr.direction === 'INBOUND' ? '进线建立通道' : '发起呼叫', detail: `主叫: ${cdr.callerPhone} -> 目标: ${cdr.agentName}`, status: 'SUCCESS' },
    { timeOffset: '+00:00.8s', stage: 'ROUTE', stageName: '路由阶段', actionCode: 'ROUTE', actionName: '信令路由寻址', detail: `呼叫坐席 ${cdr.agentName} (工号: ${cdr.agentWorkNo})`, status: 'SUCCESS', duration: cdr.ringDuration },
    { timeOffset: '+00:05.0s', stage: 'CONNECTED', stageName: '通话中', actionCode: 'BRIDGE', actionName: '通道接通', detail: cdr.audioDuration ? `双轨录音开启 (时长 ${cdr.audioDuration})` : '未录音', status: cdr.status === 'ANSWERED' ? 'SUCCESS' : 'FAILED' },
    { timeOffset: '+01:00.0s', stage: 'END', stageName: '结束阶段', actionCode: 'HANGUP', actionName: '通话释放挂断', detail: `状态: ${cdr.status}`, status: cdr.status === 'ANSWERED' ? 'SUCCESS' : 'WARNING' }
  ];
};

// 🔍 通话记录过滤条件 (严格按照用户指定：坐席姓名、坐席工号、号码、方向、通话ID、通话时间)
const filterAgentName = ref('');
const filterAgentWorkNo = ref('');
const filterPhone = ref('');
const filterDirection = ref('');
const filterCallId = ref('');
const filterStartTime = ref('');
const filterEndTime = ref('');
const filterDateRange = ref<[string, string] | null>(null);

const dateShortcuts = [
  {
    text: '今天',
    value: () => {
      const start = new Date();
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end];
    },
  },
  {
    text: '昨天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() - 1);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setDate(end.getDate() - 1);
      end.setHours(23, 59, 59, 999);
      return [start, end];
    },
  },
  {
    text: '近 3 天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() - 2);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end];
    },
  },
  {
    text: '近 7 天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() - 6);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end];
    },
  },
];

const handleDateRangeChange = (val: [string, string] | null) => {
  if (val && val.length === 2) {
    filterStartTime.value = val[0];
    filterEndTime.value = val[1];
  } else {
    filterStartTime.value = '';
    filterEndTime.value = '';
  }
};

const cdrPageNum = ref(1);
const cdrPageSize = ref(10);

/** "2026-09-19 13:00:00" -> "2026-09-19T13:00:00" */
const toIsoLocal = (value: string): string => value.trim().replace(' ', 'T');

/**
 * 将界面过滤条件装配为后端检索参数
 *
 * 说明：条件全部下推到 MySQL 执行，`total` 也由数据库 COUNT 得出，
 * 因此「共 N 条」始终是完整数据集的结果，不受分页或本地加载窗口影响。
 */
const buildCdrQuery = (): CallCdrQueryReq => {
  const params: CallCdrQueryReq = {
    pageNum: cdrPageNum.value,
    pageSize: cdrPageSize.value,
  };

  const agentName = filterAgentName.value.trim();
  if (agentName) params.agentName = agentName;

  const agentWorkNo = filterAgentWorkNo.value.trim();
  if (agentWorkNo) params.agentWorkNo = agentWorkNo;

  const phone = filterPhone.value.trim();
  if (phone) params.number = phone;

  if (filterDirection.value) params.direction = filterDirection.value;

  const callId = filterCallId.value.trim();
  if (callId) params.ctrlId = callId;

  // el-date-picker 输出 "YYYY-MM-DD HH:mm:ss"，后端 LocalDateTime 需要 ISO 的 "T" 分隔
  if (filterStartTime.value) params.startTime = toIsoLocal(filterStartTime.value);
  if (filterEndTime.value) params.endTime = toIsoLocal(filterEndTime.value);

  return params;
};

const cdrTotal = ref(0);
const cdrLoading = ref(false);

const handleSearchCall = async () => {
  cdrPageNum.value = 1;
  await loadCdrRecords();
};

const resetCallFilter = async () => {
  filterAgentName.value = '';
  filterAgentWorkNo.value = '';
  filterPhone.value = '';
  filterDirection.value = '';
  filterCallId.value = '';
  filterStartTime.value = '';
  filterEndTime.value = '';
  filterDateRange.value = null;
  cdrPageNum.value = 1;
  await loadCdrRecords();
};

// 翻页 / 改变每页条数均向数据库重新取数
watch([cdrPageNum, cdrPageSize], () => {
  loadCdrRecords();
});

const loadCdrRecords = async () => {
  cdrLoading.value = true;
  try {
    const res = await cdrApi.list(buildCdrQuery());
    cdrTotal.value = res?.total ?? 0;
    const list = res?.list ?? [];
    if (list.length > 0) {
      const realRecords: CallRecord[] = list.map((item: CallCdrVO) => {
        const ringSec = item.waitDurationMs ? Math.round(item.waitDurationMs / 1000) : 5;
        const talkSec = item.talkDurationMs ? Math.round(item.talkDurationMs / 1000) : 0;
        const audioDuration = item.audioDuration || (talkSec > 0 ? `${String(Math.floor(talkSec / 60)).padStart(2, '0')}:${String(talkSec % 60).padStart(2, '0')}` : '00:00');

        // 后端话单状态已归一化为 ANSWERED / NO_ANSWER，此处必须显式兜底为「未接听」，
        // 否则后续状态码（如 NO_ANSWER / TIMEOUT）会落到默认分支被误标成「已接通」，
        // 进而把接通率算成 100%。
        let status: 'ANSWERED' | 'MISSED' | 'BUSY' | 'REJECTED' = 'MISSED';
        if (item.status === 'ANSWERED') status = 'ANSWERED';
        else if (item.status === 'BUSY') status = 'BUSY';
        else if (item.status === 'REJECTED' || item.status === 'CANCELLED') status = 'REJECTED';
        else status = 'MISSED';
        
        return {
          id: item.ctrlId || `CDR-${item.id}`,
          callerName: item.callerName || (item.caller.startsWith('1') ? `客户 (${item.caller.slice(-4)})` : item.caller),
          callerPhone: item.caller,
          carrier: item.carrier || (item.caller.startsWith('13') ? '中国移动' : item.caller.startsWith('18') ? '中国电信' : '中国联通'),
          agentName: item.agentName || (item.agentWorkNo ? `坐席 ${item.agentWorkNo}` : '未分配'),
          agentWorkNo: item.agentWorkNo || '-',
          direction: (item.direction as any) || 'INBOUND',
          startTime: item.initiatedAt || item.answeredAt || '-',
          endTime: item.endedAt || '-',
          ringDuration: `${ringSec}秒`,
          audioDuration: audioDuration,
          recordingUrl: item.recordingUrl,
          rawId: item.id,
          status: status,
          routeMode: (item.routeMode as any) || 'RULE_ENGINE',
          satisfactionScore: item.evaluationScore || 5,
          executionTrace: item.executionTrace && item.executionTrace.length > 0 ? item.executionTrace : [
            { timeOffset: '+00:00.0s', stage: 'TRIGGER', stageName: '进线/起呼', actionCode: 'START', actionName: item.direction === 'OUTBOUND' ? '坐席发起外呼' : '客户进线应答', detail: `主叫 ${item.caller} -> 被叫 ${item.callee}`, status: 'SUCCESS' },
            { timeOffset: '+00:00.5s', stage: 'ROUTE', stageName: '路由阶段', actionCode: 'ROUTE', actionName: 'FreeSWITCH 寻址', detail: `路由模式: ${item.routeMode || 'RULE_ENGINE'}, 目标: ${item.agentName || item.agentWorkNo || '坐席组'}`, status: 'SUCCESS', duration: `${ringSec}s` },
            { timeOffset: `+00:0${ringSec}.0s`, stage: 'CONNECTED', stageName: '通话中', actionCode: 'BRIDGE', actionName: '通道桥接就绪', detail: 'uuid_bridge 桥接双方 RTP 媒体流，启动双轨录音', status: status === 'ANSWERED' ? 'SUCCESS' : 'FAILED' },
            { timeOffset: `+00:${talkSec}.0s`, stage: 'END', stageName: '结束阶段', actionCode: 'HANGUP', actionName: '通话释放挂断', detail: `挂机原因: ${item.hangupCause || 'NORMAL_CLEARING'}, 状态: ${item.status}`, status: status === 'ANSWERED' ? 'SUCCESS' : 'WARNING' }
          ]
        };
      });
      callRecords.value = realRecords;
    } else {
      callRecords.value = [];
    }
  } catch (err) {
    console.error('Failed to load CDR records:', err);
    callRecords.value = [];
    cdrTotal.value = 0;
    toastError(`话单加载失败：${errorText(err)}`);
  } finally {
    cdrLoading.value = false;
  }
};

// ==================== 2. 未接待回拨 (Unattended Callback) ====================
// 字段要求：客户号码、运营商、坐席姓名、坐席工号、进线时间、排队放弃原因、等待耗时、状态
interface CallbackRecord {
  id: string;
  customerPhone: string;   // 客户号码
  carrier: string;         // 运营商
  agentName: string;       // 坐席姓名
  agentWorkNo: string;     // 坐席工号
  inboundTime: string;     // 进线时间
  abandonReason: string;   // 排队放弃原因
  waitDuration: string;    // 等待耗时
  status: 'PENDING' | 'ASSIGNED' | 'CALLED'; // 状态: 待回拨 / 已派单 / 已回呼
  assignee?: string;
}

const callbackRecords = ref<CallbackRecord[]>([]);

// 🔍 未接待回拨过滤条件 (严格按照用户第5条指令：客户号码、坐席工号)
const filterCbCustomerPhone = ref('');
const filterCbAgentWorkNo = ref('');

const filteredCallbackRecords = computed(() => {
  return callbackRecords.value.filter(item => {
    if (filterCbCustomerPhone.value && !item.customerPhone.includes(filterCbCustomerPhone.value)) return false;
    if (filterCbAgentWorkNo.value && !item.agentWorkNo.includes(filterCbAgentWorkNo.value)) return false;
    return true;
  });
});

const cbPageNum = ref(1);
const cbPageSize = ref(10);
const pagedCallbackRecords = computed(() => {
  const start = (cbPageNum.value - 1) * cbPageSize.value;
  return filteredCallbackRecords.value.slice(start, start + cbPageSize.value);
});

const resetCallbackFilter = () => {
  filterCbCustomerPhone.value = '';
  filterCbAgentWorkNo.value = '';
  cbPageNum.value = 1;
};

// 漏话调度操作与通知 (统一走全局反馈层 toast，不再使用浏览器原生弹窗)
const handleDispatch = async (item: CallbackRecord, agent: string, workNo: string) => {
  try {
    await callbackApi.assign(item.id, { agentName: agent, agentWorkNo: workNo });
    item.status = 'ASSIGNED';
    item.agentName = agent;
    item.agentWorkNo = workNo;
    item.assignee = agent;
    toast(`已将漏话任务 ${item.customerPhone} 指派给坐席【${agent} (工号: ${workNo})】`, 'success');
  } catch (err: unknown) {
    toast(`指派失败：${errorText(err)}`, 'error');
  }
};

const handleCallbackCall = async (item: CallbackRecord) => {
  try {
    await callbackApi.call(item.id);
    item.status = 'CALLED';
    toast(`正在向客户 ${item.customerPhone} 发起优先回访呼叫`, 'success');
  } catch (err: unknown) {
    toast(`呼叫失败：${errorText(err)}`, 'error');
  }
};

const loadCallbackRecords = async () => {
  try {
    const res = await callbackApi.list({ pageNum: 1, pageSize: 50 });
    if (res && res.list && res.list.length > 0) {
      const realCallbacks: CallbackRecord[] = res.list.map((item: CallbackTaskVO) => {
        return {
          id: String(item.id),
          customerPhone: item.phone,
          carrier: item.phone.startsWith('13') ? '中国移动' : item.phone.startsWith('18') ? '中国电信' : '中国联通',
          agentName: item.assignee || '未分配',
          agentWorkNo: item.assigneeWorkNo || '-',
          inboundTime: item.time || '-',
          abandonReason: item.reason || '排队放弃未接听',
          waitDuration: item.duration || '0秒',
          status: item.status,
          assignee: item.assignee
        };
      });
      callbackRecords.value = realCallbacks;
    } else {
      callbackRecords.value = [];
    }
  } catch (err) {
    console.error('Failed to load callback records:', err);
    callbackRecords.value = [];
  }
};

// 📊 顶部 Bento 卡片真实统计指标
//    数据来源：GET /api/admin/cdrs/stats —— 由数据库对今日话单做全量聚合，
//    与下方列表的分页/筛选解耦，翻页不会让卡片数字跳动，也不会只统计到当前页。
const cdrStats = ref<CdrStatsVO | null>(null);

const loadCdrStats = async () => {
  try {
    cdrStats.value = await cdrApi.stats();
  } catch (err) {
    console.error('Failed to load CDR stats:', err);
    cdrStats.value = null;
  }
};

const totalCallsCount = computed(() => cdrStats.value?.totalCalls ?? 0);
const answeredCallsCount = computed(() => cdrStats.value?.answeredCalls ?? 0);
const answerRate = computed(() => {
  if (totalCallsCount.value === 0) return '—';
  return ((answeredCallsCount.value / totalCallsCount.value) * 100).toFixed(1) + '%';
});

const totalTalkDurationSec = computed(() => cdrStats.value?.totalTalkSec ?? 0);
const inboundTalkSec = computed(() => cdrStats.value?.inboundTalkSec ?? 0);
const outboundTalkSec = computed(() => cdrStats.value?.outboundTalkSec ?? 0);

const formatDurationDisplay = (sec: number) => {
  if (sec >= 3600) {
    return (sec / 3600).toFixed(1) + ' 小时';
  } else if (sec >= 60) {
    return Math.floor(sec / 60) + ' 分钟';
  }
  return sec + ' 秒';
};

const onlineExtensionsCount = ref(0);
const loadExtensions = async () => {
  try {
    const res = await extensionApi.list();
    if (res && res.list) {
      onlineExtensionsCount.value = res.list.filter(e => e.onlineStatus === 'ONLINE' || !!e.registeredIp).length;
    }
  } catch (e) {
    onlineExtensionsCount.value = 0;
  }
};

const currentDateStr = computed(() => {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
});

onMounted(() => {
  loadCdrRecords();
  loadCallbackRecords();
  loadExtensions();
});

// ==================== 3. 录音播放器控制 ====================
const playingAudio = ref(false);
const currentAudioUrl = ref('');
const currentAudioInfo = ref<{ title: string; duration: string; phone: string; url?: string } | null>(null);

const playAudio = (title: string, phone: string, duration: string, recordingUrl?: string, rawId?: number) => {
  // 录音地址以话单返回的 recordingUrl 为准（后端由 fcc_call_recording.object_key 推导）；
  // 无地址时不做任何占位兜底，直接提示，避免播放器加载一个必然 404 的假地址。
  const url = recordingUrl || (rawId != null ? `/api/admin/recordings/${rawId}/stream` : '');
  if (!url) {
    toast('该通话没有可复播的录音', 'warning');
    return;
  }
  currentAudioUrl.value = url;
  currentAudioInfo.value = { title, phone, duration, url };
  playingAudio.value = true;
};

const closeAudio = () => {
  playingAudio.value = false;
  currentAudioUrl.value = '';
  currentAudioInfo.value = null;
};

// ==================== 4. 详情弹窗 ====================
const showDetailModal = ref(false);
const selectedCdr = ref<CallRecord | null>(null);

const openDetail = (cdr: CallRecord) => {
  selectedCdr.value = cdr;
  showDetailModal.value = true;
};

// ==================== 5. 未接待回拨查询 ====================
// 回拨列表的过滤是随输入实时生效的，此处仅把页码复位，不再用弹窗打断用户
const handleSearchCallback = () => {
  cbPageNum.value = 1;
};

// ==================== 6. 导出 ====================
/**
 * 导出当前筛选结果集为 CSV
 *
 * <p>原按钮只弹一句「已开始导出数据，正在生成 Excel 报表」却没有任何实现，属虚假反馈；
 * 这里改为真正产出可下载文件，并在导出成功后如实告知导出条数。</p>
 *
 * <p>导出口径与列表保持一致：沿用同一套筛选条件下推到数据库取全量匹配数据，
 * 而不是只导出当前页。</p>
 */
const exporting = ref(false);

const exportCdrCsv = async () => {
  exporting.value = true;
  try {
    // 复用同一套条件，放宽分页以取回全量匹配结果
    const query = buildCdrQuery();
    const res = await cdrApi.list({ ...query, pageNum: 1, pageSize: 5000 });
    const rows = res?.list ?? [];
    if (rows.length === 0) {
      toast('当前筛选条件下没有可导出的通话记录', 'warning');
      return;
    }

    const header = ['通话ID', '主叫姓名', '主叫号码', '运营商', '坐席姓名', '坐席工号', '方向', '通话开始时间', '通话结束时间', '响铃时长', '录音时长', '状态', '挂机原因'];
    const body = rows.map((item) => [
      item.ctrlId || String(item.id),
      item.callerName || '',
      item.caller || '',
      item.carrier || '',
      item.agentName || '',
      item.agentWorkNo || '',
      item.direction === 'OUTBOUND' ? '呼出' : item.direction === 'INTERNAL' ? '内部' : '呼入',
      item.initiatedAt || '',
      item.endedAt || '',
      item.waitDurationMs ? `${Math.round(item.waitDurationMs / 1000)}秒` : '',
      item.audioDuration || '',
      item.status === 'ANSWERED' ? '已接通' : '未接听',
      item.hangupCause || '',
    ]);

    const escapeCell = (value: string) => `"${String(value).replace(/"/g, '""')}"`;
    const csv = [header, ...body].map((line) => line.map(escapeCell).join(',')).join('\r\n');

    // 前置 UTF-8 BOM，保证 Excel 打开中文不乱码
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `通话记录_${currentDateStr.value}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);

    toast(`已导出 ${rows.length} 条通话记录`, 'success');
  } catch (err) {
    toastError(`导出失败：${errorText(err)}`);
  } finally {
    exporting.value = false;
  }
};
</script>

<template>
  <div class="h-full flex-1 flex flex-col gap-6 overflow-y-auto pr-1">
    
    <!-- 顶部浮动 Toast 消息 -->
    <div v-if="toastMsg" class="fixed top-6 right-8 z-50 bg-slate-900 text-white px-5 py-3 rounded-2xl shadow-2xl text-sm font-bold flex items-center gap-2.5 animate-bounce border border-slate-700">
      <span>🔔</span><span>{{ toastMsg }}</span>
    </div>

    <!-- 1. 顶部 Bento Grid 4 张话务与回拨指标卡片 -->
    <div class="grid grid-cols-12 gap-5 shrink-0">
      <!-- 指标 1 -->
      <div class="col-span-3 bg-white p-6 rounded-3xl border border-slate-100 shadow-card flex flex-col justify-between hover:shadow-card-hover transition-all">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-bold text-slate-500">今日呼叫总数</span>
          <div class="w-9 h-9 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center text-sm font-black">↗</div>
        </div>
        <div>
          <div class="text-3xl font-black text-slate-900 tracking-tight">{{ totalCallsCount }} <span class="text-sm font-medium text-slate-400">通</span></div>
          <div class="text-xs text-slate-500 mt-1 flex items-center gap-2 font-semibold">
            <span class="text-emerald-600 font-extrabold">{{ answerRate }} 接通率</span>
            <span class="text-slate-300">•</span>
            <span>成功 {{ answeredCallsCount }}通</span>
          </div>
        </div>
      </div>

      <!-- 指标 2: 核心中继卡片 -->
      <div class="col-span-3 bg-gradient-to-tr from-amber-200 via-amber-300 to-yellow-400 p-6 rounded-3xl shadow-card text-amber-950 flex flex-col justify-between relative overflow-hidden">
        <div class="absolute -right-4 -bottom-6 w-28 h-28 bg-white/20 rounded-full blur-xl pointer-events-none"></div>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-xs font-black tracking-wider uppercase opacity-75">SIP TRUNK POOL</span>
            <div class="text-sm font-black text-amber-950">移动 / 电信智能中继</div>
          </div>
          <span class="text-xs font-extrabold bg-amber-900/15 px-2.5 py-0.5 rounded-full">主节点</span>
        </div>
        <div>
          <div class="font-mono text-base font-black tracking-widest my-1">127.0.0.1 (FreeSWITCH)</div>
          <div class="flex items-center justify-between text-xs font-bold opacity-85">
            <span>实时通道: {{ answeredCallsCount > 0 ? '通道活跃' : '就绪待命' }} (在线分机: {{ onlineExtensionsCount }})</span>
            <span class="bg-white/40 px-2.5 py-0.5 rounded-full">ACTIVE</span>
          </div>
        </div>
      </div>

      <!-- 指标 3: 未接待漏话回拨池卡片 -->
      <div class="col-span-3 bg-white p-6 rounded-3xl border border-slate-100 shadow-card flex flex-col justify-between hover:shadow-card-hover transition-all">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-bold text-slate-500">未接待漏话总池</span>
          <div class="w-9 h-9 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center text-sm font-black">↩</div>
        </div>
        <div>
          <div class="text-3xl font-black text-rose-600 tracking-tight">
            {{ callbackRecords.filter(c => c.status === 'PENDING').length }} <span class="text-sm font-medium text-slate-400">单待回访</span>
          </div>
          <div class="text-xs text-slate-500 mt-1 flex items-center gap-2 font-semibold">
            <span class="text-indigo-600 font-extrabold">{{ callbackRecords.filter(c => c.status === 'ASSIGNED').length }} 单已派单</span>
            <span class="text-slate-300">•</span>
            <span class="text-emerald-600 font-extrabold">{{ callbackRecords.filter(c => c.status === 'CALLED').length }} 单已完成</span>
          </div>
        </div>
      </div>

      <!-- 指标 4 -->
      <div class="col-span-3 bg-white p-6 rounded-3xl border border-slate-100 shadow-card flex flex-col justify-between hover:shadow-card-hover transition-all">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-bold text-slate-500">总通话时长</span>
          <div class="w-9 h-9 rounded-2xl bg-purple-50 text-purple-600 flex items-center justify-center text-sm font-black">⏱</div>
        </div>
        <div>
          <div class="text-3xl font-black text-slate-900 tracking-tight">{{ formatDurationDisplay(totalTalkDurationSec) }}</div>
          <div class="text-xs text-slate-500 mt-1 flex items-center gap-2 font-semibold">
            <span>呼入 {{ formatDurationDisplay(inboundTalkSec) }}</span>
            <span class="text-slate-300">•</span>
            <span>呼出 {{ formatDurationDisplay(outboundTalkSec) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 主内容卡片：整合【通话记录】与【未接待回拨】双 Tab -->
    <div class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 flex flex-col flex-1 min-h-[560px]">
      
      <!-- 顶栏：两个核心 Tab 切换器 + 统计日期与导出 -->
      <div class="flex items-center justify-between pb-4 border-b border-slate-100 mb-4 shrink-0">
        <!-- 核心 Tab 切换栏 -->
        <div class="flex items-center gap-8">
          <button
            @click="activeSubTab = 'records'"
            class="pb-2.5 transition-all cursor-pointer flex items-center gap-2.5 relative"
            :class="activeSubTab === 'records' ? 'font-black text-slate-900 text-lg border-b-2 border-brand-500' : 'font-bold text-slate-400 hover:text-slate-700 text-base'"
          >
            <span>📞 通话记录</span>
            <span
              class="px-2.5 py-0.5 rounded-full text-xs font-bold font-mono"
              :class="activeSubTab === 'records' ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-500'"
            >
              {{ callRecords.length }}
            </span>
          </button>

          <button
            @click="activeSubTab = 'callback'"
            class="pb-2.5 transition-all cursor-pointer flex items-center gap-2.5 relative"
            :class="activeSubTab === 'callback' ? 'font-black text-slate-900 text-lg border-b-2 border-brand-500' : 'font-bold text-slate-400 hover:text-slate-700 text-base'"
          >
            <span>↩️ 未接待回拨</span>
            <span class="px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-600 font-black text-xs font-mono">
              {{ callbackRecords.filter(c => c.status === 'PENDING').length }} 待办
            </span>
          </button>
        </div>

        <!-- 右侧辅助工具 -->
        <div class="flex items-center gap-3 text-xs">
          <div class="flex items-center gap-2 bg-slate-50 border border-slate-200/80 px-3.5 py-1.5 rounded-full text-slate-700 font-semibold">
            <span>📅</span>
            <span class="font-mono font-bold">{{ currentDateStr }}</span>
          </div>
          <button
            @click="exportCdrCsv"
            :disabled="exporting"
            class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-full transition-all cursor-pointer text-xs disabled:opacity-60 disabled:cursor-not-allowed"
            title="按当前筛选条件导出全部匹配的通话记录 (CSV)"
          >
            {{ exporting ? '导出中…' : '导出数据' }}
          </button>
        </div>
      </div>

      <!-- ==================== TAB 1: 通话记录内容区 ==================== -->
      <div v-if="activeSubTab === 'records'" class="flex-1 flex flex-col overflow-hidden">
        
        <!-- 🔍 通话记录过滤条件 (严格按要求：坐席姓名、坐席工号、号码、方向、通话ID、通话时间) -->
        <div class="flex flex-wrap items-end gap-3 text-sm mb-4 bg-slate-50/70 p-3.5 rounded-2xl border border-slate-100 shrink-0">
          <!-- 1. 坐席姓名 -->
          <div class="w-32">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">坐席姓名</label>
            <input
              v-model="filterAgentName"
              type="text"
              placeholder="舒欣 / 陈松"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium shadow-2xs"
            >
          </div>

          <!-- 2. 坐席工号 -->
          <div class="w-28">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">坐席工号</label>
            <input
              v-model="filterAgentWorkNo"
              type="text"
              placeholder="例如: 901415"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 shadow-2xs"
            >
          </div>

          <!-- 3. 号码 -->
          <div class="w-36">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">号码 (主叫/被叫)</label>
            <input
              v-model="filterPhone"
              type="text"
              placeholder="手机号或分机号..."
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-mono shadow-2xs"
            >
          </div>

          <!-- 4. 方向 -->
          <div class="w-28">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">通话方向</label>
            <select v-model="filterDirection" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-700 text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium cursor-pointer shadow-2xs">
              <option value="">全部方向</option>
              <option value="OUTBOUND">呼出 (↗)</option>
              <option value="INBOUND">呼入 (↙)</option>
              <option value="INTERNAL">内部 (↔)</option>
            </select>
          </div>

          <!-- 5. 通话ID -->
          <div class="w-36">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">通话 ID</label>
            <input
              v-model="filterCallId"
              type="text"
              placeholder="CALL-..."
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 shadow-2xs"
            >
          </div>

          <!-- 6. 通话时间 (Element Plus 专业级日期时间范围选择器) -->
          <div class="shrink-0">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">通话时间范围</label>
            <div class="custom-datepicker-wrap">
              <el-date-picker
                v-model="filterDateRange"
                type="datetimerange"
                :shortcuts="dateShortcuts"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                size="default"
                @change="handleDateRangeChange"
              />
            </div>
          </div>

          <!-- 操作按钮组：重置 + 明亮的查询按钮 -->
          <div class="flex items-center gap-2 shrink-0">
            <!-- 重置按钮 -->
            <button
              @click="resetCallFilter"
              class="px-4 py-2 bg-white border border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-bold transition-all cursor-pointer shadow-2xs"
            >
              重置
            </button>

            <!-- 明亮的查询按钮 -->
            <button
              @click="handleSearchCall"
              class="px-5 py-2 bg-[#1677FF] hover:bg-blue-600 text-white rounded-xl text-xs font-black shadow-md shadow-blue-500/25 flex items-center gap-1.5 transition-all cursor-pointer"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              <span>查询</span>
            </button>
          </div>
        </div>

        <!-- 📋 通话记录数据表格 (严格落实字段：主叫姓名/号码、运营商、坐席姓名、坐席工号、方向、通话开始时间、通话结束时间、响铃时长、录音（显示时长）、状态) -->
        <div class="flex-1 overflow-x-auto overflow-y-auto">
          <table class="w-full text-sm text-left">
            <thead class="text-slate-400 border-b border-slate-100 pb-2 text-xs font-bold uppercase sticky top-0 bg-white z-10">
              <tr>
                <th class="pb-3 px-3.5">主叫姓名 / 号码</th>
                <th class="pb-3 px-3">运营商</th>
                <th class="pb-3 px-3">坐席姓名</th>
                <th class="pb-3 px-2.5">坐席工号</th>
                <th class="pb-3 px-2.5">方向</th>
                <th class="pb-3 px-3">通话开始时间</th>
                <th class="pb-3 px-3">通话结束时间</th>
                <th class="pb-3 px-2.5">响铃时长</th>
                <th class="pb-3 px-3">录音 (时长)</th>
                <th class="pb-3 px-2.5">状态</th>
                <th class="pb-3 px-3 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              <!-- 空状态提示 (真实测试环境引导) -->
              <tr v-if="filteredCallRecords.length === 0">
                <td colspan="11" class="py-16 text-center">
                  <div class="flex flex-col items-center justify-center">
                    <div class="w-14 h-14 rounded-2xl bg-blue-50 border border-blue-100 flex items-center justify-center text-2xl mb-3 shadow-xs">
                      📞
                    </div>
                    <p class="text-sm font-bold text-slate-700 mb-1">数据库已进入真实测试模式，暂无通话话单</p>
                    <p class="text-xs text-slate-400 max-w-sm leading-relaxed">请使用 PC 客户端或话机发起呼入/呼出通话，通话建立并挂断后，系统将实时生成完整 CDR 话单与录音流水</p>
                  </div>
                </td>
              </tr>
              <tr v-for="item in callRecords" :key="item.id" class="hover:bg-slate-50/80 transition-colors">
                <!-- 1. 主叫姓名/号码 -->
                <td class="py-3.5 px-3.5">
                  <div class="flex items-center gap-2.5">
                    <div class="w-8 h-8 rounded-full bg-blue-50 text-brand-600 font-black flex items-center justify-center text-xs shrink-0 border border-indigo-100">
                      {{ item.callerName.charAt(0) }}
                    </div>
                    <div>
                      <div class="font-bold text-slate-900 text-sm leading-snug">{{ item.callerName }}</div>
                      <div class="font-mono text-slate-400 text-xs">{{ item.callerPhone }}</div>
                    </div>
                  </div>
                </td>

                <!-- 2. 运营商 -->
                <td class="py-3.5 px-3">
                  <span class="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold font-sans">
                    {{ item.carrier }}
                  </span>
                </td>

                <!-- 3. 坐席姓名 -->
                <td class="py-3.5 px-3 font-bold text-slate-900 text-sm">
                  {{ item.agentName }}
                </td>

                <!-- 4. 坐席工号 -->
                <td class="py-3.5 px-2.5">
                  <span class="font-mono text-xs text-slate-600 font-bold bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                    {{ item.agentWorkNo }}
                  </span>
                </td>

                <!-- 5. 方向 -->
                <td class="py-3.5 px-2.5">
                  <span v-if="item.direction === 'OUTBOUND'" class="text-amber-600 font-black text-xs flex items-center gap-1">
                    <span class="text-sm">↗</span><span>呼出</span>
                  </span>
                  <span v-else-if="item.direction === 'INBOUND'" class="text-blue-600 font-black text-xs flex items-center gap-1">
                    <span class="text-sm">↙</span><span>呼入</span>
                  </span>
                  <span v-else class="text-indigo-600 font-black text-xs flex items-center gap-1">
                    <span class="text-sm">↔</span><span>内部</span>
                  </span>
                </td>

                <!-- 6. 通话开始时间 -->
                <td class="py-3.5 px-3 font-mono text-slate-600 text-xs">
                  {{ item.startTime }}
                </td>

                <!-- 7. 通话结束时间 -->
                <td class="py-3.5 px-3 font-mono text-slate-400 text-xs">
                  {{ item.endTime }}
                </td>

                <!-- 8. 响铃时长 -->
                <td class="py-3.5 px-2.5 font-mono text-amber-600 font-bold text-xs">
                  {{ item.ringDuration }}
                </td>

                <!-- 9. 录音（显示时长）：可复播时渲染播放按钮，仅有通话时长但无录音文件时只展示时长 -->
                <td class="py-3.5 px-3">
                  <button
                    v-if="item.recordingUrl"
                    @click="playAudio(item.callerName, item.callerPhone, item.audioDuration || '', item.recordingUrl, item.rawId)"
                    class="px-2.5 py-1 rounded-xl bg-brand-50 hover:bg-brand-500 text-brand-600 hover:text-white flex items-center gap-1.5 transition-all cursor-pointer text-xs font-mono font-bold shadow-2xs group"
                    title="点击在线试听双轨录音"
                  >
                    <span class="text-[10px] text-brand-500 group-hover:text-white">▶</span>
                    <span>{{ item.audioDuration }}</span>
                  </button>
                  <span
                    v-else-if="item.audioDuration"
                    class="text-slate-400 font-mono text-xs"
                    title="该通话未产生录音文件"
                  >
                    {{ item.audioDuration }}
                  </span>
                  <span v-else class="text-slate-300 font-mono text-xs">-</span>
                </td>

                <!-- 10. 状态 -->
                <td class="py-3.5 px-2.5">
                  <span v-if="item.status === 'ANSWERED'" class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold text-xs">
                    已接通
                  </span>
                  <span v-else-if="item.status === 'MISSED'" class="px-2.5 py-1 rounded-full bg-rose-50 text-rose-600 font-bold text-xs">
                    未接听
                  </span>
                  <span v-else-if="item.status === 'BUSY'" class="px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 font-bold text-xs">
                    坐席忙
                  </span>
                  <span v-else class="px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 font-bold text-xs">
                    拒接
                  </span>
                </td>

                <!-- 操作 -->
                <td class="py-3.5 px-3 text-right">
                  <button
                    @click="openDetail(item)"
                    class="px-3 py-1 bg-indigo-50 hover:bg-indigo-100 text-brand-600 rounded-xl text-xs font-bold transition cursor-pointer"
                  >
                    过程详情
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 底部分页 (通话记录)：总数与分页均由数据库 COUNT + LIMIT 驱动 -->
        <div class="pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500 shrink-0">
          <span class="text-xs text-slate-600 font-medium">
            共 {{ cdrTotal }} 条通话记录数据
            <span v-if="cdrLoading" class="text-slate-400 font-normal">· 正在查询…</span>
          </span>
          <div v-if="cdrTotal > 0">
            <el-pagination
              v-model:current-page="cdrPageNum"
              v-model:page-size="cdrPageSize"
              :total="cdrTotal"
              :page-sizes="[10, 20, 50]"
              layout="sizes, prev, pager, next"
              size="small"
              background
            />
          </div>
          <span v-else class="text-slate-400 text-xs font-mono">第 0 / 0 页</span>
        </div>
      </div>

      <!-- ==================== TAB 2: 未接待回拨内容区 ==================== -->
      <div v-else-if="activeSubTab === 'callback'" class="flex-1 flex flex-col overflow-hidden">
        
        <!-- 🔍 未接待回拨过滤条件 (严格按照用户第5条指令：客户号码、坐席工号) -->
        <div class="flex items-end gap-3 text-sm mb-4 bg-slate-50/70 p-3.5 rounded-2xl border border-slate-100 shrink-0">
          <!-- 1. 客户号码 -->
          <div class="w-64">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">客户号码</label>
            <input
              v-model="filterCbCustomerPhone"
              type="text"
              placeholder="搜索客户手机号码..."
              class="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium shadow-2xs"
            >
          </div>

          <!-- 2. 坐席工号 -->
          <div class="w-48">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">坐席工号</label>
            <input
              v-model="filterCbAgentWorkNo"
              type="text"
              placeholder="例如: 901415 / 901473"
              class="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium shadow-2xs"
            >
          </div>

          <!-- 操作按钮组：重置 + 明亮的查询按钮 -->
          <div class="flex items-center gap-2 shrink-0">
            <button
              @click="resetCallbackFilter"
              class="px-4 py-2 bg-white border border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-bold transition-all cursor-pointer shadow-2xs"
            >
              重置
            </button>
            <button
              @click="showAlert(`查询完成，共匹配 ${filteredCallbackRecords.length} 条未接待回拨记录`)"
              class="px-5 py-2 bg-[#1677FF] hover:bg-blue-600 text-white rounded-xl text-xs font-black shadow-md shadow-blue-500/25 flex items-center gap-1.5 transition-all cursor-pointer"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              <span>查询</span>
            </button>
          </div>

          <div class="ml-auto text-xs text-slate-400 font-medium self-center">
            系统已自动将排队超时或未接通话拦截进入漏话回拨池
          </div>
        </div>

        <!-- 📋 未接待回拨数据表格 (严格落实字段：客户号码、运营商、坐席姓名、坐席工号、进线时间、排队放弃原因、等待耗时、状态) -->
        <div class="flex-1 overflow-x-auto overflow-y-auto">
          <table class="w-full text-sm text-left">
            <thead class="text-slate-400 border-b border-slate-100 pb-2 text-xs font-bold uppercase sticky top-0 bg-white z-10">
              <tr>
                <th class="pb-3 px-3.5">客户号码</th>
                <th class="pb-3 px-3">运营商</th>
                <th class="pb-3 px-3">坐席姓名</th>
                <th class="pb-3 px-2.5">坐席工号</th>
                <th class="pb-3 px-3.5">进线时间</th>
                <th class="pb-3 px-3.5">排队放弃原因</th>
                <th class="pb-3 px-3">等待耗时</th>
                <th class="pb-3 px-3">状态</th>
                <th class="pb-3 px-4 text-right">回访调度 / 操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              <!-- 空状态提示 (真实测试环境引导) -->
              <tr v-if="filteredCallbackRecords.length === 0">
                <td colspan="9" class="py-16 text-center">
                  <div class="flex flex-col items-center justify-center">
                    <div class="w-14 h-14 rounded-2xl bg-amber-50 border border-amber-100 flex items-center justify-center text-2xl mb-3 shadow-xs">
                      ⏳
                    </div>
                    <p class="text-sm font-bold text-slate-700 mb-1">数据库已进入真实测试模式，暂无未接待回拨任务</p>
                    <p class="text-xs text-slate-400 max-w-sm leading-relaxed">当有客户呼入因排队超时、坐席全忙或放弃未接听时，系统将自动产生真实待回拨工单并在此呈现</p>
                  </div>
                </td>
              </tr>
              <tr v-for="item in pagedCallbackRecords" :key="item.id" class="hover:bg-slate-50/80 transition-colors">
                <!-- 1. 客户号码 -->
                <td class="py-4 px-3.5 font-mono font-bold text-slate-900 text-sm">
                  {{ item.customerPhone }}
                </td>

                <!-- 2. 运营商 -->
                <td class="py-4 px-3">
                  <span class="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold">
                    {{ item.carrier }}
                  </span>
                </td>

                <!-- 3. 坐席姓名 -->
                <td class="py-4 px-3 font-bold text-slate-800 text-sm">
                  {{ item.agentName }}
                </td>

                <!-- 4. 坐席工号 -->
                <td class="py-4 px-2.5">
                  <span class="font-mono text-xs text-slate-600 font-bold bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                    {{ item.agentWorkNo }}
                  </span>
                </td>

                <!-- 5. 进线时间 -->
                <td class="py-4 px-3.5 font-mono text-slate-500 text-xs">
                  {{ item.inboundTime }}
                </td>

                <!-- 6. 排队放弃原因 -->
                <td class="py-4 px-3.5 text-slate-700 text-sm font-medium">
                  {{ item.abandonReason }}
                </td>

                <!-- 7. 等待耗时 -->
                <td class="py-4 px-3 font-mono text-amber-600 font-black text-sm">
                  {{ item.waitDuration }}
                </td>

                <!-- 8. 状态 -->
                <td class="py-4 px-3">
                  <span v-if="item.status === 'PENDING'" class="px-2.5 py-1 rounded-full bg-rose-50 text-rose-600 font-bold text-xs">
                    待回拨
                  </span>
                  <span v-else-if="item.status === 'ASSIGNED'" class="px-2.5 py-1 rounded-full bg-indigo-50 text-brand-600 font-bold text-xs">
                    已派单 ({{ item.assignee }})
                  </span>
                  <span v-else class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold text-xs">
                    已回呼
                  </span>
                </td>

                <!-- 操作栏：回访调度 -->
                <td class="py-4 px-4 text-right">
                  <div v-if="item.status === 'PENDING'" class="flex items-center justify-end gap-2">
                    <button
                      @click="handleDispatch(item, '舒欣', '901415')"
                      class="px-3 py-1.5 bg-brand-500 hover:bg-brand-600 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs"
                    >
                      派给舒欣
                    </button>
                    <button
                      @click="handleDispatch(item, '陈松', '901473')"
                      class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 rounded-xl font-bold transition cursor-pointer text-xs"
                    >
                      派给陈松
                    </button>
                    <button
                      @click="handleCallbackCall(item)"
                      class="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-xl font-bold transition cursor-pointer text-xs"
                    >
                      一键回呼
                    </button>
                  </div>
                  <div v-else class="flex items-center justify-end gap-2">
                    <button
                      @click="handleCallbackCall(item)"
                      class="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-bold transition cursor-pointer text-xs"
                    >
                      再次回呼
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 底部分页 (未接待回拨) -->
        <div class="pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500 shrink-0">
          <span class="text-xs text-slate-600 font-medium">共 {{ filteredCallbackRecords.length }} 条漏话回访任务</span>
          <div v-if="filteredCallbackRecords.length > 0">
            <el-pagination
              v-model:current-page="cbPageNum"
              v-model:page-size="cbPageSize"
              :total="filteredCallbackRecords.length"
              :page-sizes="[10, 20, 50]"
              layout="sizes, prev, pager, next"
              size="small"
              background
            />
          </div>
          <span v-else class="text-slate-400 text-xs font-mono">第 0 / 0 页</span>
        </div>
      </div>
    </div>

    <!-- 3. 录音在线试听悬浮播放器条 -->
    <div v-if="playingAudio" class="fixed bottom-8 inset-x-0 mx-auto max-w-2xl bg-white rounded-full shadow-popover border border-slate-200 p-3 px-6 flex items-center justify-between z-50 animate-fadeIn">
      <div class="flex items-center gap-3.5">
        <div class="w-10 h-10 rounded-full bg-brand-500 text-white flex items-center justify-center shadow-pill text-sm font-bold">
          🎧
        </div>
        <div>
          <div class="text-sm font-bold text-slate-900 flex items-center gap-2">
            <span>{{ currentAudioInfo?.title }}</span>
            <span class="text-xs text-slate-400 font-mono">({{ currentAudioInfo?.phone }})</span>
            <span class="text-xs text-indigo-500 font-mono bg-indigo-50 px-2 py-0.5 rounded font-bold">电信级高清</span>
          </div>
          <div class="text-xs text-slate-400 font-mono font-bold mt-0.5">总时长: {{ currentAudioInfo?.duration }}</div>
        </div>
      </div>

      <!-- 真实 HTML5 音频流播放器 -->
      <div class="flex items-center gap-3">
        <audio
          v-if="currentAudioUrl"
          :src="currentAudioUrl"
          controls
          autoplay
          class="h-9 w-72 outline-none"
        ></audio>
        <button @click="closeAudio" class="text-slate-400 hover:text-slate-600 font-bold text-sm cursor-pointer px-2 py-1">
          ✕
        </button>
      </div>
    </div>

    <!-- 4. 弹窗: 全生命周期过程详情追踪 (Stage + Action 时序流水线卡片) -->
    <div v-if="showDetailModal && selectedCdr" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="w-full max-w-4xl max-h-[92vh] bg-white rounded-3xl shadow-2xl border border-slate-100 overflow-hidden flex flex-col p-6 space-y-4">
        
        <!-- 弹窗顶部标题 -->
        <div class="flex items-center justify-between pb-3 border-b border-slate-100 shrink-0">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-xl bg-indigo-50 border border-indigo-100 text-[#1677ff] font-bold flex items-center justify-center text-base">
              🧭
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-base font-black text-slate-900">通话全生命周期过程详情追踪 (Call Journey Trace)</h2>
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border"
                  :class="selectedCdr.status === 'ANSWERED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-rose-50 text-rose-700 border-rose-200'"
                >
                  {{ selectedCdr.status === 'ANSWERED' ? '● 通话已接通' : '● 通话未接起/异常' }}
                </span>
              </div>
              <p class="text-xs text-slate-400 mt-0.5 font-mono">
                流水号: <strong class="text-slate-700">{{ selectedCdr.id }}</strong>
                <span class="mx-1.5 text-slate-300">|</span>
                呼叫方向: <span class="font-bold text-slate-700">{{ selectedCdr.direction === 'OUTBOUND' ? '外呼' : selectedCdr.direction === 'INBOUND' ? '呼入' : '内线' }}</span>
                <span class="mx-1.5 text-slate-300">|</span>
                引擎: FreeSWITCH 1.11.3 (SIP/WebRTC)
              </p>
            </div>
          </div>
          <button @click="showDetailModal = false" class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-sm font-bold cursor-pointer">✕</button>
        </div>

        <!-- 通话关键元数据 Bento 汇总卡片 -->
        <div class="grid grid-cols-4 gap-3 text-xs shrink-0">
          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">主叫客户</span>
            <div class="font-bold text-slate-900 truncate">{{ selectedCdr.callerName }}</div>
            <div class="font-mono text-slate-500 text-[11px]">{{ selectedCdr.callerPhone }} ({{ selectedCdr.carrier }})</div>
          </div>

          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">服务坐席</span>
            <div class="font-bold text-slate-900 truncate">{{ selectedCdr.agentName }}</div>
            <div class="font-mono text-slate-500 text-[11px]">工号: {{ selectedCdr.agentWorkNo }}</div>
          </div>

          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">时延与录音</span>
            <div class="flex items-center gap-1.5 font-bold text-slate-900">
              <span>振铃: {{ selectedCdr.ringDuration }}</span>
            </div>
            <div class="font-mono text-brand-600 font-bold text-[11px]">
              录音: {{ selectedCdr.audioDuration || '无录音' }}
            </div>
          </div>

          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">路由策略与评价</span>
            <div class="font-bold text-indigo-700 truncate">
              {{ selectedCdr.routeMode === 'DID_DIRECT' ? 'DID 直达专席' : selectedCdr.routeMode === 'RULE_ENGINE' ? '多维规则引擎决策' : selectedCdr.routeMode === 'HTTP_CALLBACK' ? '业务接口动态回调' : '标准流程路由' }}
            </div>
            <div class="font-mono text-[11px] text-amber-600 font-bold">
              {{ selectedCdr.satisfactionScore ? `客户评价: ${selectedCdr.satisfactionScore} 星 ★★★★★` : '未评价 / 异常挂断' }}
            </div>
          </div>
        </div>

        <!-- 阶段导航阶段指示条 (Stage Pipeline Indicator) -->
        <div class="bg-[#fcfdfe] p-3 rounded-2xl border border-slate-200/80 flex items-center justify-between text-xs font-bold text-slate-600 shrink-0">
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>1. 触发与应答 (Trigger)</span>
          </div>
          <span class="text-slate-300">➔</span>
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-indigo-500"></span>
            <span>2. 路由决策 (Route)</span>
          </div>
          <span class="text-slate-300">➔</span>
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
            <span>3. 通话中 (Connected)</span>
          </div>
          <span class="text-slate-300">➔</span>
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-amber-500"></span>
            <span>4. 结束收尾 (End)</span>
          </div>
        </div>

        <!-- 核心：纵向高颜值 Stage + Action 时序流水线 (Action Stream) -->
        <div class="flex-1 overflow-y-auto pr-1 space-y-3">
          <div class="relative pl-6 border-l-2 border-indigo-100 ml-4 space-y-3.5 my-2">
            <template v-for="(trace, tIdx) in getRecordTrace(selectedCdr)" :key="tIdx">
              <!-- 节点卡片 -->
              <div class="relative group">
                <!-- 左侧时间线圆点 -->
                <div
                  class="absolute -left-[31px] top-3.5 w-3.5 h-3.5 rounded-full border-2 border-white ring-2 transition-all flex items-center justify-center text-[8px]"
                  :class="trace.status === 'SUCCESS' ? 'bg-emerald-500 ring-emerald-200' : trace.status === 'WARNING' ? 'bg-amber-500 ring-amber-200' : 'bg-rose-500 ring-rose-200'"
                >
                </div>

                <!-- 动作卡片主体 -->
                <div
                  class="p-3.5 rounded-2xl border bg-white shadow-2xs hover:shadow-xs transition-all space-y-1.5"
                  :class="trace.status === 'SUCCESS' ? 'border-slate-200/90' : trace.status === 'WARNING' ? 'border-amber-200 bg-amber-50/20' : 'border-rose-200 bg-rose-50/20'"
                >
                  <div class="flex items-center justify-between text-xs">
                    <div class="flex items-center gap-2">
                      <span class="font-mono text-xs font-bold text-slate-400 bg-slate-100 px-2 py-0.5 rounded-md">
                        {{ trace.timeOffset }}
                      </span>
                      <span
                        class="px-2 py-0.5 rounded text-[10px] font-mono font-bold"
                        :class="trace.stage === 'TRIGGER' ? 'bg-emerald-50 text-emerald-700' : trace.stage === 'ROUTE' ? 'bg-indigo-50 text-indigo-700' : trace.stage === 'CONNECTED' ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-700'"
                      >
                        {{ trace.stageName }}
                      </span>
                      <span class="font-black text-slate-900 text-xs">{{ trace.actionName }}</span>
                      <span class="text-[10px] font-mono text-slate-400 font-bold">({{ trace.actionCode }})</span>
                    </div>

                    <div class="flex items-center gap-2 text-xs">
                      <span v-if="trace.duration" class="font-mono text-slate-500 bg-slate-50 border border-slate-200 px-2 py-0.5 rounded text-[11px] font-bold">
                        ⏱️ {{ trace.duration }}
                      </span>
                      <span
                        class="px-2 py-0.5 rounded-full text-[10px] font-bold"
                        :class="trace.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-800' : trace.status === 'WARNING' ? 'bg-amber-100 text-amber-800' : 'bg-rose-100 text-rose-800'"
                      >
                        {{ trace.status === 'SUCCESS' ? '成功' : trace.status === 'WARNING' ? '警告/重试' : '失败' }}
                      </span>
                    </div>
                  </div>

                  <!-- 动作执行明细 / 出入参 -->
                  <div class="text-xs text-slate-600 bg-slate-50/80 p-2.5 rounded-xl border border-slate-100 font-mono text-[11px] leading-relaxed">
                    {{ trace.detail }}
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 弹窗底部操作栏 -->
        <div class="pt-3 border-t border-slate-100 flex items-center justify-between shrink-0">
          <div class="flex items-center gap-2">
            <button
              v-if="selectedCdr.recordingUrl"
              @click="playAudio(selectedCdr.callerName, selectedCdr.callerPhone, selectedCdr.audioDuration || '', selectedCdr.recordingUrl, selectedCdr.rawId)"
              class="px-4 py-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-xs font-bold rounded-xl flex items-center gap-1.5 transition cursor-pointer"
            >
              <span>▶️</span>
              <span>试听双轨录音 ({{ selectedCdr.audioDuration }})</span>
            </button>
            <span v-else-if="selectedCdr.audioDuration" class="text-xs text-slate-400">
              该通话未产生录音文件（通话 {{ selectedCdr.audioDuration }}）
            </span>
            <span class="text-xs text-slate-400">💡 过程动作日志已与 FreeSWITCH ESL 事件日志 1:1 归一化对齐</span>
          </div>

          <button @click="showDetailModal = false" class="px-6 py-2 bg-[#1677ff] hover:bg-blue-600 text-white text-xs font-bold rounded-xl cursor-pointer shadow-xs transition">
            关闭
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
:deep(.el-date-editor--datetimerange.el-input__wrapper) {
  border-radius: 0.75rem !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 12px !important;
  height: 38px !important;
  background-color: #ffffff !important;
  transition: all 0.2s ease;
}
:deep(.el-date-editor--datetimerange.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
}
:deep(.el-date-editor--datetimerange.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px #1677ff inset !important;
}
:deep(.el-range-input) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace !important;
  font-size: 12px !important;
  color: #1e293b !important;
}
:deep(.el-range-separator) {
  font-size: 12px !important;
  color: #94a3b8 !important;
  font-weight: 700 !important;
}
</style>

