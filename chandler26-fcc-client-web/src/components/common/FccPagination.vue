<script setup lang="ts">
import { computed } from 'vue';
import {
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-vue-next';

interface Props {
  currentPage?: number;
  page?: number;
  pageSize?: number;
  size?: number;
  total: number;
  pageSizes?: number[];
  disabled?: boolean;
  showSizes?: boolean;
  showTotal?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  currentPage: 1,
  page: undefined,
  pageSize: 10,
  size: undefined,
  total: 0,
  pageSizes: () => [10, 20, 50, 100],
  disabled: false,
  showSizes: true,
  showTotal: false,
});

const emit = defineEmits<{
  (e: 'update:currentPage', page: number): void;
  (e: 'update:page', page: number): void;
  (e: 'update:pageSize', size: number): void;
  (e: 'update:size', size: number): void;
  (e: 'current-change', page: number): void;
  (e: 'page-change', page: number): void;
  (e: 'size-change', size: number): void;
  (e: 'change', page: number, size: number): void;
}>();

const activePage = computed(() => {
  return props.page !== undefined ? props.page : props.currentPage;
});

const activeSize = computed(() => {
  return props.size !== undefined ? props.size : props.pageSize;
});

const totalPages = computed(() => {
  return Math.max(1, Math.ceil((props.total || 0) / (activeSize.value || 10)));
});

type PagerItem =
  | { type: 'page'; page: number }
  | { type: 'prev-ellipsis' }
  | { type: 'next-ellipsis' };

/**
 * 核心分页逻辑：
 * 统一规格：10条/页 < ... p-2 p-1 p p+1 p+2 ....>
 * 以当前页 p 为核心严格展开前后各 2 页的滑动窗口 [p-2, p-1, p, p+1, p+2]
 */
const pagerItems = computed<PagerItem[]>(() => {
  const T = totalPages.value;
  const p = Math.min(Math.max(1, activePage.value), T);

  // 滑动窗口边界：当前页前后各 2 页
  const start = Math.max(1, p - 2);
  const end = Math.min(T, p + 2);

  const items: PagerItem[] = [];

  // 1. 前置页码与省略号处理
  if (start > 1) {
    items.push({ type: 'page', page: 1 });
    if (start > 2) {
      items.push({ type: 'prev-ellipsis' });
    }
  }

  // 2. 核心滑动窗口 [p-2, p-1, p, p+1, p+2] 范围内的有效页码
  for (let i = start; i <= end; i++) {
    items.push({ type: 'page', page: i });
  }

  // 3. 后置省略号与尾页处理
  if (end < T) {
    if (end < T - 1) {
      items.push({ type: 'next-ellipsis' });
    }
    items.push({ type: 'page', page: T });
  }

  return items;
});

function changePage(target: number) {
  if (props.disabled) return;
  const clamped = Math.min(Math.max(1, target), totalPages.value);
  if (clamped === activePage.value) return;

  emit('update:currentPage', clamped);
  emit('update:page', clamped);
  emit('current-change', clamped);
  emit('page-change', clamped);
  emit('change', clamped, activeSize.value);
}

function onSizeChange(event: Event) {
  if (props.disabled) return;
  const target = event.target as HTMLSelectElement;
  const newSize = Number(target.value);
  if (newSize === activeSize.value) return;

  const newTotalPages = Math.max(1, Math.ceil((props.total || 0) / newSize));
  const newPage = Math.min(activePage.value, newTotalPages);

  emit('update:pageSize', newSize);
  emit('update:size', newSize);
  emit('size-change', newSize);

  if (newPage !== activePage.value) {
    emit('update:currentPage', newPage);
    emit('update:page', newPage);
    emit('current-change', newPage);
    emit('page-change', newPage);
  }

  emit('change', newPage, newSize);
}
</script>

