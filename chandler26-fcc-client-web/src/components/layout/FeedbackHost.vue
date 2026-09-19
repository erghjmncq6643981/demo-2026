<script setup lang="ts">
import { feedbackState, resolveConfirm, type FeedbackType } from '../../utils/feedback';

const iconFor = (type: FeedbackType): string => {
  switch (type) {
    case 'success': return '✓';
    case 'warning': return '!';
    case 'error': return '✕';
    default: return 'i';
  }
};

const barColorFor = (type: FeedbackType): string => {
  switch (type) {
    case 'success': return 'bg-emerald-500';
    case 'warning': return 'bg-amber-500';
    case 'error': return 'bg-rose-500';
    default: return 'bg-brand-500';
  }
};

const pillColorFor = (type: FeedbackType): string => {
  switch (type) {
    case 'success': return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    case 'warning': return 'bg-amber-50 text-amber-700 border-amber-200';
    case 'error': return 'bg-rose-50 text-rose-700 border-rose-200';
    default: return 'bg-indigo-50 text-brand-700 border-indigo-200';
  }
};
</script>

<template>
  <!-- Toast 浮层（右上角） -->
  <Teleport to="body">
    <div class="fixed top-4 right-4 z-[100] flex flex-col items-end gap-2 pointer-events-none">
      <TransitionGroup name="fcc-toast">
        <div
          v-for="t in feedbackState.toasts"
          :key="t.id"
          class="pointer-events-auto flex items-center gap-2.5 rounded-2xl border bg-white/95 backdrop-blur px-4 py-2.5 text-sm font-bold text-slate-800 shadow-popover max-w-sm"
        >
          <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[11px] font-black" :class="pillColorFor(t.type)">
            {{ iconFor(t.type) }}
          </span>
          <span>{{ t.message }}</span>
        </div>
      </TransitionGroup>
    </div>

    <!-- 确认框遮罩 -->
    <Transition name="fcc-fade">
      <div
        v-if="feedbackState.confirm.visible"
        class="fixed inset-0 z-[110] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm"
        @click.self="resolveConfirm(false)"
      >
        <div class="w-full max-w-md rounded-3xl border border-slate-100 bg-white p-6 shadow-popover">
          <div class="flex items-center gap-3">
            <span
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl text-lg font-black"
              :class="feedbackState.confirm.danger ? 'bg-rose-50 text-rose-600' : 'bg-amber-50 text-amber-600'"
            >
              {{ feedbackState.confirm.danger ? '⚠' : '?' }}
            </span>
            <h3 class="text-base font-black text-slate-900">{{ feedbackState.confirm.title }}</h3>
          </div>
          <p class="mt-4 whitespace-pre-line text-sm font-medium leading-relaxed text-slate-600">
            {{ feedbackState.confirm.message }}
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button
              @click="resolveConfirm(false)"
              class="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-bold text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
            >
              {{ feedbackState.confirm.cancelText }}
            </button>
            <button
              @click="resolveConfirm(true)"
              class="rounded-xl px-5 py-2.5 text-sm font-bold text-white shadow-sm transition"
              :class="feedbackState.confirm.danger ? 'bg-rose-600 hover:bg-rose-700' : 'bg-brand-600 hover:bg-brand-700'"
            >
              {{ feedbackState.confirm.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fcc-toast-enter-active,
.fcc-toast-leave-active {
  transition: all 0.25s ease;
}
.fcc-toast-enter-from,
.fcc-toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}

.fcc-fade-enter-active,
.fcc-fade-leave-active {
  transition: opacity 0.2s ease;
}
.fcc-fade-enter-from,
.fcc-fade-leave-to {
  opacity: 0;
}
</style>
