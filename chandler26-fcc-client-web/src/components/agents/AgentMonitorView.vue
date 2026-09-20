<template>
  <div class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-100 shadow-card p-6 overflow-hidden">
    
    <!-- 顶栏状态与指标概览 -->
    <div class="pb-4 border-b border-slate-100 mb-4 shrink-0">
      <div class="flex items-center justify-between mb-3">
        <div>
          <h3 class="font-extrabold text-sm text-slate-900 flex items-center gap-2">
            <span>👥</span>
            <span>坐席实时态势 & 班长监控工作台</span>
            <span class="text-[10px] font-mono font-bold bg-brand-50 text-brand-700 px-2 py-0.5 rounded-full border border-brand-200">
              已开通坐席
            </span>
            <span
              class="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full border"
              :class="agentStore.isSupervisor ? 'bg-amber-50 text-amber-800 border-amber-200' : 'bg-slate-100 text-slate-600 border-slate-200'"
            >
              {{ agentStore.isSupervisor ? '👑 班长席' : '👤 普通坐席 (只读视界)' }}
            </span>
          </h3>
          <p class="text-xs text-slate-400 mt-0.5">
            仅本人状态来自本地工作台；其他坐席实时状态未知，班长干预尚未开放
          </p>
        </div>

        <div class="flex items-center gap-2">
          <button
            @click="refreshAgents"
            class="px-3 py-1.5 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl text-xs font-semibold text-slate-600 flex items-center gap-1.5 transition-all"
          >
            <span>🔄</span>
            <span>刷新态势</span>
          </button>
        </div>
      </div>

      <!-- 坐席态势统计胶囊条 -->
      <div class="grid grid-cols-5 gap-3 text-xs">
        <div class="bg-emerald-50/70 border border-emerald-100 p-3 rounded-2xl flex items-center justify-between">
          <span class="text-emerald-700 font-semibold">示闲就绪 (Ready)</span>
          <span class="font-mono font-extrabold text-lg text-emerald-800">{{ countReady }} 人</span>
        </div>
        <div class="bg-rose-50/70 border border-rose-100 p-3 rounded-2xl flex items-center justify-between">
          <span class="text-rose-700 font-semibold flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-rose-500 animate-ping"></span>
            通话中 (In Call)
          </span>
          <span class="font-mono font-extrabold text-lg text-rose-800">{{ countCalling }} 人</span>
        </div>
        <div class="bg-indigo-50/70 border border-indigo-100 p-3 rounded-2xl flex items-center justify-between">
          <span class="text-indigo-700 font-semibold">话后整理 (ACW)</span>
          <span class="font-mono font-extrabold text-lg text-indigo-800">{{ countAcw }} 人</span>
        </div>
        <div class="bg-amber-50/70 border border-amber-100 p-3 rounded-2xl flex items-center justify-between">
          <span class="text-amber-700 font-semibold">小休置忙 (Rest)</span>
          <span class="font-mono font-extrabold text-lg text-amber-800">{{ countRest }} 人</span>
        </div>
        <div class="bg-slate-50 border border-slate-200/60 p-3 rounded-2xl flex items-center justify-between">
          <span class="text-slate-500 font-semibold">离线 (Offline)</span>
          <span class="font-mono font-extrabold text-lg text-slate-700">未知</span>
        </div>
      </div>
    </div>

    <!-- 坐席矩阵与班长干预操作表格 -->
    <div class="flex-1 overflow-x-auto overflow-y-auto rounded-xl border border-slate-200/80 shadow-xs">
      <table class="w-full text-xs text-left">
        <thead class="bg-slate-100/95 text-slate-800 border-b border-slate-200 text-xs font-black tracking-wider uppercase sticky top-0 z-10 backdrop-blur-sm shadow-[0_1px_2px_rgba(0,0,0,0.03)]">
          <tr>
            <th class="py-3 px-3.5 text-slate-900 font-black whitespace-nowrap">坐席成员</th>
            <th class="py-3 px-2.5 text-slate-900 font-black whitespace-nowrap">工号</th>
            <th class="py-3 px-3.5 text-slate-900 font-black whitespace-nowrap">当前状态</th>
            <th class="py-3 px-4 text-slate-900 font-black whitespace-nowrap">正在处理 / 通话通道</th>
            <th class="py-3 px-2.5 text-slate-900 font-black whitespace-nowrap">持续时长</th>
            <th class="py-3 px-2.5 text-slate-900 font-black whitespace-nowrap">接听终端</th>
            <th class="py-3 px-4 text-right text-slate-900 font-black whitespace-nowrap">班长现场干预调度</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-50">
          <tr
            v-for="member in agentList"
            :key="member.workNo"
            :class="[
              'hover:bg-slate-50/80 transition-colors',
              member.isSupervisor ? 'bg-brand-50/15' : '',
              member.state === 'CALLING' ? 'border-l-4 border-l-rose-500 bg-rose-50/10' : ''
            ]"
          >
            <!-- 坐席成员 -->
            <td class="py-3 px-3 font-extrabold text-slate-900 flex items-center gap-2">
              <div
                :class="[
                  'w-7 h-7 rounded-xl flex items-center justify-center font-bold text-xs text-white',
                  member.isSupervisor ? 'bg-gradient-to-tr from-brand-600 to-indigo-600' : 'bg-slate-400'
                ]"
              >
                {{ member.name.substring(0, 1) }}
              </div>
              <div class="flex items-center gap-1.5">
                <span>{{ member.name }}</span>
                <span
                  v-if="member.isSupervisor"
                  class="text-brand-600 text-[10px] bg-brand-50 px-1.5 py-0.2 rounded-full border border-brand-200"
                >
                  班长主管 (当前席)
                </span>
              </div>
            </td>

            <!-- 工号 -->
            <td class="py-3 px-2 font-mono text-slate-500">{{ member.workNo }}</td>

            <!-- 状态 -->
            <td class="py-3 px-3">
              <span
                :class="[
                  'px-2.5 py-1 rounded-full font-extrabold text-[11px] flex items-center gap-1.5 w-max',
                  stateBadgeClass(member.state)
                ]"
              >
                <span :class="['w-2 h-2 rounded-full', stateDotClass(member.state)]"></span>
                {{ stateLabel(member.state) }}
              </span>
            </td>

            <!-- 处理通道 -->
            <td class="py-3 px-4">
              <div v-if="member.currentCall" class="font-mono font-bold text-slate-900 flex items-center gap-1.5">
                <span>{{ member.currentCall.phone }}</span>
                <span class="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.2 rounded font-normal">
                  {{ member.currentCall.tag }}
                </span>
              </div>
              <span v-else class="text-slate-400 font-mono text-[11px]">暂无通话事实</span>
            </td>

            <!-- 持续时长 -->
            <td class="py-3 px-2 font-mono" :class="member.state === 'CALLING' ? 'text-rose-600 font-bold' : 'text-slate-500'">
              {{ member.duration }}
            </td>

            <!-- 接听终端 -->
            <td class="py-3 px-2 font-semibold text-slate-700">
              {{ member.endpoint }}
            </td>

            <!-- 班长干预按键组 -->
            <td class="py-3 px-4 text-right">
              <div v-if="member.state === 'CALLING'" class="flex items-center justify-end gap-1.5">
                <!-- 仅班长主管具备干预权限 (钱丁君 901001) -->
                <template v-if="agentStore.isSupervisor">
                  <button
                    disabled
                    @click="handleIntervention('SPY', member)"
                    class="px-2.5 py-1 bg-indigo-50 hover:bg-brand-500 hover:text-white text-brand-600 font-bold rounded-lg text-[11px] transition-all shadow-2xs cursor-pointer"
                    title="静默监听坐席与客户通话"
                  >
                    🎧 监听
                  </button>
                  <button
                    disabled
                    @click="handleIntervention('COACH', member)"
                    class="px-2.5 py-1 bg-amber-50 hover:bg-amber-500 hover:text-white text-amber-700 font-bold rounded-lg text-[11px] transition-all shadow-2xs cursor-pointer"
                    title="仅向坐席单向指导，客户不可见"
                  >
                    🗣️ 耳语
                  </button>
                  <button
                    disabled
                    @click="handleIntervention('BARGE', member)"
                    class="px-2.5 py-1 bg-purple-50 hover:bg-purple-600 hover:text-white text-purple-700 font-bold rounded-lg text-[11px] transition-all shadow-2xs cursor-pointer"
                    title="插入通话转为三方联合通话"
                  >
                    👥 强插
                  </button>
                  <button
                    disabled
                    @click="handleIntervention('KILL', member)"
                    class="px-2.5 py-1 bg-rose-50 hover:bg-rose-600 hover:text-white text-rose-600 font-bold rounded-lg text-[11px] transition-all cursor-pointer"
                    title="强制挂断异常信道"
                  >
                    ✂️ 强拆
                  </button>
                </template>
                <div v-else class="text-slate-400 text-[11px] italic flex items-center gap-1">
                  <span>🔒 需班长权限干预</span>
                </div>
              </div>
              <div v-else class="flex items-center justify-end gap-1.5 text-slate-400 text-[11px]">
                <button
                  @click="$emit('viewAgentRecords', member.workNo)"
                  class="px-3 py-1 bg-slate-50 hover:bg-slate-100 text-slate-600 font-semibold rounded-lg transition-all cursor-pointer"
                >
                  查看历史话单
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 班长干预执行结果浮层提示 -->
    <div
      v-if="interventionMessage"
      class="fixed bottom-12 right-12 bg-slate-900/90 backdrop-blur-md text-white px-5 py-3 rounded-2xl shadow-2xl flex items-center gap-3 z-50 text-xs border border-white/10 animate-in slide-in-from-bottom-4 duration-200"
    >
      <span class="text-base">⚡️</span>
      <span class="font-medium">{{ interventionMessage }}</span>
      <button @click="interventionMessage = ''" class="text-slate-400 hover:text-white font-bold ml-2">✕</button>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { fetchAgentWhitelist } from '../../api/agentApi';
