<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';
import { toastError } from '../../../utils/feedback';
import {
  buildEndpointOptions,
  endpointKey,
  type EndpointOption,
} from '../model/endpointSelection';
import {
  Headphones,
  Phone,
  Smartphone,
  ChevronDown,
  Check,
  Loader2,
} from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    layout?: 'horizontal' | 'vertical';
  }>(),
  {
    layout: 'horizontal',
  },
);

const agentStore = useAgentStore();
const callStore = useCallStore();

const isOpen = ref(false);
const selectorRef = ref<HTMLElement | null>(null);

const options = computed(() =>
  buildEndpointOptions({
    workNo: agentStore.workNo,
    webrtcWorkNo: agentStore.webrtcWorkNo,
    sipExtensions: agentStore.availableSipExtensions,
    mobilePhone: agentStore.boundMobile,
  }),
);

const currentKey = computed(() => endpointKey(agentStore.endpoint, agentStore.extension));

const currentOption = computed(() => {
  return options.value.find((opt) => opt.key === currentKey.value) || options.value[0];
});

const callBlocksSwitch = computed(() => callStore.callState !== 'IDLE');
const disabled = computed(
  () => agentStore.endpointsLoading || agentStore.endpointSwitching || callBlocksSwitch.value,
);

function toggleDropdown() {
  if (disabled.value) {
    if (callBlocksSwitch.value) {
      toastError('当前通话尚未结束，不能切换接听方式');
    }
    return;
  }
  isOpen.value = !isOpen.value;
}

async function selectOption(option: EndpointOption) {
  if (option.disabled || option.key === currentKey.value) {
    isOpen.value = false;
    return;
  }
  if (callBlocksSwitch.value) {
    toastError('当前通话尚未结束，不能切换接听方式');
    isOpen.value = false;
    return;
  }
  isOpen.value = false;
  try {
    await agentStore.switchEndpoint(option.type, option.value);
  } catch {
    // Store owns error state
  }
}

