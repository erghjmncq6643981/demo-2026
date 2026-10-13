<script setup lang="ts">
import CallExecutionPanel from '../../flows/components/CallExecutionPanel.vue';
import type { CallRecord } from '../model/callRecord';
defineProps<{ selectedCdr: CallRecord }>();
const emit = defineEmits<{
  close: [];
  play: [title: string, phone: string, duration: string, url?: string, callId?: string];
}>();
function playAudio(title: string, phone: string, duration: string, url?: string, callId?: string) {
  emit('play', title, phone, duration, url, callId);
}
</script>
<template>
    <!-- 4. 弹窗: 全生命周期过程详情追踪 (Stage + Action 时序流水线卡片) -->
    <div
      
      class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4"
    >
      <div
        class="w-full max-w-4xl max-h-[92vh] bg-white rounded-3xl shadow-2xl border border-slate-100 overflow-hidden flex flex-col p-6 space-y-4"
      >
        <!-- 弹窗顶部标题 -->
        <div
          class="flex items-center justify-between pb-3 border-b border-slate-100 shrink-0"
        >
          <div class="flex items-center gap-3">
            <div
              class="w-9 h-9 rounded-xl bg-indigo-50 border border-indigo-100 text-[#1677ff] font-bold flex items-center justify-center text-base"
            >
              🧭
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-base font-black text-slate-900">
                  通话全生命周期过程详情追踪 (Call Journey Trace)
                </h2>
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border"
                  :class="
                    selectedCdr.status === 'ANSWERED'
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                      : 'bg-rose-50 text-rose-700 border-rose-200'
                  "
                >
                  {{
                    selectedCdr.status === "ANSWERED"
                      ? "● 通话已接通"
                      : "● 通话未接起/异常"
                  }}
                </span>
              </div>
              <p class="text-xs text-slate-400 mt-0.5 font-mono">
                流水号:
                <strong class="text-slate-700">{{ selectedCdr.id }}</strong>
                <span class="mx-1.5 text-slate-300">|</span>
                呼叫方向:
                <span class="font-bold text-slate-700">{{
                  selectedCdr.direction === "OUTBOUND"
                    ? "外呼"
                    : selectedCdr.direction === "INBOUND"
                      ? "呼入"
                      : "内线"
                }}</span>
                <span class="mx-1.5 text-slate-300">|</span>
                引擎: FreeSWITCH
              </p>
            </div>
          </div>
          <button
            @click="emit('close')"
            class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-sm font-bold cursor-pointer"
          >
            ✕
          </button>
        </div>

        <!-- 通话关键元数据 Bento 汇总卡片 -->
        <div class="grid grid-cols-4 gap-3 text-xs shrink-0">
          <div
            class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1"
          >
            <span class="text-slate-400 text-[11px]">主叫客户</span>
            <div class="font-bold text-slate-900 truncate">
              {{ selectedCdr.callerName }}
            </div>
            <div class="font-mono text-slate-500 text-[11px]">
              {{ selectedCdr.callerPhone }} ({{ selectedCdr.carrier }})
            </div>
          </div>

          <div
            class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1"
          >
            <span class="text-slate-400 text-[11px]">服务坐席</span>
            <div class="font-bold text-slate-900 truncate">
              {{ selectedCdr.agentName }}
            </div>
            <div class="font-mono text-slate-500 text-[11px]">
              工号: {{ selectedCdr.agentWorkNo }}
            </div>
          </div>

          <div
            class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1"
          >
            <span class="text-slate-400 text-[11px]">时延与录音</span>
            <div class="flex items-center gap-1.5 font-bold text-slate-900">
              <span>振铃: {{ selectedCdr.ringDuration }}</span>
            </div>
            <div class="font-mono text-brand-600 font-bold text-[11px]">
              录音: {{ selectedCdr.audioDuration || "无录音" }}
            </div>
          </div>

          <div
            class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1"
          >
            <span class="text-slate-400 text-[11px]">执行流程与评价</span>
            <div
              class="font-bold text-indigo-700 truncate"
              :title="selectedCdr.flowCode || selectedCdr.modelType"
            >
              {{
                selectedCdr.flowCode || selectedCdr.modelType || "未记录流程"
              }}
            </div>
            <div class="font-mono text-[11px] text-amber-600 font-bold">
              {{
                selectedCdr.satisfactionScore
                  ? `客户评价: ${selectedCdr.satisfactionScore} 星 ★★★★★`
                  : "未评价 / 异常挂断"
              }}
            </div>
          </div>
        </div>

        <CallExecutionPanel
          v-if="selectedCdr.rawId"
          class="flex-1 min-h-0"
          :call-id="selectedCdr.rawId"
          :cdr="selectedCdr"
          @play-audio="
            playAudio(
              selectedCdr.callerName,
              selectedCdr.callerPhone,
              selectedCdr.audioDuration || '',
              selectedCdr.recordingUrl,
              selectedCdr.rawId
            )
          "
        />

        <!-- 弹窗底部操作栏 -->
        <div
          class="pt-3 border-t border-slate-100 flex items-center justify-between shrink-0"
        >
          <div class="flex items-center gap-2">
            <button
              v-if="selectedCdr.recordingUrl"
              @click="
                playAudio(
                  selectedCdr.callerName,
                  selectedCdr.callerPhone,
                  selectedCdr.audioDuration || '',
                  selectedCdr.recordingUrl,
                  selectedCdr.rawId,
                )
              "
              class="px-4 py-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-xs font-bold rounded-xl flex items-center gap-1.5 transition cursor-pointer"
            >
              <span>▶️</span>
              <span>试听双轨录音 ({{ selectedCdr.audioDuration }})</span>
            </button>
            <span
              v-else-if="selectedCdr.audioDuration"
              class="text-xs text-slate-400"
            >
              该通话未产生录音文件（通话 {{ selectedCdr.audioDuration }}）
            </span>
          </div>

          <button
            @click="emit('close')"
            class="px-6 py-2 bg-[#1677ff] hover:bg-blue-600 text-white text-xs font-bold rounded-xl cursor-pointer shadow-xs transition"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
</template>