import { useAgentStore } from '../../stores/agentStore';
import { useCallStore } from '../../stores/callStore';

defineEmits<{ (e: 'viewAgentRecords', workNo: string): void }>();

const agentStore = useAgentStore();
const callStore = useCallStore();

interface AgentRow {
  name: string;
  workNo: string;
  isSupervisor: boolean;
  state: 'READY' | 'CALLING' | 'REST' | 'ACW' | 'UNKNOWN';
  currentCall?: { phone: string; tag: string };
  duration: string;
  endpoint: string;
}

const interventionMessage = ref('');

const agentList = ref<AgentRow[]>([]);

async function loadAgents() {
  try {
    const list = await fetchAgentWhitelist();
    if (list && list.length > 0) {
      agentList.value = list.map((item) => {
        const isCurrent = item.defaultWorkNo === agentStore.workNo;
        let st: 'READY' | 'CALLING' | 'REST' | 'ACW' | 'UNKNOWN' = 'UNKNOWN';
        let currentCallInfo: { phone: string; tag: string } | undefined = undefined;
        let dur = '—';

        if (isCurrent) {
          if (callStore.callState === 'CONNECTED' || callStore.callState === 'RINGING' || callStore.callState === 'CALLING') {
            st = 'CALLING';
            if (callStore.currentCall) {
              currentCallInfo = {
                phone: callStore.currentCall.callerNumber || '未知来电',
                tag: callStore.currentCall.ivrPath || '呼入进线'
              };
            }
            const mins = Math.floor(callStore.durationSeconds / 60).toString().padStart(2, '0');
            const secs = (callStore.durationSeconds % 60).toString().padStart(2, '0');
            dur = `00:${mins}:${secs}`;
          } else if (agentStore.status === 'REST') {
            st = 'REST';
          } else if (agentStore.status === 'ACW') {
            st = 'ACW';
          } else {
            st = 'READY';
          }
        }

        const endpointStr = isCurrent ? agentStore.endpoint : '未知';

        return {
          name: item.realName,
          workNo: item.defaultWorkNo,
          isSupervisor: item.isSupervisor,
          state: st,
          currentCall: currentCallInfo,
          duration: dur,
          endpoint: endpointStr
        };
      });
    }
  } catch (err) {
    console.error('Failed to load agent whitelist:', err);
  }
}

