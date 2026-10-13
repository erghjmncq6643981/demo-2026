<script setup lang="ts">
import {
  Copy,
  Check,
  Clock,
  Sliders,
  ChevronRight,
} from 'lucide-vue-next';
import FlowCanvas from './FlowCanvas.vue';
import { executionLabels, stageLabels } from '../model/stagedFlow';
import type { CallRecord } from '../../cdr/model/callRecord';
import { useCallJourney } from '../composables/useCallJourney';
const props = defineProps<{
  callId: string;
  cdr?: CallRecord | null;
}>();
defineEmits<{ 'play-audio': [] }>();
const { viewMode, selected, copiedKey, copyToClipboard, journeyCards, selectedAttempts, currentSelectedCard, flow, executions, error, loading, cursor, status, version, load } = useCallJourney(props);
</script>

<template>
  <div class="call-execution-panel flex flex-col bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-xs">
    <!-- 顶部状态栏与模式切换 -->
    <header class="px-5 py-3 bg-slate-50/90 border-b border-slate-200 flex items-center justify-between gap-4 flex-wrap shrink-0">
      <div class="flex items-center gap-2.5">
        <div class="w-2.5 h-2.5 rounded-full" :class="status === 'ENDED' ? 'bg-emerald-500 ring-2 ring-emerald-100' : 'bg-blue-500 animate-pulse'" />
        <span class="font-extrabold text-slate-900 text-sm">
          {{ version || '流程版本未知' }}
        </span>
        <span class="text-xs text-slate-600">{{ loading ? '加载中' : (status || '流程状态未知') }}</span>
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
        <el-button v-if="cdr?.recordingUrl" size="small" @click="$emit('play-audio')">播放已有录音</el-button>
      </div>
    </header>

    <!-- 错误或未加载提示 -->
    <div v-if="error" class="p-6 text-center text-rose-600 text-sm space-y-2">
      <p>{{ error }}</p>
      <el-button size="small" @click="load()">重新加载</el-button>
    </div>

    <p v-else-if="loading && !executions.length" role="status" class="p-6 text-slate-500">正在加载执行事实…</p>
    <p v-else-if="!executions.length" role="status" class="p-6 text-slate-500">暂无执行记录；不能据此判断接听、绑定或录音结果。</p>

    <!-- 主展示区 -->
    <div v-else class="journey-body flex-1 flex overflow-hidden min-h-0 bg-slate-50/40">
      <!-- ================= 模式 1: 业务旅程时间线 (呼叫中心管理员专属，动态匹配模型) ================= -->
      <div v-if="viewMode === 'journey'" class="flex-1 flex flex-col md:flex-row overflow-y-auto md:overflow-hidden">
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
                  <p class="text-[11px] text-slate-400">阶段定义：{{ card.subTitle }}</p>
                </div>
              </div>
              <span class="px-2 py-0.5 rounded-full text-xs font-bold border" :class="card.badgeClass">
                {{ card.statusText }}
              </span>
            </div>

            <p class="text-xs text-slate-500">{{ card.startedAt }} · {{ card.durationText }}</p>
          </article>
        </div>

        <!-- 右侧阶段深度抽屉 (Inspector Drawer) -->
        <aside class="w-full md:w-80 border-l border-slate-200 bg-white p-4 overflow-y-auto shrink-0 flex flex-col justify-between">
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

            <!-- 折叠技术审计事件 (默认折叠，不干扰日常呼叫中心管理员) -->
            <details class="text-xs group border border-slate-200 rounded-xl overflow-hidden bg-slate-50/50">
              <summary class="px-3 py-2.5 font-bold text-slate-600 cursor-pointer select-none hover:bg-slate-100 flex items-center justify-between">
                <span>🔧 原始技术事件与协议报文</span>
                <ChevronRight class="w-3.5 h-3.5 transition-transform group-open:rotate-90 text-slate-400" />
              </summary>
              <div class="p-3 bg-white border-t border-slate-200 space-y-3">
                <div v-for="attempt in selectedAttempts" :key="attempt.id" class="border-b border-slate-100 pb-2.5 last:border-0 last:pb-0">
                  <p v-if="attempt.errorCode" class="text-rose-700 break-all">错误代码：{{ attempt.errorCode }}</p>
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
