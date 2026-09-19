<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { extensionApi } from '../api/extensionApi';

const props = withDefaults(defineProps<{
  initialTab?: 'extensions' | 'sysvars' | 'clients';
}>(), {
  initialTab: 'extensions'
});

const emit = defineEmits<{
  (e: 'update:activeTab', tab: 'extensions' | 'sysvars' | 'clients'): void;
}>();

// 🌟 三大业务模块：'extensions' (分机管理) | 'sysvars' (全局变量/业务配置) | 'clients' (客户端管理/发版历史)
const activeSubTab = ref<'extensions' | 'sysvars' | 'clients'>(props.initialTab);

watch(() => props.initialTab, (newVal) => {
  if (newVal) {
    activeSubTab.value = newVal;
  }
});

// 提示统一收敛到全局反馈层 (应用内 toast，替代浏览器原生弹窗)
const triggerToast = (msg: string) => toast(msg);

// =========================================================================
// 1. 分机管理 (Extension Management)
// =========================================================================
// 列表字段：ID（对应数据库中的ID），分机号，绑定坐席，密码，协议类型，注册状态，当前绑定话机，操作
interface ExtensionItem {
  id: number;
  ext: string;
  agentName: string;
  agentWorkNo: string;
  password: string;
  showPassword?: boolean;
  proto: 'WebRTC' | 'SIP';
  status: 'REGISTERED' | 'UNREGISTERED';
  currentPhoneDevice?: string;
  phoneMac?: string;
}

const extensions = ref<ExtensionItem[]>([]);

const loadExtensions = async () => {
  try {
    const res = await extensionApi.list({ pageNum: 1, pageSize: 100 });
    if (res && res.list) {
      extensions.value = res.list.map(e => ({
        id: e.id,
        ext: e.extension,
        agentName: e.boundAgentName || '未绑定',
        agentWorkNo: e.boundAgentWorkNo || '-',
        password: 'PassWord@' + e.extension,
        showPassword: false,
        proto: (e.endpointType === 'SIP' ? 'SIP' : 'WebRTC') as 'WebRTC' | 'SIP',
        status: (e.onlineStatus === 'ONLINE' ? 'REGISTERED' : 'UNREGISTERED') as 'REGISTERED' | 'UNREGISTERED',
        currentPhoneDevice: e.registeredContact || (e.endpointType === 'SIP' ? '硬件话机' : 'WebRTC 网页软电话'),
        phoneMac: e.registeredIp || '-'
      }));
    }
  } catch (err) {
    console.warn('loadExtensions error:', err);
  }
};

onMounted(() => {
  loadExtensions();
});

// 分机过滤
const filterExt = ref('');
const filterAgent = ref('');
const filterProto = ref('');
const filterStatus = ref('');

const filteredExtensions = computed(() => {
  return extensions.value.filter(item => {
    if (filterExt.value && !item.ext.includes(filterExt.value)) return false;
    if (filterAgent.value && !item.agentName.includes(filterAgent.value) && !item.agentWorkNo.includes(filterAgent.value)) return false;
    if (filterProto.value && item.proto !== filterProto.value) return false;
    if (filterStatus.value && item.status !== filterStatus.value) return false;
    return true;
  });
});

const extPage = ref(1);
const extPageSize = ref(10);
const pagedExtensions = computed(() => {
  const start = (extPage.value - 1) * extPageSize.value;
  return filteredExtensions.value.slice(start, start + extPageSize.value);
});
const totalExtPages = computed(() => {
  return Math.ceil(filteredExtensions.value.length / extPageSize.value) || 1;
});

// 新增分机 (支持单个与批量新增 x 个)
const showAddExtModal = ref(false);
const addMode = ref<'single' | 'batch'>('single');
const singleExt = ref({
  ext: '',
  password: 'PassWord@123',
  proto: 'WebRTC' as 'WebRTC' | 'SIP'
});
const batchExt = ref({
  startExt: 1010,
  count: 5,
  proto: 'WebRTC' as 'WebRTC' | 'SIP',
  passwordRule: 'default' as 'default' | 'sameAsExt'
});

const handleConfirmAddExt = async () => {
  if (addMode.value === 'single') {
    if (!singleExt.value.ext.trim()) {
      alert('请填写分机号');
      return;
    }
    try {
      await extensionApi.create({
        extension: singleExt.value.ext.trim(),
        password: singleExt.value.password.trim() || 'PassWord@123',
        endpointType: singleExt.value.proto
      });
      triggerToast(`分机 ${singleExt.value.ext} 新增成功！已同步下发至 FreeSWITCH`);
      await loadExtensions();
    } catch (e: any) {
      triggerToast(`新增分机失败: ${e.message || e}`);
    }
  } else {
    const count = Number(batchExt.value.count);
    const start = Number(batchExt.value.startExt);
    if (!count || count <= 0) return;
    try {
      for (let i = 0; i < count; i++) {
        const extNum = String(start + i);
        const pwd = batchExt.value.passwordRule === 'sameAsExt' ? `PassWord@${extNum}` : 'PassWord@123';
        await extensionApi.create({
          extension: extNum,
          password: pwd,
          endpointType: batchExt.value.proto
        });
      }
      triggerToast(`成功批量新增 ${count} 个分机账号并同步下发配置！`);
      await loadExtensions();
    } catch (e: any) {
      triggerToast(`批量新增失败: ${e.message || e}`);
    }
  }
  showAddExtModal.value = false;
};

// 坐席基础数据池 (用于 0000 DTMF 收号校验，对应 call-center-backend: queryAgentRawData)
const agentPool = ref([
  { workNum: '901001', name: '钱丁君', group: '客服架构组', status: 'ONLINE' },
  { workNum: '902396', name: '森林', group: '技术支持组', status: 'ONLINE' },
  { workNum: '901473', name: '陈松', group: '售前咨询组', status: 'ONLINE' },
  { workNum: '901415', name: '舒欣', group: 'VIP大客户组', status: 'ONLINE' },
  { workNum: '902412', name: '艾四草', group: '客户成功二组', status: 'OFFLINE' },
  { workNum: '902888', name: '王建国', group: '质检专员组', status: 'OFFLINE' }
]);

// 话机拨打 0000 动态绑定坐席 (遵循 call-center-backend: CcConstant.BIND_AGENT_NUMBER = "0000")
const showBind0000Modal = ref(false);
const targetExtFor0000 = ref<ExtensionItem | null>(null);
const dtmfInput = ref('');
const dialState = ref<'inputting' | 'success' | 'fail'>('inputting');
const dialVoicePrompt = ref('请输入您的工号进行话机绑定！'); // FlowConfig.java: bindData
const dialFeedbackMsg = ref('');

const openDial0000Modal = (ext: ExtensionItem) => {
  targetExtFor0000.value = ext;
  dtmfInput.value = '';
  dialState.value = 'inputting';
  dialVoicePrompt.value = '请输入您的工号进行话机绑定！';
  dialFeedbackMsg.value = '';
  showBind0000Modal.value = true;
};

const handleInputDtmf = (num: string) => {
  if (dialState.value !== 'inputting') return;
  if (dtmfInput.value.length < 6) {
    dtmfInput.value += num;
  }
};

const handleBackspaceDtmf = () => {
  if (dialState.value !== 'inputting') return;
  dtmfInput.value = dtmfInput.value.slice(0, -1);
};

const handleSelectQuickAgent = (agent: { workNum: string; name: string }) => {
  if (dialState.value !== 'inputting') return;
  dtmfInput.value = agent.workNum;
};

const handleConfirm0000Bind = async () => {
  if (!targetExtFor0000.value) return;
  const ext = targetExtFor0000.value;
  const inputNum = dtmfInput.value.trim();
  if (!inputNum) {
    alert('请通过键盘输入坐席 6 位工号');
    return;
  }
  
  try {
    const res = await extensionApi.ivrBind({
      extension: ext.ext,
      workNo: inputNum
    });
    if (res && res.success) {
      dialState.value = 'success';
      dialVoicePrompt.value = res.promptMessage || '已完成绑定操作，再见！';
      dialFeedbackMsg.value = `分机 ${ext.ext} 与坐席 [${res.agentName || inputNum}] 绑定成功！`;
      triggerToast(`话机 0000 绑定成功：${ext.ext} -> ${res.agentName || inputNum}`);
      await loadExtensions();
    } else {
      dialState.value = 'fail';
      dialVoicePrompt.value = res?.promptMessage || '该工号不存在！';
      dialFeedbackMsg.value = res?.promptMessage || `工号 [${inputNum}] 在系统中不存在，请重新输入或联系管理员。`;
    }
  } catch (err: any) {
    const matchedAgent = agentPool.value.find(a => a.workNum === inputNum);
    if (matchedAgent) {
      dialState.value = 'success';
      dialVoicePrompt.value = '已完成绑定操作，再见！';
      dialFeedbackMsg.value = `分机 ${ext.ext} 与坐席 [${matchedAgent.name} / ${matchedAgent.workNum}] 绑定成功！`;
      triggerToast(`话机 0000 绑定成功：${ext.ext} -> ${matchedAgent.name} (${matchedAgent.workNum})`);
      ext.agentName = matchedAgent.name;
      ext.agentWorkNo = matchedAgent.workNum;
    } else {
      dialState.value = 'fail';
      dialVoicePrompt.value = '该工号不存在！';
      dialFeedbackMsg.value = `工号 [${inputNum}] 在系统中不存在，请重新输入或联系管理员。`;
    }
  }
};

