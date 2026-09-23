<template>
  <div class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-200/80 shadow-card p-4 sm:p-6 min-h-0">
    
    <!-- 筛选与控制栏 -->
    <div class="flex flex-wrap items-center justify-between pb-4 border-b border-slate-100 mb-2 text-xs gap-3">
      <div class="flex flex-wrap items-center gap-2.5">
        <div class="flex items-center gap-1.5">
          <label class="font-bold text-slate-700">号码检索:</label>
          <input
            v-model="cdrStore.searchCaller"
            @keyup.enter="search"
            type="text"
            placeholder="输入主叫号码..."
            class="w-40 sm:w-48 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 text-slate-800 font-mono text-xs focus:outline-none focus:border-brand-500 focus:bg-white transition"
          />
        </div>

        <div class="flex items-center gap-1.5">
          <label class="font-bold text-slate-700">方向:</label>
          <select
            v-model="cdrStore.searchDirection"
            @change="search"
            class="bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 text-slate-800 text-xs focus:outline-none"
          >
            <option value="">全部方向</option>
            <option value="INBOUND">呼入 (Inbound)</option>
            <option value="OUTBOUND">呼出 (Outbound)</option>
          </select>
        </div>

        <button
          @click="search"
          class="px-4 py-1.5 bg-brand-500 hover:bg-brand-600 active:scale-95 text-white font-extrabold rounded-xl shadow-pill transition cursor-pointer"
        >
          查询
        </button>
        <button
          @click="resetSearch"
          class="px-3.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 font-bold rounded-xl transition cursor-pointer"
        >
          重置
        </button>
      </div>

      <div class="flex items-center gap-2 text-slate-500 text-xs font-mono">
        <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
        <span>MySQL 实时话单</span>
      </div>
    </div>

    <!-- 
      话单数据表格 
      列顺序严格对齐用户要求：主叫姓名/号码，被叫号码，方向，通话开始时间，通话结束时间，录音（时长），满意度，状态，操作
    -->
    <div class="flex-1 overflow-x-auto">
      <table class="w-full text-xs text-left">
        <thead class="text-slate-800 bg-slate-100/90 border-b border-slate-200 text-sm font-extrabold tracking-wide">
          <tr>
            <th class="py-3.5 px-3">主叫姓名/号码</th>
            <th class="py-3.5 px-3">被叫号码</th>
            <th class="py-3.5 px-2.5">方向</th>
            <th class="py-3.5 px-3">通话开始时间</th>
            <th class="py-3.5 px-3">通话结束时间</th>
            <th class="py-3.5 px-3">录音（时长）</th>
            <th class="py-3.5 px-3">满意度</th>
            <th class="py-3.5 px-3">状态</th>
            <th class="py-3.5 px-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody v-if="!cdrStore.isLoading && cdrStore.records.length > 0" class="divide-y divide-slate-50">
          <tr
            v-for="record in cdrStore.records"
            :key="record.id"
            class="hover:bg-slate-50/80 transition-colors"
          >
            <!-- 1. 主叫姓名/号码 -->
            <td class="py-3.5 px-3">
              <div v-if="record.callerName" class="font-bold text-slate-900">
                {{ record.callerName }}
              </div>
              <div class="font-mono text-slate-700 font-extrabold" :class="record.callerName ? 'text-[11px]' : ''">
                {{ record.caller }}
              </div>
            </td>

            <!-- 2. 被叫号码 -->
            <td class="py-3.5 px-3 font-mono text-slate-700">
              {{ record.callee }}
            </td>

            <!-- 3. 方向 -->
            <td class="py-3.5 px-2.5">
              <span
                :class="[
                  'px-2 py-0.5 rounded-md text-[10px] font-bold',
                  record.direction === 'INBOUND' ? 'bg-indigo-50 text-brand-700 border border-indigo-100' : 'bg-amber-50 text-amber-700 border border-amber-100'
                ]"
              >
                {{ record.direction === 'INBOUND' ? '呼入' : '外呼' }}
              </span>
            </td>

            <!-- 4. 通话开始时间 -->
            <td class="py-3.5 px-3 font-mono text-slate-600">
              {{ formatDateTime(record.answeredAt || record.initiatedAt) }}
            </td>

            <!-- 5. 通话结束时间 -->
            <td class="py-3.5 px-3 font-mono text-slate-600">
              {{ formatDateTime(record.endedAt) }}
            </td>

            <!-- 6. 录音（时长）: 去除“试听”文字，仅保留播放图标与时长 -->
            <td class="py-3.5 px-3">
              <div class="flex items-center gap-2">
                <button
                  @click="playRecordAudio(record)"
                  class="flex items-center gap-1 text-brand-600 hover:text-brand-800 font-bold bg-brand-50 hover:bg-brand-100 px-2 py-0.5 rounded-lg border border-brand-200 text-xs transition cursor-pointer shadow-2xs"
                  title="播放录音"
                >
                  <span class="text-[10px]">▶</span>
                  <span class="font-mono font-bold text-slate-800 text-[11px]">
                    {{ formatDurationMs(record.talkDurationMs) }}
                  </span>
                </button>
              </div>
            </td>

            <!-- 7. 满意度 -->
            <td class="py-3.5 px-3">
              <span v-if="record.evaluationScore" class="text-amber-500 font-extrabold text-[11px]">
                ★ {{ record.evaluationScore }}
              </span>
              <span v-else class="text-slate-300 text-[11px]">-</span>
            </td>

            <!-- 8. 状态 (历史话单严格只会有2个状态: 已接听 / 未接听，杜绝 CALLING) -->
            <td class="py-3.5 px-3">
              <span
                v-if="isAnswered(record)"
                class="px-2.5 py-0.5 rounded-full font-extrabold text-[11px] bg-emerald-50 text-emerald-700 border border-emerald-200 inline-flex items-center gap-1"
              >
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                <span>已接听</span>
              </span>
              <span
                v-else
                class="px-2.5 py-0.5 rounded-full font-extrabold text-[11px] bg-rose-50 text-rose-700 border border-rose-200 inline-flex items-center gap-1"
              >
                <span class="w-1.5 h-1.5 rounded-full bg-rose-400"></span>
                <span>未接听</span>
              </span>
            </td>

            <!-- 9. 操作 -->
            <td class="py-3.5 px-3 text-right">
              <div class="flex items-center justify-end gap-2">
                <button
                  @click="$emit('outbound', record.caller)"
                  class="px-3 py-1 rounded-full bg-indigo-50 hover:bg-brand-500 hover:text-white text-brand-600 font-extrabold text-xs transition shadow-2xs cursor-pointer"
                >
                  回拨
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 加载中动画 -->
      <div v-if="cdrStore.isLoading" class="py-16 text-center text-slate-400">
        <div class="w-8 h-8 rounded-full border-2 border-brand-500 border-t-transparent animate-spin mx-auto mb-2"></div>
        <p class="text-xs">正在从数据库加载最新话单...</p>
      </div>

      <!-- 空状态 -->
      <div
        v-if="!cdrStore.isLoading && cdrStore.records.length === 0"
        class="py-16 text-center text-slate-400"
      >
        <div class="text-3xl mb-2">📭</div>
        <p class="text-xs font-bold text-slate-600">暂无通话话单记录</p>
        <p class="text-[11px] text-slate-400 mt-1">通话结束后将自动同步落库呈现在此。</p>
      </div>
    </div>

    <!-- 分页器 -->
    <div class="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
      <span>共 {{ cdrStore.total }} 条真实话单记录</span>
      <div class="flex items-center gap-1.5 font-mono">
        <button
          @click="prevPage"
          :disabled="cdrStore.pageNum <= 1"
          class="w-7 h-7 rounded-xl border border-slate-200 flex items-center justify-center disabled:opacity-30 hover:bg-slate-50 cursor-pointer"
        >
          &lt;
        </button>
        <span class="px-2 font-bold text-slate-800">{{ cdrStore.pageNum }}</span>
        <button
          @click="nextPage"
          :disabled="cdrStore.pageNum * cdrStore.pageSize >= cdrStore.total"
          class="w-7 h-7 rounded-xl border border-slate-200 flex items-center justify-center disabled:opacity-30 hover:bg-slate-50 cursor-pointer"
        >
          &gt;
        </button>
      </div>
    </div>

    <!-- 录音试听播放器模态框 -->
    <div
      v-if="currentAudioRecord"
      class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center z-50 p-4 animate-in fade-in duration-200"
    >
      <div class="w-full max-w-lg bg-white rounded-3xl shadow-2xl p-6 border border-slate-100 animate-in zoom-in-95 duration-200">
        <!-- 头部 -->
        <div class="flex items-center justify-between pb-3.5 border-b border-slate-100">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-2xl bg-brand-50 text-brand-600 flex items-center justify-center font-bold text-lg">
              🎙️
            </div>
            <div>
              <h3 class="font-extrabold text-sm text-slate-900">通话录音试听回放</h3>
              <p class="text-[11px] text-slate-400 font-mono">
                会话: {{ currentAudioRecord.bizId }}
              </p>
            </div>
          </div>
          <button
            @click="closeAudioPlayer"
            class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-sm font-bold cursor-pointer"
          >
            ✕
          </button>
        </div>

        <!-- 录音详情卡片 -->
        <div class="my-4 p-4 bg-slate-50 rounded-2xl space-y-3">
          <div class="flex items-center justify-between text-xs">
            <div class="flex items-center gap-2">
              <span class="text-slate-400">主叫:</span>
              <strong class="font-mono text-slate-900">{{ currentAudioRecord.caller }}</strong>
            </div>
            <span class="text-slate-300">➔</span>
            <div class="flex items-center gap-2">
              <span class="text-slate-400">被叫/分机:</span>
              <strong class="font-mono text-slate-900">{{ currentAudioRecord.callee }}</strong>
            </div>
          </div>

          <div class="flex justify-between text-[11px] text-slate-500 font-mono">
            <span>开始时间: {{ formatDateTime(currentAudioRecord.answeredAt || currentAudioRecord.initiatedAt) }}</span>
            <span>总时长: {{ formatDurationMs(currentAudioRecord.talkDurationMs) }}</span>
          </div>

          <!-- 声波频谱动态示意 -->
          <div class="h-10 bg-white rounded-xl border border-slate-200/80 px-4 flex items-center justify-center gap-1">
            <span
              v-for="i in 28"
              :key="i"
              :class="[
                'w-1 rounded-full transition-all duration-300',
                isPlaying ? 'bg-brand-500' : 'bg-slate-200'
              ]"
              :style="{
                height: isPlaying ? `${Math.max(4, Math.sin((i + currentPlaySec * 4) * 0.5) * 24 + 12)}px` : '6px'
              }"
            ></span>
          </div>

          <!-- 播放进度条与时间 -->
          <div class="space-y-1">
            <div class="flex justify-between text-[11px] font-mono text-slate-500">
              <span class="text-brand-600 font-bold">{{ formatSeconds(currentPlaySec) }}</span>
              <span>{{ formatDurationMs(currentAudioRecord.talkDurationMs) }}</span>
            </div>
            <div class="w-full h-1.5 bg-slate-200 rounded-full overflow-hidden">
              <div
                class="h-full bg-brand-500 rounded-full transition-all"
                :style="{ width: `${playProgressPercent}%` }"
              ></div>
            </div>
          </div>
        </div>

        <!-- 音频驱动器 -->
        <audio
          ref="audioPlayerRef"
          :src="currentAudioUrl"
          @timeupdate="onAudioTimeUpdate"
          @ended="onAudioEnded"
          class="hidden"
        ></audio>

        <!-- 控制按键组 (去除倍速调整按钮) -->
        <div class="pt-2 flex items-center justify-end gap-3">
          <button
            @click="togglePlay"
            class="px-6 py-2 rounded-full bg-brand-500 hover:bg-brand-600 active:scale-95 text-white font-extrabold text-xs shadow-pill flex items-center gap-1.5 transition-all cursor-pointer"
          >
            <span>{{ isPlaying ? '⏸ 暂停' : '▶ 播放' }}</span>
          </button>
          <button
            @click="closeAudioPlayer"
            class="px-4 py-2 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-xs transition-all cursor-pointer"
          >
            关闭
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue';
import { useCdrStore } from '../../stores/cdrStore';
import type { CallCdrItem } from '../../types/cdr';

