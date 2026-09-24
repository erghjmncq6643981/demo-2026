<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCw, Search, Trash2, X, PhoneCall, Radio, Laptop, ShieldAlert } from 'lucide-vue-next';
import { extensionApi, type ExtensionVO } from '../api/extensionApi';
import { confirmAction, errorText, toastError, toastSuccess, toastWarning } from '../../../utils/feedback';

const rows = ref<ExtensionVO[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const mutating = ref(false);
const pageNum = ref(1);
const pageSize = ref(20);
const query = reactive({ extension: '', endpointType: '', onlineStatus: '' });

const showCreate = ref(false);
const createForm = reactive({ extension: '', password: '', endpointType: 'SIP' });

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));

async function loadExtensions(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await extensionApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      extension: query.extension.trim() || undefined,
      endpointType: query.endpointType || undefined,
      onlineStatus: query.onlineStatus || undefined,
    });
    rows.value = result.list ?? [];
    total.value = result.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = errorText(error, '分机列表加载失败');
  } finally {
    loading.value = false;
  }
}

function search(): void {
  pageNum.value = 1;
  void loadExtensions();
}

function resetQuery(): void {
  query.extension = '';
  query.endpointType = '';
  query.onlineStatus = '';
  search();
}

function openCreate(): void {
  createForm.extension = '';
  createForm.password = '';
  createForm.endpointType = 'SIP';
  showCreate.value = true;
}

async function createExtension(): Promise<void> {
  const extension = createForm.extension.trim();
  const password = createForm.password.trim();
  if (!extension || !password) {
    toastWarning('分机号和注册口令均不能为空');
    return;
  }
  mutating.value = true;
  try {
    await extensionApi.create({ extension, password, endpointType: createForm.endpointType });
    showCreate.value = false;
    toastSuccess(`分机 ${extension} 已创建并同步至 FreeSWITCH`);
    await loadExtensions();
  } catch (error) {
    toastError(`创建失败：${errorText(error)}`);
  } finally {
    mutating.value = false;
  }
}

async function removeExtension(row: ExtensionVO): Promise<void> {
  const confirmed = await confirmAction(`删除分机 ${row.extension} 后将同步从 FreeSWITCH 移除该配置，此操作不可撤销。确认继续吗？`, {
    title: '删除分机',
    confirmText: '确认删除',
    danger: true,
  });
  if (!confirmed) return;

  mutating.value = true;
  try {
    await extensionApi.delete(row.id);
    toastSuccess(`分机 ${row.extension} 已成功删除`);
    await loadExtensions();
  } catch (error) {
    toastError(`删除失败：${errorText(error)}`);
  } finally {
    mutating.value = false;
  }
}

async function changePage(delta: number): Promise<void> {
  const next = Math.min(totalPages.value, Math.max(1, pageNum.value + delta));
  if (next === pageNum.value) return;
  pageNum.value = next;
  await loadExtensions();
}

onMounted(loadExtensions);
</script>

