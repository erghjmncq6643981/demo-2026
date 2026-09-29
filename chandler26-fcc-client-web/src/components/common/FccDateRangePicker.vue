<script setup lang="ts">
import { ref, watch } from 'vue';
import { Calendar } from 'lucide-vue-next';

interface Props {
  modelValue?: [string, string] | null;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  disabled: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: [string, string] | null): void;
  (e: 'change', value: [string, string] | null): void;
}>();

const internalStart = ref('');
const internalEnd = ref('');
const selectedShortcut = ref('');

// Format Date object to "YYYY-MM-DDTHH:mm" for datetime-local input
function formatForInput(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  const y = d.getFullYear();
  const m = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const h = pad(d.getHours());
  const min = pad(d.getMinutes());
  return `${y}-${m}-${day}T${h}:${min}`;
}

// Convert "YYYY-MM-DDTHH:mm" or "YYYY-MM-DD HH:mm:ss" to ISO string "YYYY-MM-DDTHH:mm:ss"
function toBackendIso(val: string, isEnd = false): string {
  if (!val) return '';
  const trimmed = val.trim().replace(' ', 'T');
  if (trimmed.length === 16) {
    return isEnd ? `${trimmed}:59` : `${trimmed}:00`;
  }
  return trimmed;
}

watch(
  () => props.modelValue,
  (val) => {
    if (val && val.length === 2) {
      internalStart.value = val[0] ? val[0].substring(0, 16).replace(' ', 'T') : '';
      internalEnd.value = val[1] ? val[1].substring(0, 16).replace(' ', 'T') : '';
    } else {
      internalStart.value = '';
      internalEnd.value = '';
      selectedShortcut.value = '';
    }
  },
  { immediate: true }
);

function notifyChange() {
  if (internalStart.value || internalEnd.value) {
    const s = toBackendIso(internalStart.value, false);
    const e = toBackendIso(internalEnd.value, true);
    const res: [string, string] = [s, e];
    emit('update:modelValue', res);
    emit('change', res);
  } else {
    emit('update:modelValue', null);
    emit('change', null);
  }
}

function onInputChange() {
  selectedShortcut.value = '';
  notifyChange();
}

function applyShortcut() {
  const code = selectedShortcut.value;
  if (!code) return;

  const now = new Date();
  if (code === 'today') {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
    internalStart.value = formatForInput(start);
    internalEnd.value = formatForInput(end);
  } else if (code === 'yesterday') {
    const yStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 0, 0, 0);
    const yEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 23, 59, 59);
    internalStart.value = formatForInput(yStart);
    internalEnd.value = formatForInput(yEnd);
  } else if (code === 'last3days') {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 2, 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
    internalStart.value = formatForInput(start);
    internalEnd.value = formatForInput(end);
  } else if (code === 'last7days') {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6, 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
    internalStart.value = formatForInput(start);
    internalEnd.value = formatForInput(end);
  }
  notifyChange();
}

function clear() {
  internalStart.value = '';
  internalEnd.value = '';
  selectedShortcut.value = '';
  notifyChange();
}
</script>

<template>
  <div class="inline-flex items-center gap-1.5">
    <div
      class="inline-flex items-center bg-slate-50 border border-slate-200 rounded-xl px-2.5 py-1 text-slate-800 text-xs focus-within:border-brand-500 focus-within:bg-white transition shadow-xs"
      :class="{ 'opacity-50 pointer-events-none': disabled }"
    >
      <Calendar class="w-3.5 h-3.5 text-slate-400 mr-1.5 shrink-0" />
      <input
        type="datetime-local"
        v-model="internalStart"
        :disabled="disabled"
        @change="onInputChange"
        class="bg-transparent text-slate-700 text-xs font-mono focus:outline-none cursor-pointer"
        title="开始时间"
      />
      <span class="text-slate-400 px-1 font-bold text-xs">至</span>
      <input
        type="datetime-local"
        v-model="internalEnd"
        :disabled="disabled"
        @change="onInputChange"
        class="bg-transparent text-slate-700 text-xs font-mono focus:outline-none cursor-pointer"
        title="结束时间"
      />
      <button
        v-if="internalStart || internalEnd"
        @click="clear"
        type="button"
        class="ml-1 text-slate-400 hover:text-slate-600 rounded-full w-4 h-4 flex items-center justify-center text-[10px] cursor-pointer"
        title="清空时间"
      >
        ✕
      </button>
    </div>

    <!-- 快捷区间选择 -->
    <select
      v-model="selectedShortcut"
      :disabled="disabled"
      @change="applyShortcut"
      aria-label="时间快捷区间"
      class="h-[30px] px-2 bg-slate-50 border border-slate-200 rounded-xl text-slate-700 text-xs font-medium focus:outline-none focus:border-brand-500 cursor-pointer shadow-xs"
    >
      <option value="">快捷区间</option>
      <option value="today">今天</option>
      <option value="yesterday">昨天</option>
      <option value="last3days">近3天</option>
      <option value="last7days">近7天</option>
    </select>
  </div>
</template>