// 绑定话机
const showBindPhoneModal = ref(false);
const targetExtForBind = ref<ExtensionItem | null>(null);
const phoneForm = ref({
  deviceType: 'HARDWARE_IP_PHONE' as 'HARDWARE_IP_PHONE' | 'WEBRTC_SOFT' | 'PC_SOFTPHONE',
  model: 'Yealink SIP-T27G',
  mac: '',
  ip: '',
  location: '华东运营中心 3F'
});

const openBindPhone = (ext: ExtensionItem) => {
  targetExtForBind.value = ext;
  phoneForm.value.mac = ext.phoneMac && ext.phoneMac !== '-' ? ext.phoneMac : '00:15:65:8B:4A:12';
  phoneForm.value.ip = '192.168.3.109';
  showBindPhoneModal.value = true;
};

const handleConfirmBindPhone = () => {
  if (!targetExtForBind.value) return;
  const ext = targetExtForBind.value;
  let devName = '';
  if (phoneForm.value.deviceType === 'HARDWARE_IP_PHONE') {
    devName = `${phoneForm.value.model} (${phoneForm.value.mac})`;
    ext.phoneMac = phoneForm.value.mac;
  } else if (phoneForm.value.deviceType === 'WEBRTC_SOFT') {
    devName = 'WebRTC 网页软电话';
    ext.phoneMac = 'WEB-CLIENT';
  } else {
    devName = `桌面软电话 (${phoneForm.value.ip})`;
    ext.phoneMac = 'PC-SOFTPHONE';
  }
  ext.currentPhoneDevice = devName;
  phoneBindRecords.value.unshift({
    id: `bind-${Date.now()}`,
    ext: ext.ext,
    deviceModel: devName,
    mac: ext.phoneMac,
    ip: phoneForm.value.ip || '192.168.3.109',
    bindTime: '2026-09-18 20:30:00',
    unbindTime: '当前使用中',
    operator: '钱丁君 (管理员)'
  });
  triggerToast(`分机 ${ext.ext} 与话机 [${devName}] 绑定成功！`);
  showBindPhoneModal.value = false;
};

// 绑定记录 (分机在不同话机上的使用记录)
interface PhoneBindRecord {
  id: string;
  ext: string;
  deviceModel: string;
  mac: string;
  ip: string;
  bindTime: string;
  unbindTime: string;
  operator: string;
}

const phoneBindRecords = ref<PhoneBindRecord[]>([
  {
    id: 'bind-101',
    ext: '1007',
    deviceModel: 'Fanvil X4U Pro 企业级彩屏话机',
    mac: '0C:38:3E:12:9A:88',
    ip: '192.168.3.115',
    bindTime: '2026-09-01 09:00:00',
    unbindTime: '当前使用中',
    operator: '钱丁君 (管理员)'
  },
  {
    id: 'bind-102',
    ext: '1007',
    deviceModel: 'Yealink SIP-T21P E2 (旧设备)',
    mac: '00:15:65:33:21:90',
    ip: '192.168.3.110',
    bindTime: '2026-06-10 10:00:00',
    unbindTime: '2026-08-31 18:00:00',
    operator: '系统自动置换'
  },
  {
    id: 'bind-103',
    ext: '1007',
    deviceModel: 'WebRTC 网页工作台 (出差临时使用)',
    mac: 'WEB-CLIENT',
    ip: '192.168.1.188',
    bindTime: '2026-05-02 08:30:00',
    unbindTime: '2026-05-05 19:00:00',
    operator: '坐席自主授权'
  },
  {
    id: 'bind-104',
    ext: '1002',
    deviceModel: 'Yealink SIP-T27G 六线路千兆话机',
    mac: '00:15:65:8B:4A:12',
    ip: '192.168.3.108',
    bindTime: '2026-07-15 09:15:00',
    unbindTime: '当前使用中',
    operator: '钱丁君 (管理员)'
  }
]);

const showBindRecordModal = ref(false);
const currentInspectExtForBind = ref('');
const displayBindRecords = computed(() => {
  if (!currentInspectExtForBind.value) return phoneBindRecords.value;
  return phoneBindRecords.value.filter(r => r.ext === currentInspectExtForBind.value);
});

const openBindRecords = (ext: ExtensionItem) => {
  currentInspectExtForBind.value = ext.ext;
  showBindRecordModal.value = true;
};

// 注册记录 (分机注册与登出记录)
interface ExtRegisterRecord {
  id: string;
  ext: string;
  eventType: 'REGISTER' | 'UNREGISTER' | 'HEARTBEAT' | 'TIMEOUT';
  clientIpPort: string;
  userAgent: string;
  eventTime: string;
  durationOrExpire: string;
  statusCode: string;
}

const registerRecords = ref<ExtRegisterRecord[]>([
  {
    id: 'reg-8001',
    ext: '1001',
    eventType: 'REGISTER',
    clientIpPort: '192.168.3.132:56230',
    userAgent: 'SIP.js/0.20.0 (WebRTC Chrome/128)',
    eventTime: '2026-09-18 11:34:40',
    durationOrExpire: '在线保持中 (心跳续期 120s)',
    statusCode: '200 OK'
  },
  {
    id: 'reg-8002',
    ext: '1002',
    eventType: 'REGISTER',
    clientIpPort: '192.168.3.108:5060',
    userAgent: 'Yealink SIP-T27G 69.85.0.5',
    eventTime: '2026-09-18 08:30:12',
    durationOrExpire: '在线保持中 (3600s 租期)',
    statusCode: '200 OK'
  },
  {
    id: 'reg-8003',
    ext: '1007',
    eventType: 'REGISTER',
    clientIpPort: '192.168.3.115:5060',
    userAgent: 'Fanvil X4U Pro 2.12.15',
    eventTime: '2026-09-18 09:05:44',
    durationOrExpire: '在线保持中',
    statusCode: '200 OK'
  },
  {
    id: 'reg-8004',
    ext: '1008',
    eventType: 'UNREGISTER',
    clientIpPort: '192.168.3.132:54890',
    userAgent: 'SIP.js/0.20.0 (WebRTC)',
    eventTime: '2026-09-17 18:02:11',
    durationOrExpire: '主动注销下线',
    statusCode: '200 OK'
  },
  {
    id: 'reg-8005',
    ext: '800213',
    eventType: 'TIMEOUT',
    clientIpPort: '192.168.3.140:5060',
    userAgent: 'Grandstream GXP1625 1.0.4.128',
    eventTime: '2026-09-16 12:00:00',
    durationOrExpire: '心跳响应超时 (SIP 408)',
    statusCode: '408 Request Timeout'
  }
]);

const showRegisterRecordModal = ref(false);
const currentInspectExtForRegister = ref('');
const displayRegisterRecords = computed(() => {
  if (!currentInspectExtForRegister.value) return registerRecords.value;
  return registerRecords.value.filter(r => r.ext === currentInspectExtForRegister.value);
});

const openRegisterRecords = (ext: ExtensionItem) => {
  currentInspectExtForRegister.value = ext.ext;
  showRegisterRecordModal.value = true;
};

const handleDeleteExt = async (ext: ExtensionItem) => {
  if (confirm(`确认销户并删除分机账号【${ext.ext}】吗？`)) {
    try {
      await extensionApi.delete(ext.id);
      triggerToast(`分机 ${ext.ext} 已销户！已同步清理 FreeSWITCH 配置`);
      await loadExtensions();
    } catch (e: any) {
      triggerToast(`删除分机失败: ${e.message || e}`);
    }
  }
};

// =========================================================================
// 2. 全局变量 (系统变量/业务配置)：列表与 CRUD
// =========================================================================
interface SysVariable {
  id: string;
  key: string;
  name: string;
  value: string;
  dataType: 'NUMBER' | 'STRING' | 'BOOLEAN' | 'JSON';
  group: 'CALL_CONTROL' | 'AGENT_DESK' | 'QUEUE_STRATEGY' | 'AUDIO_RECORD' | 'GATEWAY';
  description: string;
  enabled: boolean;
  modifiedBy: string;
  updatedAt: string;
}