<template>
  <div class="space-y-6">
    <!-- Header Card -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-slate-100 shadow-card">
      <div>
        <h2 class="text-base font-black text-slate-900 flex items-center gap-2">
          <PhoneCall class="w-5 h-5 text-brand-600" />
          分机终端管理
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
          管理 SIP 话机与 WebRTC 分机档案、SIP 注册鉴权与信令状态
        </p>
      </div>

      <div class="flex items-center space-x-3">
        <button
          @click="loadExtensions"
          :disabled="loading"
          class="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl transition border border-slate-200 cursor-pointer"
          title="刷新分机列表"
        >
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        </button>

        <button
          @click="openCreate"
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
        >
          <Plus class="w-4 h-4" />
          <span>新增分机</span>
        </button>
      </div>
    </div>

    <!-- Filter Card -->
    <div class="bg-white p-4 rounded-2xl border border-slate-100 shadow-card flex flex-wrap items-center gap-3">
      <div class="w-44">
        <label class="block text-xs font-bold text-slate-600 mb-1">分机号</label>
        <input
          v-model="query.extension"
          placeholder="例如: 901001"
          @keyup.enter="search"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs font-mono focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 placeholder:text-slate-400 font-medium"
        />
      </div>

      <div class="w-40">
        <label class="block text-xs font-bold text-slate-600 mb-1">终端类型</label>
        <select
          v-model="query.endpointType"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 bg-white font-medium"
        >
          <option value="">全部类型</option>
          <option value="SIP">☎️ SIP 话机</option>
          <option value="WEBRTC">💻 WebRTC 软话机</option>
        </select>
      </div>

      <div class="w-40">
        <label class="block text-xs font-bold text-slate-600 mb-1">注册状态</label>
        <select
          v-model="query.onlineStatus"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 bg-white font-medium"
        >
          <option value="">全部状态</option>
          <option value="ONLINE">● 在线</option>
          <option value="OFFLINE">○ 离线</option>
        </select>
      </div>

      <div class="flex items-center gap-2 pt-5">
        <button
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
          @click="search"
        >
          <Search class="w-3.5 h-3.5" />
          <span>查询</span>
        </button>

        <button
          class="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer"
          @click="resetQuery"
        >
          重置
        </button>
      </div>
    </div>

    <!-- Error Alert -->
    <div
      v-if="loadError"
      role="alert"
      class="p-4 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-between text-xs text-rose-700 font-bold"
    >
      <span>{{ loadError }}</span>
      <button class="font-bold underline cursor-pointer" @click="loadExtensions">
        重试
      </button>
    </div>

    <!-- Table Card -->
    <div class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold">
              <th class="py-3.5 px-4">分机号</th>
              <th class="py-3.5 px-4">终端类型</th>
              <th class="py-3.5 px-4">注册状态</th>
              <th class="py-3.5 px-4">绑定坐席</th>
              <th class="py-3.5 px-4">信令注册地址</th>
              <th class="py-3.5 px-4 text-right">操作管理</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
            <tr v-if="loading && rows.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400">
                <RefreshCw class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500" />
                正在加载分机列表...
              </td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400 font-medium">
                暂无匹配的分机档案
              </td>
            </tr>
            <tr v-for="row in rows" :key="row.id" class="hover:bg-slate-50/80 transition-colors">
              <td class="py-4 px-4 font-mono font-bold text-slate-900 text-sm">
                {{ row.extension }}
              </td>
              <td class="py-4 px-4">
                <span
                  class="px-2.5 py-0.5 rounded-md font-bold text-xs font-mono inline-flex items-center gap-1"
                  :class="row.endpointType === 'WEBRTC' ? 'text-purple-700 bg-purple-50 border border-purple-200' : 'text-blue-700 bg-blue-50 border border-blue-200'"
                >
                  <Laptop v-if="row.endpointType === 'WEBRTC'" class="w-3.5 h-3.5" />
                  <Radio v-else class="w-3.5 h-3.5" />
                  {{ row.endpointType }}
                </span>
              </td>
              <td class="py-4 px-4">
                <span
                  class="inline-flex items-center gap-1.5 text-xs font-bold"
                  :class="row.onlineStatus === 'ONLINE' ? 'text-emerald-600' : 'text-slate-400'"
                >
                  <span
                    class="w-2 h-2 rounded-full"
                    :class="row.onlineStatus === 'ONLINE' ? 'bg-emerald-500' : 'bg-slate-300'"
                  ></span>
                  {{ row.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
                </span>
              </td>
              <td class="py-4 px-4">
                <div v-if="row.boundAgentName" class="flex items-center gap-1.5">
                  <span class="font-bold text-slate-800 text-xs">{{ row.boundAgentName }}</span>
                  <span v-if="row.boundAgentWorkNo" class="font-mono text-xs text-slate-400">({{ row.boundAgentWorkNo }})</span>
                </div>
                <span v-else class="text-xs text-slate-300">未绑定</span>
              </td>
              <td class="py-4 px-4 font-mono text-xs text-slate-400 max-w-xs truncate" :title="row.registeredContact || row.registeredIp || ''">
                {{ row.registeredContact || row.registeredIp || '-' }}
              </td>
              <td class="py-4 px-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button
                    @click="removeExtension(row)"
                    class="p-1.5 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded-xl transition cursor-pointer"
                    title="删除分机"
                  >
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Footer -->
      <div class="flex items-center justify-between text-xs text-slate-500 pt-4 border-t border-slate-100 mt-2">
        <span>共 {{ total }} 条分机档案</span>
        <div class="flex items-center gap-2">
          <button
            class="px-3 py-1.5 border border-slate-200 bg-white rounded-xl hover:bg-slate-50 disabled:opacity-40 cursor-pointer font-medium"
            :disabled="pageNum <= 1"
            @click="changePage(-1)"
          >
            上一页
          </button>
          <span class="font-mono text-slate-600">{{ pageNum }} / {{ totalPages }}</span>
          <button
            class="px-3 py-1.5 border border-slate-200 bg-white rounded-xl hover:bg-slate-50 disabled:opacity-40 cursor-pointer font-medium"
            :disabled="pageNum >= totalPages"
            @click="changePage(1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- Create Extension Modal -->
    <div v-if="showCreate" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <form class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4 animate-in fade-in zoom-in-95 duration-150" @submit.prevent="createExtension">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <PhoneCall class="w-5 h-5 text-brand-600" />
            新增话机分机
          </h3>
          <button type="button" @click="showCreate = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div class="space-y-3.5 text-xs">
          <div>
            <label class="block text-slate-700 font-bold mb-1">分机号 *</label>
            <input
              v-model="createForm.extension"
              type="text"
              autocomplete="off"
              placeholder="例如: 901001 / 1001"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">SIP 注册密码 *</label>
            <input
              v-model="createForm.password"
              type="password"
              autocomplete="new-password"
              placeholder="话机或软电话 SIP 鉴权密码"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">终端类型</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                @click="createForm.endpointType = 'SIP'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="createForm.endpointType === 'SIP' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                ☎️ SIP 话机
              </button>
              <button
                type="button"
                @click="createForm.endpointType = 'WEBRTC'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="createForm.endpointType === 'WEBRTC' ? 'bg-purple-50 border-purple-300 text-purple-700' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                💻 WebRTC 软话机
              </button>
            </div>
          </div>

          <div class="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80 text-[11px] text-slate-500">
            💡 提示：创建分机后会自动同步至 FreeSWITCH 目录，实体话机通过拨打 0000 即可自助完成坐席关联。
          </div>
        </div>

        <div class="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
          <button
            type="button"
            @click="showCreate = false"
            class="px-4 py-2 text-slate-600 hover:text-slate-800 font-bold cursor-pointer transition text-xs"
          >
            取消
          </button>
          <button
            type="submit"
            :disabled="mutating"
            class="px-5 py-2.5 bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs"
          >
            {{ mutating ? '提交中...' : '确认创建' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
