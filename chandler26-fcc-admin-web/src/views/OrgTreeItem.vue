<script setup lang="ts">
export interface OrgNode {
  id: string;
  name: string;
  level: number;
  icon?: 'company' | 'center' | 'group' | 'vip' | 'support' | 'phone';
  count?: number;
  disabled?: boolean;
  expanded?: boolean;
  children?: OrgNode[];
}

const props = defineProps<{
  node: OrgNode;
  selectedId: string;
  search?: string;
}>();

const emit = defineEmits<{
  (e: 'select', node: OrgNode): void;
  (e: 'contextmenu', event: MouseEvent, node: OrgNode): void;
}>();

const toggleExpand = (e: MouseEvent) => {
  e.stopPropagation();
  props.node.expanded = !props.node.expanded;
};

const handleSelect = () => {
  if (props.node.disabled) return;
  emit('select', props.node);
};

const handleContextMenu = (e: MouseEvent) => {
  emit('contextmenu', e, props.node);
};

const matchesSearch = (node: OrgNode, query: string): boolean => {
  if (!query) return true;
  if (node.name.toLowerCase().includes(query.toLowerCase())) return true;
  if (node.children) {
    return node.children.some(c => matchesSearch(c, query));
  }
  return false;
};
</script>

<template>
  <div v-if="!search || matchesSearch(node, search)" class="select-none text-sm leading-6 my-1">
    <!-- 当前节点行 -->
    <div
      @click="handleSelect"
      @contextmenu.prevent="handleContextMenu"
      class="group flex items-center gap-2.5 px-3 py-2.5 rounded-xl cursor-pointer transition-all"
      :class="[
        node.disabled ? 'text-slate-400 cursor-not-allowed opacity-60' : 'text-slate-800 hover:bg-slate-100/90',
        selectedId === node.id ? '!bg-blue-50 !text-[#1677ff] font-black shadow-xs border border-blue-200/60' : ''
      ]"
      :style="{ paddingLeft: `${node.level * 22 + 10}px` }"
      :title="`右键可管理: ${node.name}`"
    >
      <!-- 折叠/展开小箭头 -->
      <button
        v-if="node.children && node.children.length > 0"
        @click="toggleExpand"
        class="w-5 h-5 flex items-center justify-center text-slate-400 hover:text-slate-800 rounded transition shrink-0 cursor-pointer"
      >
        <svg
          class="w-4 h-4 transition-transform duration-150"
          :class="node.expanded ? 'rotate-90 text-slate-700' : 'text-slate-400'"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" />
        </svg>
      </button>
      <span v-else class="w-5 h-5 shrink-0"></span>

      <!-- 节点专属彩色图标 (加大至 w-5 h-5，鲜明直观) -->
      <span class="shrink-0 flex items-center justify-center text-base">
        <!-- 集团 / 公司 -->
        <svg v-if="node.icon === 'company' || node.level === 0" class="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
        <!-- 业务中心 / 部门 -->
        <svg v-else-if="node.icon === 'center' || (node.children && node.children.length > 0)" class="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        <!-- VIP 特殊组 -->
        <svg v-else-if="node.icon === 'vip'" class="w-5 h-5 text-purple-600" fill="currentColor" viewBox="0 0 20 20">
          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
        </svg>
        <!-- 普通技能组 -->
        <svg v-else class="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
        </svg>
      </span>

      <!-- 节点名称 (大号加粗字体，清晰舒适) -->
      <span class="truncate flex-1 font-bold text-sm tracking-normal">
        {{ node.name }}
      </span>

      <!-- 成员数量徽章 (字体清晰醒目) -->
      <span
        v-if="node.count !== undefined"
        class="text-xs px-2 py-0.5 rounded-full font-mono font-bold transition-colors shrink-0"
        :class="selectedId === node.id ? 'bg-blue-100 text-[#1677ff]' : 'bg-slate-100 text-slate-500 group-hover:bg-slate-200/80 group-hover:text-slate-700'"
      >
        {{ node.count }}人
      </span>

      <!-- 右键提示小标记 (hover 时显现) -->
      <span class="hidden group-hover:inline-block text-xs text-slate-400 font-bold shrink-0">
        ⋮
      </span>
    </div>

    <!-- 子节点递归展开 -->
    <div v-if="node.expanded && node.children && node.children.length > 0" class="relative">
      <OrgTreeItem
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :selected-id="selectedId"
        :search="search"
        @select="emit('select', $event)"
        @contextmenu="(e, n) => emit('contextmenu', e, n)"
      />
    </div>
  </div>
</template>

