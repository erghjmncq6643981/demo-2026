<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { Pencil, Plus, RefreshCw, Trash2, X, Sliders, ShieldAlert } from 'lucide-vue-next';
import { systemConfigApi, type ConfigScope, type ConfigValueType, type SystemConfigVO } from '../api/systemConfigApi';
import { confirmAction, errorText, toastError, toastSuccess, toastWarning } from '../../../utils/feedback';

const rows = ref<SystemConfigVO[]>([]);
const scope = ref<ConfigScope>('BACKEND');
const loading = ref(false);
const loadError = ref('');
const saving = ref(false);
const showEditor = ref(false);
const editingId = ref<string | null>(null);
const form = reactive({ propName: '', propValue: '', propType: 'STRING' as ConfigValueType, description: '' });

const scopes: { label: string; value: ConfigScope; desc: string }[] = [
  { label: 'BACKEND', value: 'BACKEND', desc: '后端核心服务' },
  { label: 'WEB', value: 'WEB', desc: '管理后台 Web' },
  { label: 'CLIENT', value: 'CLIENT', desc: '坐席工作台客户端' },
  { label: 'SYSTEM', value: 'SYSTEM', desc: '底层基座与信令' },
];

async function loadConfigs(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    rows.value = await systemConfigApi.list(scope.value);
  } catch (error) {
    rows.value = [];
    loadError.value = errorText(error, '系统配置加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editingId.value = null;
  form.propName = '';
  form.propValue = '';
  form.propType = 'STRING';
  form.description = '';
  showEditor.value = true;
}

function openEdit(row: SystemConfigVO): void {
  editingId.value = row.id;
  form.propName = row.propName;
  form.propValue = row.propValue;
  form.propType = row.propType;
  form.description = row.description || '';
  showEditor.value = true;
}

async function saveConfig(): Promise<void> {
  if (!form.propName.trim() || !form.propValue.trim()) {
    toastWarning('配置键和值均不能为空');
    return;
  }
  saving.value = true;
  try {
    await systemConfigApi.save({
      propName: form.propName.trim(),
      propValue: form.propValue,
      propType: form.propType,
      scope: scope.value,
      description: form.description.trim() || undefined,
    });
    showEditor.value = false;
    toastSuccess(editingId.value ? '系统配置已更新' : '系统配置已创建');
    await loadConfigs();
  } catch (error) {
    toastError(`保存失败：${errorText(error)}`);
  } finally {
    saving.value = false;
  }
}

async function removeConfig(row: SystemConfigVO): Promise<void> {
  const confirmed = await confirmAction(`确认删除配置项【${row.propName}】吗？此操作将立即影响当前作用域的运行时配置。`, {
    title: '删除系统配置',
    confirmText: '确认删除',
    danger: true,
  });
  if (!confirmed) return;
  try {
    await systemConfigApi.delete(row.id);
    toastSuccess('系统配置已成功删除');
    await loadConfigs();
  } catch (error) {
    toastError(`删除失败：${errorText(error)}`);
  }
}

watch(scope, loadConfigs);
onMounted(loadConfigs);
</script>

<template>
  <div class="space-y-6">
    <!-- Header Card -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-slate-100 shadow-card">
      <div>
        <h2 class="text-base font-black text-slate-900 flex items-center gap-2">
          <Sliders class="w-5 h-5 text-brand-600" />
          系统变量 (业务配置)
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
          实时管理后端微服务、Web 控制台与工作台客户端的运行时业务参数
        </p>
      </div>

      <div class="flex items-center space-x-3">
        <button
          @click="loadConfigs"
          :disabled="loading"
          class="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl transition border border-slate-200 cursor-pointer"
          title="刷新配置列表"
        >
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        </button>

        <button
          @click="openCreate"
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
        >
          <Plus class="w-4 h-4" />
          <span>新增配置</span>
        </button>
      </div>
    </div>

    <!-- Scope Selector Card -->
    <div class="bg-white p-4 rounded-2xl border border-slate-100 shadow-card flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <span class="text-xs font-bold text-slate-500 mr-1">配置作用域:</span>
        <button
          v-for="s in scopes"
          :key="s.value"
          @click="scope = s.value"
          class="px-3.5 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer flex items-center gap-1.5"
          :class="scope === s.value ? 'bg-brand-50 text-brand-600 border border-brand-200 shadow-xs' : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/80'"
        >
          <span>{{ s.label }}</span>
          <span class="text-[10px] font-normal opacity-70">({{ s.desc }})</span>
        </button>
      </div>

      <div class="text-xs text-slate-400">
        当前作用域包含 <span class="font-bold text-slate-700 font-mono">{{ rows.length }}</span> 项配置
      </div>
    </div>

    <!-- Error Alert -->
    <div
      v-if="loadError"
      role="alert"
      class="p-4 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-between text-xs text-rose-700 font-bold"
    >
      <span>{{ loadError }}</span>
      <button class="font-bold underline cursor-pointer" @click="loadConfigs">
        重试
      </button>
    </div>

    <!-- Table Card -->
    <div class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold">
              <th class="py-3.5 px-4">配置键 (Prop Name)</th>
              <th class="py-3.5 px-4">配置值 (Value)</th>
              <th class="py-3.5 px-4">数据类型</th>
              <th class="py-3.5 px-4">说明备注</th>
              <th class="py-3.5 px-4">最后更新</th>
              <th class="py-3.5 px-4 text-right">操作管理</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
            <tr v-if="loading && rows.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400">
                <RefreshCw class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500" />
                正在加载系统配置...
              </td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400 font-medium">
                当前【{{ scope }}】作用域下暂无配置项
              </td>
            </tr>
            <tr v-for="row in rows" :key="row.id" class="hover:bg-slate-50/80 transition-colors">
              <td class="py-4 px-4 font-mono font-bold text-slate-900 text-xs">
                {{ row.propName }}
              </td>
              <td class="py-4 px-4 font-mono text-xs text-slate-700 max-w-sm truncate" :title="row.propValue">
                <span class="bg-slate-50 px-2 py-1 rounded-md border border-slate-200/80 font-medium select-all">
                  {{ row.propValue }}
                </span>
              </td>
              <td class="py-4 px-4">
                <span
                  class="px-2 py-0.5 rounded-md font-mono font-bold text-[11px]"
                  :class="row.propType === 'INT' ? 'bg-blue-50 text-blue-700 border border-blue-200' : row.propType === 'JSON' ? 'bg-purple-50 text-purple-700 border border-purple-200' : 'bg-slate-100 text-slate-700 border border-slate-200'"
                >
                  {{ row.propType }}
                </span>
              </td>
              <td class="py-4 px-4 text-xs text-slate-500 max-w-xs truncate" :title="row.description || ''">
                {{ row.description || '-' }}
              </td>
              <td class="py-4 px-4 text-xs text-slate-400 font-mono">
                <div>{{ row.updatedAt || '-' }}</div>
                <div v-if="row.updatedBy" class="text-[11px] text-slate-300">by {{ row.updatedBy }}</div>
              </td>
              <td class="py-4 px-4 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  <button
                    @click="openEdit(row)"
                    class="p-1.5 text-brand-600 hover:text-brand-800 hover:bg-brand-50 rounded-xl transition cursor-pointer"
                    title="编辑此配置"
                  >
                    <Pencil class="w-4 h-4" />
                  </button>
                  <button
                    @click="removeConfig(row)"
                    class="p-1.5 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded-xl transition cursor-pointer"
                    title="删除此配置"
                  >
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <div v-if="showEditor" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <form class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4 animate-in fade-in zoom-in-95 duration-150" @submit.prevent="saveConfig">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <Sliders class="w-5 h-5 text-brand-600" />
            {{ editingId ? '修改系统配置' : '新增系统配置' }}
          </h3>
          <button type="button" @click="showEditor = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div class="space-y-3.5 text-xs">
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="block text-slate-700 font-bold">配置作用域</label>
              <span class="text-[11px] text-slate-400">跟随当前选中的作用域</span>
            </div>
            <input
              :value="scope"
              disabled
              class="w-full px-3.5 py-2.5 bg-slate-100 border border-slate-200 rounded-xl text-slate-600 font-mono font-bold cursor-not-allowed"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">配置键 (Prop Name) *</label>
            <input
              v-model="form.propName"
              type="text"
              :disabled="!!editingId"
              placeholder="例如: fcc.call.timeout"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium disabled:bg-slate-100 disabled:text-slate-500 disabled:cursor-not-allowed"
            />
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 font-bold mb-1">数据类型</label>
              <select
                v-model="form.propType"
                class="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 font-medium focus:outline-none focus:ring-2 focus:ring-brand-500 bg-white"
              >
                <option value="STRING">STRING (字符串)</option>
                <option value="INT">INT (整型数值)</option>
                <option value="JSON">JSON (结构体/对象)</option>
              </select>
            </div>

            <div>
              <label class="block text-slate-700 font-bold mb-1">说明摘要</label>
              <input
                v-model="form.description"
                type="text"
                placeholder="简述配置用途"
                class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">配置值 (Prop Value) *</label>
            <textarea
              v-model="form.propValue"
              rows="3"
              placeholder="输入该配置的具体值"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            ></textarea>
          </div>
        </div>

        <div class="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
          <button
            type="button"
            @click="showEditor = false"
            class="px-4 py-2 text-slate-600 hover:text-slate-800 font-bold cursor-pointer transition text-xs"
          >
            取消
          </button>
          <button
            type="submit"
            :disabled="saving"
            class="px-5 py-2.5 bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs"
          >
            {{ saving ? '保存中...' : '确认保存' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
