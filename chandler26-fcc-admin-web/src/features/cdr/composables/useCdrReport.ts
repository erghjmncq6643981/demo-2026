import { toCallRecord, type CallRecord } from '../model/callRecord';
import { ref, computed, watch, onMounted } from 'vue';
import { cdrApi, type CallCdrVO, type CallCdrQueryReq, type CdrStatsVO } from '../../../api/cdrApi';
import { callbackApi, type CallbackTaskVO } from '../../../api/callbackApi';
import { extensionApi } from '../../extensions/api/extensionApi';
import { toast, toastError, errorText } from '../../../utils/feedback';

/** Owns cdr queries, mutations and view state for one mounted page. */
export function useCdrReport(props: Readonly<{ initialTab?: 'records' | 'callback' }>) {
  // 🌟 核心双 Tab：'records' (通话记录) | 'callback' (未接待回拨)
  const activeSubTab = ref<'records' | 'callback'>(props.initialTab || 'records');

  watch(() => props.initialTab, (val) => {
    if (val) {
      activeSubTab.value = val;
    }
  });

  // ==================== 1. 通话记录 (Call Records) ====================
  // 字段要求：主叫姓名/号码、运营商、坐席姓名、坐席工号、方向、通话开始时间、通话结束时间、响铃时长、录音（显示时长）、状态


  const callRecords = ref<CallRecord[]>([]);

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
        const realRecords = list.map(toCallRecord);
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
            carrier: '',
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

  const playAudio = (title: string, phone: string, duration: string, recordingUrl?: string, rawId?: string) => {
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

  return {
    activeSubTab,
    callRecords,
    filterAgentName,
    filterAgentWorkNo,
    filterPhone,
    filterDirection,
    filterCallId,
    filterDateRange,
    dateShortcuts,
    handleDateRangeChange,
    cdrPageNum,
    cdrPageSize,
    cdrTotal,
    cdrLoading,
    handleSearchCall,
    resetCallFilter,
    callbackRecords,
    filterCbCustomerPhone,
    filterCbAgentWorkNo,
    filteredCallbackRecords,
    cbPageNum,
    cbPageSize,
    pagedCallbackRecords,
    resetCallbackFilter,
    handleDispatch,
    handleCallbackCall,
    totalCallsCount,
    answeredCallsCount,
    answerRate,
    totalTalkDurationSec,
    inboundTalkSec,
    outboundTalkSec,
    formatDurationDisplay,
    onlineExtensionsCount,
    currentDateStr,
    playingAudio,
    currentAudioUrl,
    currentAudioInfo,
    playAudio,
    closeAudio,
    showDetailModal,
    selectedCdr,
    openDetail,
    handleSearchCallback,
    exporting,
    exportCdrCsv
  };
}