const countReady = computed(() => agentList.value.filter((a) => a.state === 'READY').length);
const countCalling = computed(() => agentList.value.filter((a) => a.state === 'CALLING').length);
const countRest = computed(() => agentList.value.filter((a) => a.state === 'REST').length);
const countAcw = computed(() => agentList.value.filter((a) => a.state === 'ACW').length);

onMounted(async () => {
  await loadAgents();
});

async function refreshAgents() {
  await loadAgents();
  interventionMessage.value = '坐席名单已刷新；他人实时状态尚未接入';
  setTimeout(() => {
    interventionMessage.value = '';
  }, 3000);
}

function handleIntervention(_type: string, _member: AgentRow) {
  interventionMessage.value = '班长干预尚未实现，操作未执行';
}

function stateLabel(state: AgentRow['state']) {
  if (state === 'UNKNOWN') return '状态未知';
  if (state === 'READY') return '示闲就绪 (Ready)';
  if (state === 'CALLING') return '通话中 (Busy)';
  if (state === 'REST') return '小休 (Rest)';
  return '话后整理 (ACW)';
}

function stateBadgeClass(state: AgentRow['state']) {
  if (state === 'READY') return 'bg-emerald-50 text-emerald-700';
  if (state === 'CALLING') return 'bg-rose-50 text-rose-700';
  if (state === 'REST') return 'bg-amber-50 text-amber-700';
  return 'bg-indigo-50 text-indigo-700';
}

function stateDotClass(state: AgentRow['state']) {
  if (state === 'READY') return 'bg-emerald-500 status-pulse-ready';
  if (state === 'CALLING') return 'bg-rose-500 animate-ping';
  if (state === 'REST') return 'bg-amber-500';
  return 'bg-indigo-500';
}
</script>