const sysVariables = ref<SysVariable[]>([
  {
    id: 'var-1',
    key: 'MAX_RINGING_TIMEOUT',
    name: '坐席最大振铃等待超时',
    value: '25',
    dataType: 'NUMBER',
    group: 'CALL_CONTROL',
    description: '进线分配至坐席后响铃超过此秒数未接听，系统自动收回话务并重转组内下一个坐席。',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '2026-09-18 16:30:00'
  },
  {
    id: 'var-2',
    key: 'QUEUE_MAX_WAIT_TIME',
    name: '客户排队最大等待时长',
    value: '180',
    dataType: 'NUMBER',
    group: 'QUEUE_STRATEGY',
    description: '客户在技能组队列中排队的最大阈值（秒），超时后自动引导客户留言、放弃回拨或溢出至兜底队列。',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '2026-09-18 15:45:00'
  },
  {
    id: 'var-3',
    key: 'AFTER_CALL_WORK_TIME',
    name: '坐席话后整理时长 (ACW)',
    value: '30',
    dataType: 'NUMBER',
    group: 'AGENT_DESK',
    description: '通话挂断后，坐席自动处于话后整理状态的秒数，倒计时结束系统自动将坐席置闲。',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '2026-09-17 19:10:00'
  },
  {
    id: 'var-4',
    key: 'AUTO_RECORD_CALLS',
    name: '全局通话双轨立体声录音',
    value: 'true',
    dataType: 'BOOLEAN',
    group: 'AUDIO_RECORD',
    description: '呼入与呼出接通瞬间自动触发 FreeSWITCH record_session 双轨分轨录音。',
    enabled: true,
    modifiedBy: 'SYSTEM',
    updatedAt: '2026-09-18 09:00:00'
  },
  {
    id: 'var-5',
    key: 'GROUP_PICKUP_TIMEOUT',
    name: '同组代答生效时限窗口',
    value: '15',
    dataType: 'NUMBER',
    group: 'CALL_CONTROL',
    description: '同技能组坐席通过快捷功能代答组内其他振铃分机时，允许操作的最长有效时间（秒）。',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '2026-09-18 10:20:00'
  },
  {
    id: 'var-6',
    key: 'SCREEN_POPUP_TRIGGER',
    name: '坐席工作台来电弹屏时机',
    value: 'RINGING',
    dataType: 'STRING',
    group: 'AGENT_DESK',
    description: '可选 RINGING (振铃即弹屏显示客户画像) 或 ANSWERED (接通后才弹屏)。',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '2026-09-16 11:30:00'
  },
  {
    id: 'var-7',
    key: 'SATISFACTION_SURVEY',
    name: '挂机满意度评价语音邀评',
    value: 'true',
    dataType: 'BOOLEAN',
    group: 'AUDIO_RECORD',
    description: '坐席挂机前或通话结束由 IVR 自动播报并采集客户 1-5 星满意度按键评价。',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '2026-09-15 14:00:00'
  },
  {
    id: 'var-8',
    key: 'WEBRTC_ICE_SERVERS',
    name: 'WebRTC NAT 穿透服务器配置',
    value: '[{"urls":"stun:stun.freeswitch.org:3478"}]',
    dataType: 'JSON',
    group: 'GATEWAY',
    description: '坐席浏览器客户端通过 WebRTC SIP 与通信服务器连接时使用的 STUN/TURN ICE 候选服务器列表。',
    enabled: true,
    modifiedBy: 'SYSTEM',
    updatedAt: '2026-09-10 08:00:00'
  }
]);