<template>
  <nav
    class="fcc-pagination flex items-center gap-1.5 sm:gap-2 select-none text-xs text-slate-600"
    aria-label="分页导航"
  >
    <!-- 可选：内嵌总数展示 -->
    <span v-if="showTotal" class="text-xs text-slate-500 font-medium mr-1">
      <slot name="total" :total="total">
        共 <strong class="text-slate-800 font-semibold font-mono">{{ total }}</strong> 条
      </slot>
    </span>

    <!-- 1. 每页条数选择器 (10条/页) -->
    <div v-if="showSizes" class="relative inline-flex items-center">
      <select
        :value="activeSize"
        :disabled="disabled"
        aria-label="每页显示条数"
        class="h-8 pl-2.5 pr-6 rounded-xl border border-slate-200 bg-white hover:border-slate-300 text-xs font-medium text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition cursor-pointer appearance-none disabled:opacity-40 disabled:cursor-not-allowed shadow-xs"
        @change="onSizeChange"
      >
        <option v-for="sizeOption in pageSizes" :key="sizeOption" :value="sizeOption">
          {{ sizeOption }}条/页
        </option>
      </select>
      <ChevronDown class="w-3.5 h-3.5 text-slate-400 absolute right-1.5 pointer-events-none" />
    </div>

    <!-- 2. 上一页按钮 (<) -->
    <button
      type="button"
      class="w-8 h-8 flex items-center justify-center rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 hover:text-slate-900 disabled:opacity-40 disabled:cursor-not-allowed transition cursor-pointer shadow-xs"
      :disabled="disabled || activePage <= 1"
      title="上一页"
      aria-label="上一页"
      @click="changePage(activePage - 1)"
    >
      <ChevronLeft class="w-4 h-4" />
    </button>

    <!-- 3. 页码列表与滑动窗口 (... p-2 p-1 p p+1 p+2 ...) -->
    <div class="flex items-center gap-1">
      <template v-for="(item, idx) in pagerItems" :key="idx">
        <!-- 向前快速跳转 ... -->
        <button
          v-if="item.type === 'prev-ellipsis'"
          type="button"
          class="w-8 h-8 flex items-center justify-center rounded-xl text-slate-400 hover:text-brand-600 hover:bg-brand-50 transition cursor-pointer group"
          title="向前 5 页"
          aria-label="向前 5 页"
          :disabled="disabled"
          @click="changePage(activePage - 5)"
        >
          <span class="group-hover:hidden text-xs font-bold tracking-widest leading-none">•••</span>
          <ChevronsLeft class="w-3.5 h-3.5 hidden group-hover:block text-brand-600" />
        </button>

        <!-- 向后快速跳转 ... -->
        <button
          v-else-if="item.type === 'next-ellipsis'"
          type="button"
          class="w-8 h-8 flex items-center justify-center rounded-xl text-slate-400 hover:text-brand-600 hover:bg-brand-50 transition cursor-pointer group"
          title="向后 5 页"
          aria-label="向后 5 页"
          :disabled="disabled"
          @click="changePage(activePage + 5)"
        >
          <span class="group-hover:hidden text-xs font-bold tracking-widest leading-none">•••</span>
          <ChevronsRight class="w-3.5 h-3.5 hidden group-hover:block text-brand-600" />
        </button>

        <!-- 具体页码按钮 -->
        <button
          v-else
          type="button"
          class="min-w-[32px] h-8 px-2 flex items-center justify-center rounded-xl text-xs font-mono font-medium transition cursor-pointer"
          :class="[
            item.page === activePage
              ? 'bg-brand-500 text-white font-bold border border-brand-500 shadow-xs shadow-brand-500/25 pointer-events-none'
              : 'border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 hover:border-slate-300 hover:text-brand-600 shadow-xs'
          ]"
          :disabled="disabled"
          :aria-current="item.page === activePage ? 'page' : undefined"
          :title="`第 ${item.page} 页`"
          @click="changePage(item.page)"
        >
          {{ item.page }}
        </button>
      </template>
    </div>

    <!-- 4. 下一页按钮 (>) -->
    <button
      type="button"
      class="w-8 h-8 flex items-center justify-center rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 hover:text-slate-900 disabled:opacity-40 disabled:cursor-not-allowed transition cursor-pointer shadow-xs"
      :disabled="disabled || activePage >= totalPages"
      title="下一页"
      aria-label="下一页"
      @click="changePage(activePage + 1)"
    >
      <ChevronRight class="w-4 h-4" />
    </button>
  </nav>
</template>