function handlePointerDown(event: PointerEvent) {
  if (selectorRef.value && !selectorRef.value.contains(event.target as Node)) {
    isOpen.value = false;
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && isOpen.value) {
    isOpen.value = false;
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown);
  document.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener('pointerdown', handlePointerDown);
  document.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <!-- Horizontal layout (default): label on the left -->
  <div
    ref="selectorRef"
    v-if="props.layout === 'horizontal'"
    class="relative flex items-center gap-2.5 text-xs select-none"
  >
    <span class="font-bold text-slate-500 shrink-0">接听方式</span>

    <!-- 高颜值自定义触发按钮 -->
    <button
      type="button"
      :disabled="disabled"
      @click="toggleDropdown"
      class="group relative h-9 px-3 bg-white hover:bg-slate-50/90 border rounded-xl flex items-center gap-2 transition-all duration-150 cursor-pointer shadow-2xs"
      :class="[
        isOpen
          ? 'border-indigo-500 ring-2 ring-indigo-500/15 bg-indigo-50/20'
          : 'border-slate-200 hover:border-slate-300',
        disabled ? 'opacity-60 cursor-not-allowed bg-slate-50' : '',
      ]"
    >
      <!-- 图标徽章 -->
      <span
        class="w-5 h-5 rounded-lg flex items-center justify-center shrink-0 transition-colors"
        :class="[
          currentOption?.type === 'WEBRTC'
            ? 'bg-indigo-50 text-indigo-600'
            : currentOption?.type === 'SIP'
              ? 'bg-emerald-50 text-emerald-600'
              : 'bg-slate-100 text-slate-500',
        ]"
      >
        <Headphones v-if="currentOption?.type === 'WEBRTC'" class="w-3.5 h-3.5" />
        <Phone v-else-if="currentOption?.type === 'SIP'" class="w-3.5 h-3.5" />
        <Smartphone v-else class="w-3.5 h-3.5" />
      </span>

      <!-- 标签与分机信息 -->
      <div class="flex items-center gap-1.5 text-left font-sans">
        <span class="font-bold text-slate-800">{{ currentOption?.label || '选择接听方式' }}</span>
        <span class="text-slate-300">·</span>
        <span class="text-[11px] font-mono text-slate-500 font-medium">{{ currentOption?.detail }}</span>
      </div>

      <!-- 切换中 / 展开箭头 -->
      <Loader2
        v-if="agentStore.endpointSwitching"
        class="w-3.5 h-3.5 text-indigo-600 animate-spin ml-1 shrink-0"
      />
      <ChevronDown
        v-else
        class="w-3.5 h-3.5 text-slate-400 group-hover:text-slate-600 transition-transform duration-200 shrink-0"
        :class="{ 'rotate-180 text-indigo-600': isOpen }"
      />
    </button>

    <!-- 下拉菜单浮层 -->
    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="transform scale-95 opacity-0 -translate-y-1"
      enter-to-class="transform scale-100 opacity-100 translate-y-0"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="transform scale-100 opacity-100 translate-y-0"
      leave-to-class="transform scale-95 opacity-0 -translate-y-1"
    >
      <div
        v-if="isOpen"
        class="absolute left-[54px] top-full mt-1.5 z-50 min-w-[270px] bg-white/95 backdrop-blur-md border border-slate-200/90 rounded-2xl p-1.5 shadow-xl shadow-slate-900/10 space-y-1"
      >
        <div class="px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-400">
          可用接听终端
        </div>

        <div
          v-for="option in options"
          :key="option.key"
          @click="selectOption(option)"
          class="flex items-center justify-between p-2 rounded-xl transition-all cursor-pointer select-none"
          :class="[
            option.key === currentKey
              ? 'bg-indigo-50/80 text-indigo-900 font-bold'
              : option.disabled
                ? 'opacity-40 cursor-not-allowed hover:bg-transparent'
                : 'hover:bg-slate-50 text-slate-700',
          ]"
        >
          <div class="flex items-center gap-2.5">
            <span
              class="w-6 h-6 rounded-lg flex items-center justify-center shrink-0"
              :class="[
                option.type === 'WEBRTC'
                  ? 'bg-indigo-100/70 text-indigo-600'
                  : option.type === 'SIP'
                    ? 'bg-emerald-100/70 text-emerald-600'
                    : 'bg-slate-100 text-slate-400',
              ]"
            >
              <Headphones v-if="option.type === 'WEBRTC'" class="w-3.5 h-3.5" />
              <Phone v-else-if="option.type === 'SIP'" class="w-3.5 h-3.5" />
              <Smartphone v-else class="w-3.5 h-3.5" />
            </span>
            <div class="flex flex-col text-left">
              <span class="text-xs font-bold leading-tight">{{ option.label }}</span>
              <span class="text-[11px] font-mono text-slate-400 font-normal leading-tight">{{ option.detail }}</span>
            </div>
          </div>

          <div class="flex items-center ml-3 shrink-0">
            <Check v-if="option.key === currentKey" class="w-4 h-4 text-indigo-600 stroke-[2.5]" />
            <span
              v-else-if="option.disabled"
              class="px-1.5 py-0.5 rounded text-[10px] bg-slate-100 text-slate-400 font-medium"
            >
              未开放
            </span>
          </div>
        </div>
      </div>
    </transition>

    <button
      v-if="agentStore.endpointError"
      type="button"
      class="text-xs font-bold text-rose-600 underline hover:text-rose-700 cursor-pointer whitespace-nowrap"
      :disabled="agentStore.endpointsLoading"
      @click="agentStore.loadEndpoints()"
    >
      重试
    </button>
  </div>

  <!-- Vertical layout: heading on top -->
  <div ref="selectorRef" v-else class="min-w-full select-none text-xs">
    <div class="flex items-center justify-between mb-1.5 text-slate-600 font-extrabold">
      <span>接听方式</span>
      <button
        v-if="agentStore.endpointError"
        type="button"
        class="text-brand-600 font-bold underline cursor-pointer"
        :disabled="agentStore.endpointsLoading"
        @click="agentStore.loadEndpoints()"
      >
        重试
      </button>
    </div>

    <div class="relative">
      <button
        type="button"
        :disabled="disabled"
        @click="toggleDropdown"
        class="w-full h-10 px-3 bg-white hover:bg-slate-50/90 border rounded-xl flex items-center justify-between gap-2 transition-all cursor-pointer shadow-2xs"
        :class="[
          isOpen
            ? 'border-indigo-500 ring-2 ring-indigo-500/15 bg-indigo-50/20'
            : 'border-slate-200 hover:border-slate-300',
          disabled ? 'opacity-60 cursor-not-allowed bg-slate-50' : '',
        ]"
      >
        <div class="flex items-center gap-2">
          <span
            class="w-5 h-5 rounded-lg flex items-center justify-center shrink-0"
            :class="[
              currentOption?.type === 'WEBRTC'
                ? 'bg-indigo-50 text-indigo-600'
                : currentOption?.type === 'SIP'
                  ? 'bg-emerald-50 text-emerald-600'
                  : 'bg-slate-100 text-slate-500',
            ]"
          >
            <Headphones v-if="currentOption?.type === 'WEBRTC'" class="w-3.5 h-3.5" />
            <Phone v-else-if="currentOption?.type === 'SIP'" class="w-3.5 h-3.5" />
            <Smartphone v-else class="w-3.5 h-3.5" />
          </span>
          <span class="font-bold text-slate-800">{{ currentOption?.label }}</span>
          <span class="text-slate-300">·</span>
          <span class="text-[11px] font-mono text-slate-500">{{ currentOption?.detail }}</span>
        </div>

        <Loader2
          v-if="agentStore.endpointSwitching"
          class="w-3.5 h-3.5 text-indigo-600 animate-spin shrink-0"
        />
        <ChevronDown
          v-else
          class="w-3.5 h-3.5 text-slate-400 transition-transform duration-200 shrink-0"
          :class="{ 'rotate-180 text-indigo-600': isOpen }"
        />
      </button>

      <!-- 下拉浮层 -->
      <transition
        enter-active-class="transition duration-150 ease-out"
        enter-from-class="transform scale-95 opacity-0 -translate-y-1"
        enter-to-class="transform scale-100 opacity-100 translate-y-0"
        leave-active-class="transition duration-100 ease-in"
        leave-from-class="transform scale-100 opacity-100 translate-y-0"
        leave-to-class="transform scale-95 opacity-0 -translate-y-1"
      >
        <div
          v-if="isOpen"
          class="absolute left-0 right-0 top-full mt-1.5 z-50 bg-white/95 backdrop-blur-md border border-slate-200/90 rounded-2xl p-1.5 shadow-xl shadow-slate-900/10 space-y-1"
        >
          <div
            v-for="option in options"
            :key="option.key"
            @click="selectOption(option)"
            class="flex items-center justify-between p-2 rounded-xl transition-all cursor-pointer select-none"
            :class="[
              option.key === currentKey
                ? 'bg-indigo-50/80 text-indigo-900 font-bold'
                : option.disabled
                  ? 'opacity-40 cursor-not-allowed hover:bg-transparent'
                  : 'hover:bg-slate-50 text-slate-700',
            ]"
          >
            <div class="flex items-center gap-2.5">
              <span
                class="w-6 h-6 rounded-lg flex items-center justify-center shrink-0"
                :class="[
                  option.type === 'WEBRTC'
                    ? 'bg-indigo-100/70 text-indigo-600'
                    : option.type === 'SIP'
                      ? 'bg-emerald-100/70 text-emerald-600'
                      : 'bg-slate-100 text-slate-400',
                ]"
              >
                <Headphones v-if="option.type === 'WEBRTC'" class="w-3.5 h-3.5" />
                <Phone v-else-if="option.type === 'SIP'" class="w-3.5 h-3.5" />
                <Smartphone v-else class="w-3.5 h-3.5" />
              </span>
              <div class="flex flex-col text-left">
                <span class="text-xs font-bold leading-tight">{{ option.label }}</span>
                <span class="text-[11px] font-mono text-slate-400 font-normal leading-tight">{{ option.detail }}</span>
              </div>
            </div>

            <div class="flex items-center ml-3 shrink-0">
              <Check v-if="option.key === currentKey" class="w-4 h-4 text-indigo-600 stroke-[2.5]" />
              <span
                v-else-if="option.disabled"
                class="px-1.5 py-0.5 rounded text-[10px] bg-slate-100 text-slate-400 font-medium"
              >
                未开放
              </span>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <p v-if="callBlocksSwitch" class="mt-1 text-[11px] text-slate-400">通话或话后整理期间不可切换</p>
    <p v-else-if="agentStore.endpointError" class="mt-1 text-[11px] text-rose-600" role="alert">
      {{ agentStore.endpointError }}
    </p>
  </div>
</template>