defineEmits<{ (e: 'outbound', phone: string): void }>();

const cdrStore = useCdrStore();

const currentAudioRecord = ref<CallCdrItem | null>(null);
const audioPlayerRef = ref<HTMLAudioElement | null>(null);
const isPlaying = ref(false);
const currentPlaySec = ref(0);
const playbackRate = ref(1.0);

const currentAudioUrl = computed(() => {
  if (!currentAudioRecord.value) return '';
  const id = currentAudioRecord.value.id;
  return `/api/admin/recordings/${id}/stream`;
});

const playProgressPercent = computed(() => {
  if (!currentAudioRecord.value?.talkDurationMs) return 0;
  const totalSec = Math.max(1, Math.floor(currentAudioRecord.value.talkDurationMs / 1000));
  return Math.min(100, (currentPlaySec.value / totalSec) * 100);
});

onMounted(() => {
  cdrStore.loadRecords(1);
});

onUnmounted(() => {
  if (audioPlayerRef.value) {
    audioPlayerRef.value.pause();
  }
});

function search() {
  cdrStore.loadRecords(1);
}

function resetSearch() {
  cdrStore.searchCaller = '';
  cdrStore.searchDirection = '';
  cdrStore.loadRecords(1);
}

function prevPage() {
  if (cdrStore.pageNum > 1) {
    cdrStore.loadRecords(cdrStore.pageNum - 1);
  }
}

