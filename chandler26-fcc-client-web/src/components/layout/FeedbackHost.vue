<script setup lang="ts">
import { computed } from 'vue';
import { feedbackState, resolveConfirm, type FeedbackType } from '../../utils/feedback';
import { LogOut, AlertTriangle, AlertCircle, Info, Check, X } from 'lucide-vue-next';

const pillColorFor = (type: FeedbackType): string => {
  switch (type) {
    case 'success': return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    case 'warning': return 'bg-amber-50 text-amber-700 border-amber-200';
    case 'error': return 'bg-rose-50 text-rose-700 border-rose-200';
    default: return 'bg-indigo-50 text-indigo-700 border-indigo-200';
  }
};

const confirmIconBadgeClass = computed(() => {
  const icon = feedbackState.confirm.iconType;
  if (icon === 'logout' || icon === 'danger' || feedbackState.confirm.danger) {
    return 'bg-rose-50 border-rose-100 text-rose-600';
  }
  if (icon === 'warning') {
    return 'bg-amber-50 border-amber-100 text-amber-600';
  }
  return 'bg-indigo-50 border-indigo-100 text-indigo-600';
});
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
            <Check v-if="t.type === 'success'" class="w-3 h-3 stroke-[3]" />
            <AlertCircle v-else-if="t.type === 'warning'" class="w-3 h-3 stroke-[2.5]" />
            <X v-else-if="t.type === 'error'" class="w-3 h-3 stroke-[3]" />
            <Info v-else class="w-3 h-3 stroke-[2.5]" />
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
        <div class="w-full max-w-md rounded-3xl border border-slate-100 bg-white p-6 shadow-2xl">
          <div class="flex items-center justify-between pb-3.5 border-b border-slate-100">
            <div class="flex items-center gap-3">
              <span
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border shadow-2xs"
                :class="confirmIconBadgeClass"
              >
                <LogOut v-if="feedbackState.confirm.iconType === 'logout'" class="w-5 h-5 text-rose-600" />
                <AlertTriangle v-else-if="feedbackState.confirm.iconType === 'danger' || feedbackState.confirm.danger" class="w-5 h-5 text-rose-600" />
                <AlertCircle v-else-if="feedbackState.confirm.iconType === 'warning'" class="w-5 h-5 text-amber-600" />
                <Info v-else class="w-5 h-5 text-indigo-600" />
              </span>
              <div>
                <h3 class="text-base font-extrabold text-slate-900 tracking-tight">{{ feedbackState.confirm.title }}</h3>
              </div>
            </div>
            <button
              type="button"
              @click="resolveConfirm(false)"
              class="w-8 h-8 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 flex items-center justify-center transition cursor-pointer"
              title="关闭"
            >
              <X class="w-4 h-4" />
            </button>
          </div>
          
          <p class="mt-4 whitespace-pre-line text-sm font-medium leading-relaxed text-slate-600">
            {{ feedbackState.confirm.message }}
          </p>
          
          <div class="mt-6 flex justify-end gap-3">
            <button
              type="button"
              @click="resolveConfirm(false)"
              class="rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-xs font-bold text-slate-600 transition hover:bg-slate-50 hover:text-slate-900 active:scale-95 cursor-pointer shadow-2xs"
            >
              {{ feedbackState.confirm.cancelText }}
            </button>
            <button
              type="button"
              @click="resolveConfirm(true)"
              class="rounded-xl px-5 py-2.5 text-xs font-extrabold text-white transition active:scale-95 cursor-pointer shadow-sm"
              :class="(feedbackState.confirm.danger || feedbackState.confirm.iconType === 'logout') ? 'bg-rose-600 hover:bg-rose-700 shadow-rose-600/25' : 'bg-indigo-600 hover:bg-indigo-700 shadow-indigo-600/25'"
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