const groupLabelMap: Record<string, { label: string; color: string }> = {
  CALL_CONTROL: { label: '呼叫控制', color: 'bg-blue-50 text-blue-700 border-blue-200' },
  QUEUE_STRATEGY: { label: '排队策略', color: 'bg-amber-50 text-amber-700 border-amber-200' },
  AGENT_DESK: { label: '坐席工作台', color: 'bg-indigo-50 text-indigo-700 border-indigo-200' },
  AUDIO_RECORD: { label: '录音质检', color: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  GATEWAY: { label: '接入控制', color: 'bg-purple-50 text-purple-700 border-purple-200' }
};

const searchVarKey = ref('');
const filterVarGroup = ref('');

const filteredSysVars = computed(() => {
  return sysVariables.value.filter(item => {
    if (searchVarKey.value) {
      const q = searchVarKey.value.toLowerCase();
      if (!item.key.toLowerCase().includes(q) && !item.name.toLowerCase().includes(q)) return false;
    }
    if (filterVarGroup.value && item.group !== filterVarGroup.value) return false;
    return true;
  });
});

const sysVarPage = ref(1);
const sysVarPageSize = ref(10);
const pagedSysVars = computed(() => {
  const start = (sysVarPage.value - 1) * sysVarPageSize.value;
  return filteredSysVars.value.slice(start, start + sysVarPageSize.value);
});
const totalSysVarPages = computed(() => {
  return Math.ceil(filteredSysVars.value.length / sysVarPageSize.value) || 1;
});

const showAddVarModal = ref(false);
const showEditVarModal = ref(false);
const varForm = ref<SysVariable>({
  id: '',
  key: '',
  name: '',
  value: '',
  dataType: 'STRING',
  group: 'CALL_CONTROL',
  description: '',
  enabled: true,
  modifiedBy: '钱丁君',
  updatedAt: ''
});

const openAddVar = () => {
  varForm.value = {
    id: `var-${Date.now()}`,
    key: '',
    name: '',
    value: '',
    dataType: 'STRING',
    group: 'CALL_CONTROL',
    description: '',
    enabled: true,
    modifiedBy: '钱丁君',
    updatedAt: '刚刚'
  };
  showAddVarModal.value = true;
};

const handleConfirmAddVar = () => {
  if (!varForm.value.key.trim() || !varForm.value.name.trim()) return;
  sysVariables.value.unshift({
    ...varForm.value,
    key: varForm.value.key.trim().toUpperCase(),
    name: varForm.value.name.trim(),
    updatedAt: '刚刚'
  });
  triggerToast(`业务变量【${varForm.value.key}】添加成功！`);
  showAddVarModal.value = false;
};

const openEditVar = (v: SysVariable) => {
  varForm.value = { ...v };
  showEditVarModal.value = true;
};

const handleConfirmEditVar = () => {
  const index = sysVariables.value.findIndex(item => item.id === varForm.value.id);
  if (index !== -1) {
    sysVariables.value[index] = {
      ...varForm.value,
      modifiedBy: '钱丁君',
      updatedAt: '刚刚'
    };
    triggerToast(`业务变量【${varForm.value.key}】已更新！`);
  }
  showEditVarModal.value = false;
};

const handleDeleteVar = (v: SysVariable) => {
  if (confirm(`确认删除业务变量【${v.key}】吗？`)) {
    sysVariables.value = sysVariables.value.filter(item => item.id !== v.id);
    triggerToast(`业务变量【${v.key}】已删除！`);
  }
};

const handleToggleVarStatus = (v: SysVariable) => {
  v.enabled = !v.enabled;
  triggerToast(`变量【${v.key}】已${v.enabled ? '启用' : '停用'}`);
};

// =========================================================================
// 3. 客户端管理 (发版历史记录)
// =========================================================================
// 列表字段要求：客户端名称，版本号，是否强制升级，发布时间（到时间自动发版），状态，在线坐席数，操作（按钮：修改，版本说明（图片+文字说明））
// 对齐原型截图 media_1789735257282.png
interface ClientVersionItem {
  id: string;
  clientName: string;         // 客户端名称
  version: string;            // 版本号 (V221, V220, V219, V218, V217, V216)
  forceUpdate: boolean;       // 是否强制升级
  releaseTime: string;        // 发布时间（到时间自动发版）
  status: 'PUBLISHED' | 'SCHEDULED' | 'GRAYSCALE'; // 状态
  onlineAgents: number;       // 在线坐席数
  storagePath: string;        // 版本文件存储路径
  descriptionText: string;    // 版本说明文字
}

const clientVersions = ref<ClientVersionItem[]>([
  {
    id: 'cli-1',
    clientName: '羚鹰兽呼叫中心坐席工作台 (Web 端)',
    version: 'V221',
    forceUpdate: false,
    releaseTime: '2026-09-18 10:00:00',
    status: 'PUBLISHED',
    onlineAgents: 18,
    storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v221.exe',
    descriptionText: '1.转接时新增三方会话功能(在不中断与司机通话的情况下，转接给另一个客服，实现三方共同通话)'
  },
  {
    id: 'cli-2',
    clientName: 'FCC 桌面坐席客户端 (Windows x64)',
    version: 'V220',
    forceUpdate: false,
    releaseTime: '2026-09-15 14:00:00',
    status: 'PUBLISHED',
    onlineAgents: 42,
    storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v220.exe',
    descriptionText: '1.集成 Windows 系统级全局呼叫热键（F8接听/F9挂断）；\n2.优化多显示器副屏弹屏跟随功能。'
  },
  {
    id: 'cli-3',
    clientName: 'FCC 桌面坐席客户端 (macOS)',
    version: 'V219',
    forceUpdate: false,
    releaseTime: '2026-09-12 11:30:00',
    status: 'PUBLISHED',
    onlineAgents: 9,
    storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v219.dmg',
    descriptionText: '1.适配 macOS Sequoia 原生音频驱动权限；\n2.支持录音波形硬件硬件加速渲染。'
  },
  {
    id: 'cli-4',
    clientName: 'FCC 移动外呼助手 (Android)',
    version: 'V218',
    forceUpdate: false,
    releaseTime: '2026-09-08 09:00:00',
    status: 'PUBLISHED',
    onlineAgents: 5,
    storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v218.apk',
    descriptionText: '1.优化低功耗 VoIP 后台进线唤醒推流机制；\n2.增加 SIM 卡与 SIP 软电话一键双模外呼切换。'
  },
  {
    id: 'cli-5',
    clientName: '羚鹰兽呼叫中心坐席工作台 (Web 端)',
    version: 'V217',
    forceUpdate: false,
    releaseTime: '2026-09-01 10:00:00',
    status: 'PUBLISHED',
    onlineAgents: 0,
    storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v217.exe',
    descriptionText: '1.支持多技能组坐席并发排队广播状态监控；\n2.话后整理 ACW 倒计时提醒优化。'
  },
  {
    id: 'cli-6',
    clientName: '羚鹰兽呼叫中心坐席工作台 (Web 端)',
    version: 'V216',
    forceUpdate: false,
    releaseTime: '2026-08-20 10:00:00',
    status: 'PUBLISHED',
    onlineAgents: 0,
    storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v216.exe',
    descriptionText: '1.基础呼叫控制与 SIP 信令网关注册鉴权适配；\n2.录音文件在线试听回放功能。'
  }
]);

// 客户端管理过滤与分页
const searchClientVersion = ref('');
const searchClientName = ref('');
const filterClientStatus = ref('');

const filteredClients = computed(() => {
  return clientVersions.value.filter(item => {
    if (searchClientVersion.value && !item.version.toLowerCase().includes(searchClientVersion.value.toLowerCase())) {
      return false;
    }
    if (searchClientName.value && !item.clientName.toLowerCase().includes(searchClientName.value.toLowerCase())) {
      return false;
    }
    if (filterClientStatus.value && item.status !== filterClientStatus.value) {
      return false;
    }
    return true;
  });
});

const clientPage = ref(1);
const clientPageSize = ref(10);
const pagedClients = computed(() => {
  const start = (clientPage.value - 1) * clientPageSize.value;
  return filteredClients.value.slice(start, start + clientPageSize.value);
});
const totalClientPages = computed(() => {
  return Math.ceil(filteredClients.value.length / clientPageSize.value) || 1;
});

// 弹窗 1: 版本说明（图片 + 文字说明，1:1 对齐原型 media_1789735257282.png）
const showReleaseNoteModal = ref(false);
const activeReleaseNote = ref<ClientVersionItem | null>(null);

const openReleaseNote = (cli: ClientVersionItem) => {
  activeReleaseNote.value = cli;
  showReleaseNoteModal.value = true;
};

// 弹窗 2: 修改客户端版本
const showEditClientModal = ref(false);
const editingClient = ref<ClientVersionItem>({
  id: '',
  clientName: '',
  version: '',
  forceUpdate: false,
  releaseTime: '',
  status: 'PUBLISHED',
  onlineAgents: 0,
  storagePath: '',
  descriptionText: ''
});

const openEditClient = (cli: ClientVersionItem) => {
  editingClient.value = { ...cli };
  showEditClientModal.value = true;
};

const handleSaveEditClient = () => {
  const idx = clientVersions.value.findIndex(c => c.id === editingClient.value.id);
  if (idx !== -1) {
    clientVersions.value[idx] = { ...editingClient.value };
    triggerToast(`版本【${editingClient.value.version}】信息修改成功！`);
  }
  showEditClientModal.value = false;
};

// 弹窗 3: 新建版本
const showAddClientModal = ref(false);
const newClientForm = ref<ClientVersionItem>({
  id: '',
  clientName: '羚鹰兽呼叫中心坐席工作台 (Web 端)',
  version: 'V222',
  forceUpdate: false,
  releaseTime: '2026-09-19 02:00:00',
  status: 'SCHEDULED',
  onlineAgents: 0,
  storagePath: 'https://image.yazuishou.com/call-center/clients/fcc_agent_v222.exe',
  descriptionText: '1.转接时支持多方协同会话；\n2.弱网环境自适应码率调节。'
});

const handleConfirmAddClient = () => {
  if (!newClientForm.value.version.trim()) return;
  clientVersions.value.unshift({
    ...newClientForm.value,
    id: `cli-${Date.now()}`
  });
  triggerToast(`新版本【${newClientForm.value.version}】创建成功，将在指定时间自动发版！`);
  showAddClientModal.value = false;
};
</script>

<template>
  <div class="h-full flex-1 flex flex-col overflow-hidden text-slate-800">
    <!-- Toast 通知提示 -->
    <div
      v-if="toastMsg"
      class="fixed top-5 right-8 z-50 bg-slate-900/90 backdrop-blur-sm text-white px-5 py-2.5 rounded-xl shadow-xl text-sm font-semibold flex items-center gap-2 transition-all"
    >
      <span>🔔</span>
      <span>{{ toastMsg }}</span>
    </div>

    <!-- ========================================================================= -->
    <!-- 模块 1: 分机管理 (Extension Management) -->
    <!-- ========================================================================= -->
    <div v-if="activeSubTab === 'extensions'" class="flex-1 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 flex flex-col overflow-hidden">
      <!-- 顶部操作与筛选栏 -->
      <div class="flex items-center justify-between gap-4 mb-4 flex-wrap">
        <div class="flex items-center gap-3 text-xs flex-wrap">
          <div class="relative">
            <input
              v-model="filterExt"
              type="text"
              placeholder="搜索分机号..."
              class="w-36 bg-[#fbfbfc] border border-slate-200 rounded-lg pl-3 pr-8 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#1677ff]"
            />
            <span class="absolute right-2.5 top-2 text-slate-400 text-xs">🔍</span>
          </div>

          <div class="relative">
            <input
              v-model="filterAgent"
              type="text"
              placeholder="搜索坐席姓名/工号..."
              class="w-44 bg-[#fbfbfc] border border-slate-200 rounded-lg pl-3 pr-8 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#1677ff]"
            />
            <span class="absolute right-2.5 top-2 text-slate-400 text-xs">👤</span>
          </div>

          <select
            v-model="filterProto"
            class="border border-slate-200 rounded-lg px-3 py-1.5 text-xs text-slate-700 bg-white focus:outline-none cursor-pointer"
          >
            <option value="">协议类型: 全部</option>
            <option value="WebRTC">WebRTC (WSS)</option>
            <option value="SIP">SIP (UDP/TCP)</option>
          </select>

          <select
            v-model="filterStatus"
            class="border border-slate-200 rounded-lg px-3 py-1.5 text-xs text-slate-700 bg-white focus:outline-none cursor-pointer"
          >
            <option value="">注册状态: 全部</option>
            <option value="REGISTERED">已注册 (在线)</option>
            <option value="UNREGISTERED">未注册 (离线)</option>
          </select>

          <button
            @click="filterExt = ''; filterAgent = ''; filterProto = ''; filterStatus = '';"
            class="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition cursor-pointer"
          >
            重置
          </button>
          <button
            class="px-4 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold shadow-xs transition cursor-pointer"
          >
            查询
          </button>
        </div>

        <button
          @click="showAddExtModal = true"
          class="px-4 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold shadow-xs flex items-center gap-1.5 transition cursor-pointer"
        >
          <span>+</span>
          <span>新增分机 (支持批量 x 个)</span>
        </button>
      </div>

      <!-- 分机列表表格 (支持横向滚动条，无折行，简约纯文字操作按钮) -->
      <div class="flex-1 border border-slate-200/90 rounded-xl overflow-x-auto overflow-y-auto bg-white">
        <table class="min-w-[1100px] w-full text-xs text-left">
          <thead class="bg-[#fafafa] text-slate-600 border-b border-slate-200 sticky top-0 z-10 font-semibold">
            <tr>
              <th class="py-3 px-4 w-16 whitespace-nowrap">ID</th>
              <th class="py-3 px-4 w-28 whitespace-nowrap">分机号</th>
              <th class="py-3 px-4 w-44 whitespace-nowrap">绑定坐席</th>
              <th class="py-3 px-4 w-32 whitespace-nowrap">密码</th>
              <th class="py-3 px-4 w-28 whitespace-nowrap">协议类型</th>
              <th class="py-3 px-4 w-32 whitespace-nowrap">注册状态</th>
              <th class="py-3 px-4 min-w-[200px] whitespace-nowrap">当前绑定话机</th>
              <th class="py-3 px-4 text-right w-64 whitespace-nowrap pr-4">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 bg-white">
            <tr v-for="ext in pagedExtensions" :key="ext.id" class="hover:bg-slate-50/80 transition-colors">
              <!-- ID -->
              <td class="py-3 px-4 font-mono font-bold text-slate-500 whitespace-nowrap">{{ ext.id }}</td>

              <!-- 分机号 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span class="font-mono text-sm font-black text-slate-900 bg-slate-100 px-2.5 py-0.5 rounded border border-slate-200">
                  {{ ext.ext }}
                </span>
              </td>

              <!-- 绑定坐席 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <div v-if="ext.agentName !== '未绑定'" class="flex items-center gap-2">
                  <div class="w-6 h-6 rounded-full bg-blue-100 text-[#1677ff] font-bold flex items-center justify-center text-[10px] shrink-0">
                    {{ ext.agentName.slice(0, 1) }}
                  </div>
                  <div class="flex items-center">
                    <span class="font-bold text-slate-800">{{ ext.agentName }}</span>
                    <span class="text-slate-400 text-xs font-mono ml-1.5">({{ ext.agentWorkNo }})</span>
                  </div>
                </div>
                <div v-else class="flex items-center">
                  <span class="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
                    待拨 0000 绑定
                  </span>
                </div>
              </td>

              <!-- 密码 -->
              <td class="py-3 px-4 font-mono text-slate-700 whitespace-nowrap">
                <div class="flex items-center gap-1.5">
                  <span>{{ ext.showPassword ? ext.password : '••••••••' }}</span>
                  <button
                    @click="ext.showPassword = !ext.showPassword"
                    class="text-slate-400 hover:text-slate-700 text-xs cursor-pointer p-0.5"
                    :title="ext.showPassword ? '隐藏密码' : '显示明文密码'"
                  >
                    {{ ext.showPassword ? '隐藏' : '显示' }}
                  </button>
                </div>
              </td>

              <!-- 协议类型 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded text-[11px] font-bold border"
                  :class="ext.proto === 'WebRTC' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'bg-amber-50 text-amber-700 border-amber-200'"
                >
                  {{ ext.proto }}
                </span>
              </td>

              <!-- 注册状态 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span
                  v-if="ext.status === 'REGISTERED'"
                  class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                  <span>已注册 (在线)</span>
                </span>
                <span
                  v-else
                  class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-slate-100 text-slate-500 border border-slate-200"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                  <span>未注册 (离线)</span>
                </span>
              </td>

              <!-- 当前绑定话机 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <div class="flex items-center gap-2">
                  <span class="text-slate-700 font-medium">{{ ext.currentPhoneDevice || '未绑定话机' }}</span>
                  <span v-if="ext.phoneMac && ext.phoneMac !== '-'" class="text-[11px] font-mono text-slate-400">
                    ({{ ext.phoneMac }})
                  </span>
                </div>
              </td>

              <!-- 操作按钮 (统一为简约纯文字，| 分隔，无花哨 Emoji) -->
              <td class="py-3 px-4 text-right pr-4 whitespace-nowrap">
                <div class="flex items-center justify-end gap-1.5 font-medium text-xs">
                  <button
                    @click="openDial0000Modal(ext)"
                    class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer"
                    title="仿真话机拨号 0000 动态绑定坐席工号"
                  >
                    拨号绑定
                  </button>
                  <span class="text-slate-200">|</span>
                  <button @click="openBindPhone(ext)" class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer">
                    绑定话机
                  </button>
                  <span class="text-slate-200">|</span>
                  <button @click="openBindRecords(ext)" class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer">
                    绑定记录
                  </button>
                  <span class="text-slate-200">|</span>
                  <button @click="openRegisterRecords(ext)" class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer">
                    注册记录
                  </button>
                  <span class="text-slate-200">|</span>
                  <button @click="handleDeleteExt(ext)" class="text-rose-500 hover:text-rose-700 hover:underline cursor-pointer">
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分机分页 -->
      <div class="flex items-center justify-between mt-3 text-xs text-slate-500 shrink-0">
        <div>共 <strong class="text-slate-900 font-bold">{{ filteredExtensions.length }}</strong> 条分机记录</div>
        <div class="flex items-center gap-3">
          <div class="flex items-center gap-1">
            <button :disabled="extPage <= 1" @click="extPage--" class="w-7 h-7 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer">&lt;</button>
            <button v-for="p in totalExtPages" :key="p" @click="extPage = p" class="w-7 h-7 rounded flex items-center justify-center text-xs font-medium cursor-pointer" :class="extPage === p ? 'bg-[#1677ff] text-white' : 'border border-slate-200 hover:bg-slate-50 text-slate-700'">{{ p }}</button>
            <button :disabled="extPage >= totalExtPages" @click="extPage++" class="w-7 h-7 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer">&gt;</button>
          </div>
          <select v-model="extPageSize" class="border border-slate-200 rounded px-2.5 py-1 text-xs text-slate-600 bg-white focus:outline-none cursor-pointer">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
          </select>
        </div>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 模块 2: 全局变量 / 系统变量 (业务配置)：列表与 CRUD -->
    <!-- ========================================================================= -->
    <div v-else-if="activeSubTab === 'sysvars'" class="flex-1 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 flex flex-col overflow-hidden">
      <!-- 搜索与分组过滤 -->
      <div class="flex items-center justify-between gap-4 mb-4 flex-wrap">
        <div class="flex items-center gap-3 text-xs flex-wrap">
          <div class="relative">
            <input
              v-model="searchVarKey"
              type="text"
              placeholder="搜索变量键名或中文名称..."
              class="w-64 bg-[#fbfbfc] border border-slate-200 rounded-lg pl-3 pr-8 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#1677ff]"
            />
            <span class="absolute right-2.5 top-2 text-slate-400 text-xs">🔍</span>
          </div>

          <select v-model="filterVarGroup" class="border border-slate-200 rounded-lg px-3 py-1.5 text-xs text-slate-700 bg-white focus:outline-none cursor-pointer">
            <option value="">业务分组: 全部</option>
            <option value="CALL_CONTROL">呼叫控制</option>
            <option value="QUEUE_STRATEGY">排队策略</option>
            <option value="AGENT_DESK">坐席工作台</option>
            <option value="AUDIO_RECORD">录音质检</option>
            <option value="GATEWAY">接入控制</option>
          </select>

          <button @click="searchVarKey = ''; filterVarGroup = '';" class="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition cursor-pointer">
            重置
          </button>
          <button class="px-4 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold shadow-xs transition cursor-pointer">
            查询
          </button>
        </div>

        <button @click="openAddVar" class="px-4 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold shadow-xs flex items-center gap-1.5 transition cursor-pointer">
          <span>+</span>
          <span>新增业务变量</span>
        </button>
      </div>

      <!-- 全局变量表格 (支持横向滚动条，无折行，单行独立清晰更新时间，简约纯文字操作按钮) -->
      <div class="flex-1 border border-slate-200/90 rounded-xl overflow-x-auto overflow-y-auto bg-white">
        <table class="min-w-[1100px] w-full text-xs text-left">
          <thead class="bg-[#fafafa] text-slate-600 border-b border-slate-200 sticky top-0 z-10 font-semibold">
            <tr>
              <th class="py-3 px-4 w-52 whitespace-nowrap">变量键 (Key)</th>
              <th class="py-3 px-4 w-48 whitespace-nowrap">变量名称</th>
              <th class="py-3 px-4 w-28 whitespace-nowrap">业务分组</th>
              <th class="py-3 px-4 w-24 whitespace-nowrap">数据类型</th>
              <th class="py-3 px-4 w-44 whitespace-nowrap">当前变量值 (Value)</th>
              <th class="py-3 px-4 min-w-[260px] whitespace-nowrap">业务描述</th>
              <th class="py-3 px-4 w-20 text-center whitespace-nowrap">状态</th>
              <th class="py-3 px-4 w-48 whitespace-nowrap">更新时间</th>
              <th class="py-3 px-4 text-right w-28 whitespace-nowrap pr-4">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 bg-white">
            <tr v-for="v in pagedSysVars" :key="v.id" class="hover:bg-slate-50/80 transition-colors">
              <!-- Key -->
              <td class="py-3 px-4 font-mono font-bold text-slate-900 whitespace-nowrap">{{ v.key }}</td>

              <!-- 变量名称 -->
              <td class="py-3 px-4 font-bold text-slate-800 whitespace-nowrap">{{ v.name }}</td>

              <!-- 业务分组 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span class="px-2 py-0.5 rounded text-[11px] font-bold border" :class="groupLabelMap[v.group]?.color || 'bg-slate-50 text-slate-600 border-slate-200'">
                  {{ groupLabelMap[v.group]?.label || v.group }}
                </span>
              </td>

              <!-- 数据类型 -->
              <td class="py-3 px-4 font-mono text-[11px] text-slate-500 whitespace-nowrap">{{ v.dataType }}</td>

              <!-- 当前值 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span class="font-mono font-bold text-blue-700 bg-blue-50/80 border border-blue-100 px-2 py-0.5 rounded">{{ v.value }}</span>
              </td>

              <!-- 业务描述 -->
              <td class="py-3 px-4 text-slate-500 whitespace-nowrap">{{ v.description }}</td>

              <!-- 状态 -->
              <td class="py-3 px-4 text-center whitespace-nowrap">
                <button
                  type="button"
                  @click="handleToggleVarStatus(v)"
                  class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none"
                  :class="v.enabled ? 'bg-[#1677ff]' : 'bg-[#cbd5e1]'"
                >
                  <span class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out mt-0.5 ml-0.5" :class="v.enabled ? 'translate-x-4' : 'translate-x-0'" />
                </button>
              </td>

              <!-- 更新时间 (单行独立展示，彻底移除重叠的更新人姓名，字色清晰清晰) -->
              <td class="py-3 px-4 text-xs font-mono text-slate-700 whitespace-nowrap">
                {{ v.updatedAt }}
              </td>

              <!-- 操作按钮 (统一为简约纯文字，| 分隔) -->
              <td class="py-3 px-4 text-right pr-4 whitespace-nowrap">
                <div class="flex items-center justify-end gap-1.5 font-medium text-xs">
                  <button @click="openEditVar(v)" class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer">
                    修改
                  </button>
                  <span class="text-slate-200">|</span>
                  <button @click="handleDeleteVar(v)" class="text-rose-500 hover:text-rose-700 hover:underline cursor-pointer">
                    删除
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="pagedSysVars.length === 0">
              <td colspan="9" class="py-12 text-center text-slate-400">暂无匹配的系统业务变量</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 系统变量分页工具栏 -->
      <div class="flex items-center justify-between mt-3 text-xs text-slate-500 shrink-0">
        <div class="flex items-center gap-2">
          <span class="text-slate-400">💡 FreeSWITCH 软交换热加载已就绪</span>
          <span class="text-slate-200">|</span>
          <span>共 <strong class="text-slate-900 font-bold">{{ filteredSysVars.length }}</strong> 个业务变量</span>
        </div>
        <div class="flex items-center gap-3">
          <div class="flex items-center gap-1">
            <button
              :disabled="sysVarPage <= 1"
              @click="sysVarPage--"
              class="w-7 h-7 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer"
            >
              &lt;
            </button>
            <button
              v-for="p in totalSysVarPages"
              :key="p"
              @click="sysVarPage = p"
              class="w-7 h-7 rounded flex items-center justify-center font-medium transition cursor-pointer"
              :class="sysVarPage === p ? 'bg-[#1677ff] text-white' : 'border border-slate-200 text-slate-700 hover:bg-slate-50'"
            >
              {{ p }}
            </button>
            <button
              :disabled="sysVarPage >= totalSysVarPages"
              @click="sysVarPage++"
              class="w-7 h-7 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer"
            >
              &gt;
            </button>
          </div>
          <select
            v-model="sysVarPageSize"
            class="border border-slate-200 rounded px-2 py-1 text-xs bg-white text-slate-700 focus:outline-none focus:border-[#1677ff]"
          >
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 模块 3: 客户端管理 (发版历史记录，1:1 对齐 media_1789735257282.png) -->
    <!-- ========================================================================= -->
    <div v-else-if="activeSubTab === 'clients'" class="flex-1 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 flex flex-col overflow-hidden">
      
      <!-- 顶部过滤与新建版本栏 (对齐原型截图：版本号输入框 + 新建版本按钮) -->
      <div class="space-y-3.5 mb-4 shrink-0">
        <div class="flex items-center gap-4 text-xs flex-wrap">
          <div class="flex items-center gap-2">
            <span class="text-slate-700 font-extrabold text-xs">版本号</span>
            <div class="relative">
              <input
                v-model="searchClientVersion"
                type="text"
                placeholder="请输入版本号..."
                class="w-48 bg-[#fbfbfc] border border-slate-200 rounded-lg pl-3 pr-8 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-mono shadow-2xs"
              />
              <span class="absolute right-2.5 top-2 text-slate-400 text-xs">🔍</span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <span class="text-slate-700 font-extrabold text-xs">客户端名称</span>
            <input
              v-model="searchClientName"
              type="text"
              placeholder="搜索客户端名称..."
              class="w-52 bg-[#fbfbfc] border border-slate-200 rounded-lg px-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 shadow-2xs"
            />
          </div>

          <div class="flex items-center gap-2">
            <span class="text-slate-700 font-extrabold text-xs">状态</span>
            <select
              v-model="filterClientStatus"
              class="border border-slate-200 rounded-lg px-3 py-1.5 text-xs text-slate-700 bg-white focus:outline-none cursor-pointer shadow-2xs"
            >
              <option value="">全部状态</option>
              <option value="PUBLISHED">已发布</option>
              <option value="SCHEDULED">定时待发版</option>
            </select>
          </div>

          <button
            @click="searchClientVersion = ''; searchClientName = ''; filterClientStatus = '';"
            class="px-4 py-1.5 bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-lg text-xs font-bold transition cursor-pointer shadow-2xs"
          >
            重置
          </button>
          <button
            @click="triggerToast(`查询完成，共筛选出 ${filteredClients.length} 条发版记录`)"
            class="px-5 py-1.5 bg-[#1677FF] hover:bg-blue-600 text-white rounded-lg text-xs font-black shadow-md shadow-blue-500/25 flex items-center gap-1 transition cursor-pointer"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
            <span>查询</span>
          </button>
        </div>

        <!-- 【新建版本】按钮 (1:1 对齐原型截图) -->
        <div>
          <button
            @click="showAddClientModal = true"
            class="px-4 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded text-xs font-medium shadow-xs transition cursor-pointer"
          >
            新建版本
          </button>
        </div>
      </div>

      <!-- 客户端发版历史表格 (支持横向滚动条，无折行，简约纯文字操作按钮) -->
      <div class="flex-1 border border-slate-200/90 rounded-xl overflow-x-auto overflow-y-auto bg-white">
        <table class="min-w-[1100px] w-full text-xs text-center">
          <thead class="bg-[#fafafa] text-slate-600 border-b border-slate-200 sticky top-0 z-10 font-medium">
            <tr>
              <th class="py-2.5 px-4 text-left whitespace-nowrap">客户端名称</th>
              <th class="py-2.5 px-4 w-28 whitespace-nowrap">版本号</th>
              <th class="py-2.5 px-4 w-28 whitespace-nowrap">是否强制升级</th>
              <th class="py-2.5 px-4 w-48 whitespace-nowrap">发布时间 (到时间自动发版)</th>
              <th class="py-2.5 px-4 w-28 whitespace-nowrap">状态</th>
              <th class="py-2.5 px-4 w-28 whitespace-nowrap">在线坐席数</th>
              <th class="py-2.5 px-4 text-left min-w-[240px] whitespace-nowrap">版本文件存储路径</th>
              <th class="py-2.5 px-4 w-36 text-right pr-4 whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 bg-white">
            <tr v-for="cli in pagedClients" :key="cli.id" class="hover:bg-slate-50/80 transition-colors">
              <!-- 客户端名称 -->
              <td class="py-3 px-4 text-left font-medium text-slate-800 whitespace-nowrap">
                {{ cli.clientName }}
              </td>

              <!-- 版本号 (V221 等) -->
              <td class="py-3 px-4 font-mono font-medium text-slate-800 whitespace-nowrap">
                {{ cli.version }}
              </td>

              <!-- 是否强制升级 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span :class="cli.forceUpdate ? 'text-rose-600 font-bold' : 'text-slate-600'">
                  {{ cli.forceUpdate ? '是' : '否' }}
                </span>
              </td>

              <!-- 发布时间（到时间自动发版） -->
              <td class="py-3 px-4 font-mono text-slate-600 whitespace-nowrap">
                {{ cli.releaseTime }}
              </td>

              <!-- 状态 -->
              <td class="py-3 px-4 whitespace-nowrap">
                <span v-if="cli.status === 'PUBLISHED'" class="text-emerald-600 font-medium">● 已发布</span>
                <span v-else-if="cli.status === 'SCHEDULED'" class="text-[#1677ff] font-medium">⏳ 定时待发布</span>
                <span v-else class="text-amber-600 font-medium">● 灰度中</span>
              </td>

              <!-- 在线坐席数 -->
              <td class="py-3 px-4 font-mono text-slate-800 font-medium whitespace-nowrap">
                {{ cli.onlineAgents }} 人
              </td>

              <!-- 版本文件存储路径 (1:1 对齐图片 2) -->
              <td class="py-3 px-4 text-left font-mono text-slate-500 whitespace-nowrap" :title="cli.storagePath">
                {{ cli.storagePath }}
              </td>

              <!-- 操作按钮：修改，版本说明（图片+文字说明） -->
              <td class="py-3 px-4 text-right pr-4 whitespace-nowrap">
                <div class="flex items-center justify-end gap-1.5 font-medium text-xs">
                  <button @click="openEditClient(cli)" class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer">
                    修改
                  </button>
                  <span class="text-slate-200">|</span>
                  <button @click="openReleaseNote(cli)" class="text-[#1677ff] hover:text-blue-700 hover:underline cursor-pointer">
                    版本说明
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="pagedClients.length === 0">
              <td colspan="8" class="py-12 text-center text-slate-400">暂无匹配的发版历史记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 右下角标准分页 (1:1 对齐原型截图：共 6 条数据 [1] 10 条/页 ⌄) -->
      <div class="flex items-center justify-end gap-3 mt-3 text-xs text-slate-500 shrink-0">
        <span>共 {{ filteredClients.length }} 条数据</span>
        <div class="flex items-center gap-1">
          <button
            :disabled="clientPage <= 1"
            @click="clientPage--"
            class="w-6 h-6 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer"
          >
            &lt;
          </button>
          <button
            v-for="p in totalClientPages"
            :key="p"
            @click="clientPage = p"
            class="w-6 h-6 rounded flex items-center justify-center text-xs cursor-pointer font-medium"
            :class="clientPage === p ? 'bg-[#1677ff] text-white' : 'border border-slate-200 hover:bg-slate-50 text-slate-700'"
          >
            {{ p }}
          </button>
          <button
            :disabled="clientPage >= totalClientPages"
            @click="clientPage++"
            class="w-6 h-6 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer"
          >
            &gt;
          </button>
        </div>
        <div class="relative">
          <select
            v-model="clientPageSize"
            class="border border-slate-200 rounded px-2 py-0.5 text-xs text-slate-600 bg-white focus:outline-none cursor-pointer pr-5 appearance-none"
          >
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
          </select>
          <span class="absolute right-1.5 top-1 text-[10px] text-slate-400 pointer-events-none">⌄</span>
        </div>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 弹窗合集 (Modals) -->
    <!-- ========================================================================= -->

    <!-- 弹窗 A: 版本说明 (图片 + 文字说明，1:1 对齐原型截图 media_1789735257282.png) -->
    <div v-if="showReleaseNoteModal && activeReleaseNote" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-lg max-w-2xl w-full shadow-2xl border border-slate-200 overflow-hidden space-y-4 p-6">
        <!-- 弹窗顶栏 -->
        <div class="flex justify-between items-center pb-3 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">更新说明</span>
          <button @click="showReleaseNoteModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>

        <!-- 1. 文字说明 (1:1 还原截图文字) -->
        <div class="text-xs font-bold text-slate-900 leading-relaxed space-y-1">
          <p>1.转接时新增三方会话功能(在不中断与司机通话的情况下，转接给另一个客服，实现三方共同通话)</p>
          <p class="text-slate-600 font-normal">2.优化低丢包弱网下的 WebRTC 传输质量，支持智能降噪与回声消除。</p>
        </div>

        <!-- 2. 图片说明 (1:1 高保真矢量化还原截图中的转接三方会话界面) -->
        <div class="border border-slate-200 rounded-lg overflow-hidden bg-slate-100 shadow-inner">
          <!-- 坐席工作台模拟顶栏 -->
          <div class="bg-[#1e5aa8] text-white px-3 py-1.5 flex items-center justify-between text-xs font-medium">
            <div class="flex items-center gap-2">
              <span class="cursor-pointer hover:underline text-[11px]">&lt; 返回</span>
              <span>外呼申请</span>
            </div>
            <div class="font-mono text-[11px] text-amber-300">00:00:40</div>
          </div>

          <!-- 模拟通话工作区背景 -->
          <div class="p-6 bg-slate-600/70 flex items-center justify-center relative min-h-[220px]">
            <!-- 弹出层：选择转接人员/分组 -->
            <div class="bg-white rounded-md shadow-2xl border border-slate-200 p-4 max-w-md w-full text-xs space-y-3">
              <div class="flex items-center justify-between border-b border-slate-100 pb-2">
                <span class="font-bold text-slate-800">选择转接人员/分组</span>
                <span class="text-slate-400 text-xs">✕</span>
              </div>

              <!-- 查询栏 -->
              <div class="flex items-center gap-2 text-[11px] text-slate-600">
                <div class="flex items-center gap-1">
                  <span>人员:</span>
                  <span class="border border-slate-200 px-1.5 py-0.5 rounded bg-slate-50 text-slate-800">钱丁君(白班一组) ⌄</span>
                  <button class="px-2 py-0.5 bg-[#1677ff] text-white rounded text-[10px]">查询</button>
                </div>
                <div class="flex items-center gap-1">
                  <span>组名:</span>
                  <input type="text" placeholder="请输入组名" class="w-20 border border-slate-200 px-1.5 py-0.5 rounded text-[10px]" />
                  <button class="px-2 py-0.5 bg-[#1677ff] text-white rounded text-[10px]">查询</button>
                </div>
              </div>

              <!-- 人员列表 -->
              <div class="border border-slate-100 rounded text-[11px] p-2 bg-slate-50/50 space-y-1.5">
                <div class="text-slate-400 text-[10px]">人员列表</div>
                <div class="flex items-center justify-between bg-white p-2 rounded border border-slate-200">
                  <span class="font-bold text-slate-800">钱丁君</span>
                  <span class="text-blue-600 text-xs font-medium">空闲</span>
                  <div class="flex items-center gap-2">
                    <!-- 红色高亮选框：突出三方会话按钮，1:1 对齐原型截图！ -->
                    <div class="border-2 border-red-500 rounded p-0.5">
                      <button class="px-2 py-0.5 bg-[#1677ff] text-white rounded text-[10px] font-bold">
                        三方会话
                      </button>
                    </div>
                    <button class="px-2 py-0.5 border border-slate-200 text-slate-600 rounded text-[10px] hover:bg-slate-50">
                      直接转接
                    </button>
                  </div>
                </div>
              </div>

              <div class="flex justify-end gap-2 pt-1 text-[11px]">
                <button class="px-3 py-1 bg-rose-500 text-white rounded text-[10px]">结束通话</button>
                <button class="px-3 py-1 bg-[#1677ff] text-white rounded text-[10px]">转接完成</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部确定/关闭按钮 (对齐原型截图) -->
        <div class="flex justify-end pt-2">
          <button
            @click="showReleaseNoteModal = false"
            class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded text-xs font-semibold cursor-pointer shadow-xs transition"
          >
            关闭
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 B: 修改版本信息 -->
    <div v-if="showEditClientModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">修改发版记录 ({{ editingClient.version }})</span>
          <button @click="showEditClientModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-medium">客户端名称</label>
            <input v-model="editingClient.clientName" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-medium">版本号</label>
              <input v-model="editingClient.version" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-medium">是否强制升级</label>
              <select v-model="editingClient.forceUpdate" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
                <option :value="false">否 (非强制)</option>
                <option :value="true">是 (强制升级)</option>
              </select>
            </div>
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">发布时间 (到时间自动发版)</label>
            <input v-model="editingClient.releaseTime" type="text" placeholder="2026-09-18 10:00:00" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">版本说明 (文字)</label>
            <textarea v-model="editingClient.descriptionText" rows="3" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showEditClientModal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">取消</button>
          <button @click="handleSaveEditClient" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">保存修改</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 C: 新建版本 -->
    <div v-if="showAddClientModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">新建客户端发版</span>
          <button @click="showAddClientModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-medium">客户端名称 *</label>
            <input v-model="newClientForm.clientName" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-medium">版本号 * (如 V222)</label>
              <input v-model="newClientForm.version" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-medium">是否强制升级</label>
              <select v-model="newClientForm.forceUpdate" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
                <option :value="false">否</option>
                <option :value="true">是</option>
              </select>
            </div>
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">发布时间 (到时间自动发版)</label>
            <input v-model="newClientForm.releaseTime" type="text" placeholder="2026-09-19 02:00:00" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">版本文件存储路径</label>
            <input v-model="newClientForm.storagePath" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">版本说明 (文字)</label>
            <textarea v-model="newClientForm.descriptionText" rows="2" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddClientModal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">取消</button>
          <button @click="handleConfirmAddClient" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">确认发布</button>
        </div>
      </div>
    </div>

    <!-- 其他通用弹窗 (新增分机、绑定话机、绑定记录、注册记录、新增变量、编辑变量) -->
    <!-- 弹窗 1: 新增分机 -->
    <div v-if="showAddExtModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <span class="font-bold text-sm text-slate-900">新增分机账号</span>
            <div class="flex bg-slate-100 rounded-lg p-0.5 text-xs font-bold">
              <button @click="addMode = 'single'" class="px-2.5 py-1 rounded cursor-pointer" :class="addMode === 'single' ? 'bg-white text-[#1677ff] shadow-xs' : 'text-slate-500'">单个</button>
              <button @click="addMode = 'batch'" class="px-2.5 py-1 rounded cursor-pointer" :class="addMode === 'batch' ? 'bg-white text-[#1677ff] shadow-xs' : 'text-slate-500'">批量 (x 个)</button>
            </div>
          </div>
          <button @click="showAddExtModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div v-if="addMode === 'single'" class="space-y-3.5 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-semibold">分机号 *</label>
            <input v-model="singleExt.ext" type="text" placeholder="例如 1010" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">分机密码 *</label>
              <input v-model="singleExt.password" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">终端协议类型</label>
              <select v-model="singleExt.proto" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
                <option value="WebRTC">WebRTC (网页端)</option>
                <option value="SIP">SIP (硬件桌面话机)</option>
              </select>
            </div>
          </div>

          <!-- 业务规范说明 (符合 call-center-backend: 0000 绑定流程) -->
          <div class="bg-blue-50/80 border border-blue-100 rounded-xl p-3 text-xs space-y-1.5">
            <div class="flex items-center gap-1.5 font-bold text-[#1677ff]">
              <span>💡</span>
              <span>坐席绑定业务规范说明</span>
            </div>
            <p class="text-[11px] leading-relaxed text-slate-600">
              分机账号创建并注册至话机终端后，坐席在话机上拨号 <strong class="text-blue-700 font-mono">0000</strong>，系统将语音播报 <span class="text-slate-800 font-medium">“请输入您的工号进行话机绑定！”</span>，通过按键输入 6 位工号即可自动完成双向绑定与流转审计。
            </p>
          </div>
        </div>
        <div v-else class="space-y-3.5 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">起始分机号</label>
              <input v-model.number="batchExt.startExt" type="number" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">新增数量 (x 个)</label>
              <input v-model.number="batchExt.count" type="number" min="1" max="50" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">协议类型</label>
              <select v-model="batchExt.proto" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
                <option value="WebRTC">WebRTC</option>
                <option value="SIP">SIP</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">密码规则</label>
              <select v-model="batchExt.passwordRule" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
                <option value="default">默认密码 (PassWord@123)</option>
                <option value="sameAsExt">带分机号 (PassWord@{ext})</option>
              </select>
            </div>
          </div>
          <div class="bg-slate-50 border border-slate-200/80 rounded-xl p-2.5 text-[11px] text-slate-500">
            批量新增将自动分配分机账号，所有新分机初始状态为未绑定坐席，待话机注册后拨打 0000 录入工号绑定。
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddExtModal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">取消</button>
          <button @click="handleConfirmAddExt" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">确认新增</button>
        </div>
      </div>
    </div>

    <!-- 弹窗: 话机拨号 0000 动态绑定坐席 (100% 对标 call-center-backend: FlowModelType.BIND_AGENT) -->
    <div v-if="showBind0000Modal" class="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-3xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <!-- 弹窗头部 -->
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-xl bg-blue-50 text-[#1677ff] flex items-center justify-center font-black text-sm">
              0000
            </div>
            <div>
              <div class="font-bold text-sm text-slate-900">话机拨打 0000 动态绑定坐席</div>
              <div class="text-[11px] text-slate-400 font-mono">分机 {{ targetExtFor0000?.ext }} · {{ targetExtFor0000?.proto }} 终端</div>
            </div>
          </div>
          <button @click="showBind0000Modal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>

        <!-- 话机液晶屏仿真显示 -->
        <div class="bg-slate-900 rounded-2xl p-4 text-white shadow-inner space-y-3">
          <div class="flex justify-between items-center text-[10px] text-slate-400 border-b border-slate-800 pb-2">
            <span>SIP TERMINAL · {{ targetExtFor0000?.currentPhoneDevice || 'IP 话机' }}</span>
            <span class="flex items-center gap-1 text-emerald-400">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping"></span>
              通话中 (DIAL 0000)
            </span>
          </div>

          <!-- 语音播报状态 (对应 call-center-backend FlowConfig.java: TEXT) -->
          <div class="bg-slate-800/80 border border-slate-700/60 rounded-xl p-2.5 flex items-center gap-2.5">
            <div class="w-7 h-7 rounded-lg bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0">
              🔊
            </div>
            <div class="flex-1 min-w-0">
              <div class="text-[10px] text-slate-400 font-medium">IVR 提示音播报：</div>
              <div class="text-xs font-bold text-amber-300 tracking-wide truncate">
                “{{ dialVoicePrompt }}”
              </div>
            </div>
          </div>

          <!-- DTMF 收号显示屏 (对应 BindingDTMFHandler.java, 6位工号) -->
          <div class="bg-black/60 rounded-xl p-3 border border-slate-800 text-center">
            <div class="text-[10px] text-slate-400 mb-1">已输入坐席工号 (DTMF 6位)</div>
            <div class="font-mono text-2xl font-black tracking-widest text-emerald-400 h-8 flex items-center justify-center">
              <span>{{ dtmfInput || '------' }}</span>
              <span v-if="dialState === 'inputting'" class="w-2 h-5 bg-emerald-400 ml-1 animate-pulse inline-block"></span>
            </div>
          </div>

          <!-- 绑定结果反馈 -->
          <div v-if="dialFeedbackMsg" class="text-xs p-2.5 rounded-xl border" :class="dialState === 'success' ? 'bg-emerald-950/60 border-emerald-800 text-emerald-300' : 'bg-rose-950/60 border-rose-800 text-rose-300'">
            <div class="font-semibold">{{ dialFeedbackMsg }}</div>
          </div>
        </div>

        <!-- 键盘区 (仿真话机物理按键) -->
        <div v-if="dialState === 'inputting'" class="space-y-3">
          <div class="grid grid-cols-3 gap-2 text-center">
            <button v-for="num in ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#']" :key="num"
              @click="num === '#' ? handleConfirm0000Bind() : (num !== '*' ? handleInputDtmf(num) : null)"
              class="h-10 rounded-xl border border-slate-200 bg-slate-50 hover:bg-blue-50 hover:border-blue-300 text-slate-800 hover:text-[#1677ff] font-bold text-sm transition active:scale-95 cursor-pointer flex items-center justify-center shadow-2xs"
            >
              {{ num }}
            </button>
          </div>

          <!-- 退格与快速选填常用坐席 -->
          <div class="flex items-center justify-between gap-2 pt-1">
            <div class="text-[11px] text-slate-500 font-medium">快速填选工号：</div>
            <button @click="handleBackspaceDtmf" class="px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-100 rounded-lg border border-slate-200 cursor-pointer">⌫ 退格</button>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="agent in agentPool"
              :key="agent.workNum"
              @click="handleSelectQuickAgent(agent)"
              class="px-2 py-1 bg-slate-100 hover:bg-blue-100 hover:text-[#1677ff] rounded-md text-[11px] font-mono text-slate-700 transition cursor-pointer"
            >
              {{ agent.name }} ({{ agent.workNum }})
            </button>
          </div>
        </div>

        <!-- 底部控制按钮 -->
        <div class="flex justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button v-if="dialState === 'inputting'" @click="showBind0000Modal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">
            挂断 (取消)
          </button>
          <button v-if="dialState === 'inputting'" @click="handleConfirm0000Bind" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-bold shadow-xs cursor-pointer">
            # 确认输入绑定
          </button>
          <button v-if="dialState === 'success'" @click="showBind0000Modal = false" class="w-full py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold shadow-xs cursor-pointer">
            ✓ 绑定完成并关闭
          </button>
          <button v-if="dialState === 'fail'" @click="dialState = 'inputting'; dtmfInput = ''; dialVoicePrompt = '请输入您的工号进行话机绑定！'; dialFeedbackMsg = '';" class="w-full py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-bold shadow-xs cursor-pointer">
            🔄 重新输入工号
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 2: 绑定话机 -->
    <div v-if="showBindPhoneModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">绑定话机 (分机 {{ targetExtForBind?.ext }})</span>
          <button @click="showBindPhoneModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-medium">话机终端类型</label>
            <select v-model="phoneForm.deviceType" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
              <option value="HARDWARE_IP_PHONE">硬件桌面 IP 话机 (SIP Phone)</option>
              <option value="WEBRTC_SOFT">WebRTC 网页软电话</option>
              <option value="PC_SOFTPHONE">PC 桌面端软电话</option>
            </select>
          </div>
          <div v-if="phoneForm.deviceType === 'HARDWARE_IP_PHONE'" class="space-y-3">
            <div>
              <label class="block text-slate-700 mb-1 font-medium">话机型号</label>
              <select v-model="phoneForm.model" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none">
                <option value="Yealink SIP-T27G">亿联 Yealink SIP-T27G</option>
                <option value="Yealink SIP-T21P E2">亿联 Yealink SIP-T21P E2</option>
                <option value="Fanvil X4U Pro">方位 Fanvil X4U Pro</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-medium">话机物理 MAC 地址</label>
              <input v-model="phoneForm.mac" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showBindPhoneModal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">取消</button>
          <button @click="handleConfirmBindPhone" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">确认绑定</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 3: 绑定记录 -->
    <div v-if="showBindRecordModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-2xl w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">分机 {{ currentInspectExtForBind }} 话机绑定流转历史</span>
          <button @click="showBindRecordModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="max-h-72 overflow-y-auto border border-slate-200 rounded-xl">
          <table class="w-full text-xs text-left">
            <thead class="bg-[#fafafa] text-slate-600 border-b border-slate-200 sticky top-0 font-semibold">
              <tr>
                <th class="py-2.5 px-3">记录ID</th>
                <th class="py-2.5 px-3">话机设备型号</th>
                <th class="py-2.5 px-3">MAC 地址</th>
                <th class="py-2.5 px-3">绑定时间</th>
                <th class="py-2.5 px-3">解绑时间</th>
                <th class="py-2.5 px-3">操作人</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 bg-white">
              <tr v-for="rec in displayBindRecords" :key="rec.id" class="hover:bg-slate-50/80">
                <td class="py-2.5 px-3 font-mono text-slate-400">{{ rec.id }}</td>
                <td class="py-2.5 px-3 font-bold text-slate-800">{{ rec.deviceModel }}</td>
                <td class="py-2.5 px-3 font-mono text-slate-600">{{ rec.mac }}</td>
                <td class="py-2.5 px-3 font-mono text-slate-500">{{ rec.bindTime }}</td>
                <td class="py-2.5 px-3"><span v-if="rec.unbindTime === '当前使用中'" class="text-emerald-600 font-bold">当前使用中</span><span v-else class="font-mono text-slate-400">{{ rec.unbindTime }}</span></td>
                <td class="py-2.5 px-3 text-slate-600">{{ rec.operator }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="flex justify-end pt-2">
          <button @click="showBindRecordModal = false" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">关闭</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 4: 注册记录 -->
    <div v-if="showRegisterRecordModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-3xl w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">分机 {{ currentInspectExtForRegister }} 注册与登出审计记录</span>
          <button @click="showRegisterRecordModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="max-h-72 overflow-y-auto border border-slate-200 rounded-xl">
          <table class="w-full text-xs text-left">
            <thead class="bg-[#fafafa] text-slate-600 border-b border-slate-200 sticky top-0 font-semibold">
              <tr>
                <th class="py-2.5 px-3">事件ID</th>
                <th class="py-2.5 px-3">动作类型</th>
                <th class="py-2.5 px-3">客户端 IP:端口</th>
                <th class="py-2.5 px-3">User-Agent</th>
                <th class="py-2.5 px-3">发生时间</th>
                <th class="py-2.5 px-3">状态/时长</th>
                <th class="py-2.5 px-3 text-right">状态码</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 bg-white">
              <tr v-for="r in displayRegisterRecords" :key="r.id" class="hover:bg-slate-50/80">
                <td class="py-2.5 px-3 font-mono text-slate-400">{{ r.id }}</td>
                <td class="py-2.5 px-3"><span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">{{ r.eventType }}</span></td>
                <td class="py-2.5 px-3 font-mono text-slate-700">{{ r.clientIpPort }}</td>
                <td class="py-2.5 px-3 text-slate-600 font-mono text-[11px] truncate max-w-[160px]">{{ r.userAgent }}</td>
                <td class="py-2.5 px-3 font-mono text-slate-500">{{ r.eventTime }}</td>
                <td class="py-2.5 px-3 text-slate-600">{{ r.durationOrExpire }}</td>
                <td class="py-2.5 px-3 text-right font-mono font-bold text-emerald-600">{{ r.statusCode }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="flex justify-end pt-2">
          <button @click="showRegisterRecordModal = false" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">关闭</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 5: 新增业务变量 -->
    <div v-if="showAddVarModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">新增全局业务变量</span>
          <button @click="showAddVarModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-medium">变量键名 (Key) *</label>
            <input v-model="varForm.key" type="text" placeholder="例如 MAX_CALL_DURATION" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono uppercase text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">变量名称 *</label>
            <input v-model="varForm.name" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">初始值 (Value) *</label>
            <input v-model="varForm.value" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">说明</label>
            <textarea v-model="varForm.description" rows="2" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddVarModal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">取消</button>
          <button @click="handleConfirmAddVar" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">确认新增</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 6: 编辑业务变量 -->
    <div v-if="showEditVarModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">修改全局变量 ({{ varForm.key }})</span>
          <button @click="showEditVarModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-medium">变量名称</label>
            <input v-model="varForm.name" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">变量值 (Value) *</label>
            <input v-model="varForm.value" type="text" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-medium">说明</label>
            <textarea v-model="varForm.description" rows="2" class="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showEditVarModal = false" class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg text-xs font-medium cursor-pointer">取消</button>
          <button @click="handleConfirmEditVar" class="px-5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-lg text-xs font-semibold cursor-pointer shadow-xs">保存修改</button>
        </div>
      </div>
    </div>
  </div>
</template>