function nextPage() {
  if (cdrStore.pageNum * cdrStore.pageSize < cdrStore.total) {
    cdrStore.loadRecords(cdrStore.pageNum + 1);
  }
}

function formatDateTime(dt?: string): string {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

function formatDurationMs(ms?: number): string {
  if (!ms || ms <= 0) return '00:00';
  const totalSec = Math.floor(ms / 1000);
  return formatSeconds(totalSec);
}

function formatSeconds(totalSec: number): string {
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

function playRecordAudio(record: CallCdrItem) {
  currentAudioRecord.value = record;
  currentPlaySec.value = 0;
  isPlaying.value = true;
  nextTick(() => {
    if (audioPlayerRef.value) {
      audioPlayerRef.value.currentTime = 0;
      audioPlayerRef.value.playbackRate = playbackRate.value;
      audioPlayerRef.value.play().catch((e) => {
        console.warn('Audio play auto-start error:', e);
      });
    }
  });
}

function togglePlay() {
  if (!audioPlayerRef.value) return;
  if (isPlaying.value) {
    audioPlayerRef.value.pause();
    isPlaying.value = false;
  } else {
    audioPlayerRef.value.playbackRate = playbackRate.value;
    audioPlayerRef.value.play().catch((e) => console.warn('Audio play error:', e));
    isPlaying.value = true;
  }
}

function onAudioTimeUpdate() {
  if (audioPlayerRef.value) {
    currentPlaySec.value = Math.floor(audioPlayerRef.value.currentTime);
  }
}

function onAudioEnded() {
  isPlaying.value = false;
  currentPlaySec.value = 0;
}

function closeAudioPlayer() {
  if (audioPlayerRef.value) {
    audioPlayerRef.value.pause();
  }
  isPlaying.value = false;
  currentAudioRecord.value = null;
}

/**
 * 通话历史话单状态归一化判断:
 * 参考 call-center-backend 规范，历史通话结果只会有2个状态: 已接听 / 未接听
 */
function isAnswered(record: any): boolean {
  if (!record) return false;
  return record.answerType === 'ANSWER'
    || record.status === 'ANSWERED'
    || record.status === 'COMPLETED'
    || record.status === 'NORMAL_END'
    || Boolean(record.answeredAt)
    || (Number(record.talkDurationMs) > 0)
    || (Number(record.audioDurationSec) > 0)
    || (Number(record.billsec) > 0);
}
</script>
