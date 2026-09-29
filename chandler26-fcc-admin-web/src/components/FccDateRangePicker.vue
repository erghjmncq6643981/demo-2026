<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  modelValue?: [string, string] | null;
  type?: 'datetimerange' | 'daterange';
  startPlaceholder?: string;
  endPlaceholder?: string;
  valueFormat?: string;
  format?: string;
  disabled?: boolean;
  clearable?: boolean;
  shortcuts?: boolean | Array<{ text: string; value: () => [Date, Date] }>;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  type: 'datetimerange',
  startPlaceholder: '开始时间',
  endPlaceholder: '结束时间',
  valueFormat: 'YYYY-MM-DD HH:mm:ss',
  format: 'YYYY-MM-DD HH:mm:ss',
  disabled: false,
  clearable: true,
  shortcuts: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: [string, string] | null): void;
  (e: 'change', value: [string, string] | null): void;
}>();

const defaultShortcuts = [
  {
    text: '今天',
    value: () => {
      const start = new Date();
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: '昨天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() - 1);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setDate(end.getDate() - 1);
      end.setHours(23, 59, 59, 999);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: '近 7 天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() - 7);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: '近 30 天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() - 30);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: '本月',
    value: () => {
      const start = new Date();
      start.setDate(1);
      start.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return [start, end] as [Date, Date];
    },
  },
];

const computedShortcuts = computed(() => {
  if (Array.isArray(props.shortcuts)) {
    return props.shortcuts;
  }
  return props.shortcuts ? defaultShortcuts : [];
});

const defaultTime = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
] as [Date, Date];

function onChange(val: any) {
  emit('update:modelValue', val);
  emit('change', val);
}
</script>

<template>
  <div class="fcc-date-picker-wrap">
    <el-date-picker
      :model-value="modelValue"
      :type="type"
      :shortcuts="computedShortcuts"
      range-separator="至"
      :start-placeholder="startPlaceholder"
      :end-placeholder="endPlaceholder"
      :value-format="valueFormat"
      :format="format"
      :disabled="disabled"
      :clearable="clearable"
      :default-time="defaultTime"
      popper-class="fcc-date-picker-popper"
      class="!w-full"
      @update:model-value="emit('update:modelValue', $event)"
      @change="onChange"
    />
  </div>
</template>

<style scoped>
.fcc-date-picker-wrap {
  width: 100%;
}
</style>
