<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCw, X, MonitorSmartphone, Laptop, Apple, Terminal, Globe, ShieldCheck, Download, Check } from 'lucide-vue-next';
import { clientFleetApi, type ClientHardwareRecordVO, type ClientVersionVO } from '../api/clientFleetApi';
import { errorText, toastError, toastSuccess, toastWarning } from '../../../utils/feedback';

const versions = ref<ClientVersionVO[]>([]);
const hardware = ref<ClientHardwareRecordVO[]>([]);
const loading = ref(false);
const loadError = ref('');
const publishing = ref(false);
const showRelease = ref(false);
const platform = ref('');
const releaseForm = reactive({ version: '', platform: 'WINDOWS', downloadUrl: '', fileMd5: '', forceUpdate: false, releaseNotes: '' });

const platforms: { label: string; value: string }[] = [
  { label: '全部平台', value: '' },
  { label: 'WINDOWS', value: 'WINDOWS' },
  { label: 'MAC', value: 'MAC' },
  { label: 'LINUX', value: 'LINUX' },
  { label: 'WEB', value: 'WEB' },
];

async function loadFleet(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    const [vList, hList] = await Promise.all([
      clientFleetApi.listVersions(platform.value || undefined),
      clientFleetApi.listHardware(),
    ]);
    versions.value = vList || [];
    hardware.value = hList || [];
  } catch (error) {
    versions.value = [];
    hardware.value = [];
    loadError.value = errorText(error, '客户端治理数据加载失败');
  } finally {
    loading.value = false;
  }
}

function openRelease(): void {
  Object.assign(releaseForm, { version: '', platform: 'WINDOWS', downloadUrl: '', fileMd5: '', forceUpdate: false, releaseNotes: '' });
  showRelease.value = true;
}

async function publishVersion(): Promise<void> {
  if (!releaseForm.version.trim() || !releaseForm.downloadUrl.trim()) {
    toastWarning('版本号和下载安装包地址为必填项');
    return;
  }
  publishing.value = true;
  try {
    await clientFleetApi.publishVersion({
      ...releaseForm,
      version: releaseForm.version.trim(),
      downloadUrl: releaseForm.downloadUrl.trim(),
      fileMd5: releaseForm.fileMd5.trim() || undefined,
      releaseNotes: releaseForm.releaseNotes.trim() || undefined,
    });
    showRelease.value = false;
    toastSuccess(`客户端版本 v${releaseForm.version.trim()} 已成功发布上线`);
    await loadFleet();
  } catch (error) {
    toastError(`发布失败：${errorText(error)}`);
  } finally {
    publishing.value = false;
  }
}

onMounted(loadFleet);
</script>

<template>
  <div class="space-y-6">
    <!-- Header Card -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-slate-100 shadow-card">
      <div>
        <h2 class="text-base font-black text-slate-900 flex items-center gap-2">
          <MonitorSmartphone class="w-5 h-5 text-brand-600" />
          客户端管理 (Fleet)
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
          全生命周期管理坐席工作台版本分发、强制升级策略与在线终端硬件安全审计
        </p>
      </div>

      <div class="flex items-center space-x-3">
        <button
          @click="loadFleet"
          :disabled="loading"
          class="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl transition border border-slate-200 cursor-pointer"
          title="刷新客户端治理数据"
        >
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        </button>

        <button
          @click="openRelease"
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
        >
          <Plus class="w-4 h-4" />
          <span>发布新版本</span>
        </button>
      </div>
    </div>

    <!-- Filter Card -->
    <div class="bg-white p-4 rounded-2xl border border-slate-100 shadow-card flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <span class="text-xs font-bold text-slate-500 mr-1">目标系统平台:</span>
        <button
          v-for="p in platforms"
          :key="p.value"
          @click="platform = p.value; loadFleet()"
          class="px-3.5 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer"
          :class="platform === p.value ? 'bg-brand-50 text-brand-600 border border-brand-200 shadow-xs' : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/80'"
        >
          {{ p.label }}
        </button>
      </div>

      <div class="text-xs text-slate-400">
        共 <span class="font-bold text-slate-700 font-mono">{{ versions.length }}</span> 个已分发版本，已登记 <span class="font-bold text-slate-700 font-mono">{{ hardware.length }}</span> 台坐席硬件
      </div>
    </div>

    <!-- Error Alert -->
    <div
      v-if="loadError"
      role="alert"
      class="p-4 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-between text-xs text-rose-700 font-bold"
    >
      <span>{{ loadError }}</span>
      <button class="font-bold underline cursor-pointer" @click="loadFleet">
        重试
      </button>
    </div>

    <!-- Matrix: Versions & Hardware -->
    <div class="space-y-6">
      <!-- 1. Version Releases -->
      <div class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6 space-y-4">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-black text-slate-900 flex items-center gap-2">
            <span>📦</span>
            <span>客户端版本发布矩阵</span>
          </h3>
          <span class="text-xs text-slate-400">支持静默升级与灰度强制升级指令</span>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-sm">
            <thead>
              <tr class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold">
                <th class="py-3 px-4">发行版本号</th>
                <th class="py-3 px-4">操作系统平台</th>
                <th class="py-3 px-4">发布状态</th>
                <th class="py-3 px-4">升级策略</th>
                <th class="py-3 px-4">下载包 URL</th>
                <th class="py-3 px-4">发布时间</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
              <tr v-if="loading && versions.length === 0">
                <td colspan="6" class="py-10 text-center text-slate-400">正在拉取版本列表...</td>
              </tr>
              <tr v-else-if="versions.length === 0">
                <td colspan="6" class="py-10 text-center text-slate-400 font-medium">当前平台暂无版本发布记录</td>
              </tr>
              <tr v-for="row in versions" :key="row.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="py-3.5 px-4 font-mono font-bold text-slate-900 text-xs">
                  v{{ row.version }}
                </td>
                <td class="py-3.5 px-4">
                  <span class="px-2.5 py-0.5 rounded-md font-mono font-bold text-xs bg-slate-100 text-slate-700 border border-slate-200 inline-flex items-center gap-1">
                    <Apple v-if="row.platform === 'MAC'" class="w-3.5 h-3.5" />
                    <Terminal v-else-if="row.platform === 'LINUX'" class="w-3.5 h-3.5" />
                    <Globe v-else-if="row.platform === 'WEB'" class="w-3.5 h-3.5" />
                    <Laptop v-else class="w-3.5 h-3.5" />
                    {{ row.platform }}
                  </span>
                </td>
                <td class="py-3.5 px-4">
                  <span class="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600">
                    <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
                    {{ row.status || 'RELEASED' }}
                  </span>
                </td>
                <td class="py-3.5 px-4">
                  <span
                    class="px-2 py-0.5 rounded-full text-xs font-bold"
                    :class="row.forceUpdate ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-slate-100 text-slate-500'"
                  >
                    {{ row.forceUpdate ? '⚡ 强制升级' : '静默可选' }}
                  </span>
                </td>
                <td class="py-3.5 px-4 font-mono text-xs text-slate-500 max-w-sm truncate" :title="row.downloadUrl">
                  <a :href="row.downloadUrl" target="_blank" class="text-brand-600 hover:underline flex items-center gap-1">
                    <Download class="w-3.5 h-3.5 shrink-0" />
                    <span class="truncate">{{ row.downloadUrl }}</span>
                  </a>
                </td>
                <td class="py-3.5 px-4 font-mono text-xs text-slate-400">
                  {{ row.releasedAt ? row.releasedAt.replace('T', ' ').slice(0, 19) : '-' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 2. Hardware Audit -->
      <div class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6 space-y-4">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-black text-slate-900 flex items-center gap-2">
            <span>🖥️</span>
            <span>终端硬件设备审计档案</span>
          </h3>
          <span class="text-xs text-slate-400">记录坐席登录工作台时的物理机器指纹与网络特征</span>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-sm">
            <thead>
              <tr class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold">
                <th class="py-3 px-4">坐席工号</th>
                <th class="py-3 px-4">客户端版本</th>
                <th class="py-3 px-4">操作系统环境</th>
                <th class="py-3 px-4">网络 IP 地址</th>
                <th class="py-3 px-4">硬件 MAC 地址</th>
                <th class="py-3 px-4">最近登录上报</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
              <tr v-if="hardware.length === 0">
                <td colspan="6" class="py-10 text-center text-slate-400 font-medium">暂无终端硬件审计记录</td>
              </tr>
              <tr v-for="row in hardware" :key="row.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="py-3.5 px-4 font-mono font-bold text-slate-900 text-xs">
                  {{ row.workNum }}
                </td>
                <td class="py-3.5 px-4">
                  <span class="px-2 py-0.5 rounded font-mono text-xs bg-slate-100 text-slate-700 border border-slate-200 font-semibold">
                    {{ row.clientVersion || '未知' }}
                  </span>
                </td>
                <td class="py-3.5 px-4 text-xs text-slate-700 font-medium">
                  {{ row.os || '-' }}
                </td>
                <td class="py-3.5 px-4 font-mono text-xs text-slate-500">
                  {{ row.ipAddr || '-' }}
                </td>
                <td class="py-3.5 px-4 font-mono text-xs text-slate-400">
                  {{ row.macAddr || '-' }}
                </td>
                <td class="py-3.5 px-4 font-mono text-xs text-slate-400">
                  {{ row.loginTime ? row.loginTime.replace('T', ' ').slice(0, 19) : '-' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Release Version Modal -->
    <div v-if="showRelease" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <form class="w-full max-w-lg bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4 animate-in fade-in zoom-in-95 duration-150" @submit.prevent="publishVersion">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <MonitorSmartphone class="w-5 h-5 text-brand-600" />
            发布客户端版本
          </h3>
          <button type="button" @click="showRelease = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div class="space-y-3.5 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 font-bold mb-1">版本号 *</label>
              <input
                v-model="releaseForm.version"
                type="text"
                placeholder="例如: 2.1.0"
                class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium"
              />
            </div>
            <div>
              <label class="block text-slate-700 font-bold mb-1">操作系统平台 *</label>
              <select
                v-model="releaseForm.platform"
                class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 font-medium focus:outline-none focus:ring-2 focus:ring-brand-500 bg-white"
              >
                <option value="WINDOWS">WINDOWS</option>
                <option value="MAC">MAC (macOS)</option>
                <option value="LINUX">LINUX</option>
                <option value="WEB">WEB 网页端</option>
              </select>
            </div>
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">安装包下载地址 (URL) *</label>
            <input
              v-model="releaseForm.downloadUrl"
              type="text"
              placeholder="https://download.example.com/packages/setup-v2.1.0.exe"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">文件 MD5 校验和</label>
            <input
              v-model="releaseForm.fileMd5"
              type="text"
              placeholder="例如: e10adc3949ba59abbe56e057f20f883e"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">发布更新说明 (Release Notes)</label>
            <textarea
              v-model="releaseForm.releaseNotes"
              rows="3"
              placeholder="版本更新日志与主要特性说明"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            ></textarea>
          </div>

          <div class="flex items-center gap-2 p-3 bg-amber-50/60 border border-amber-200/80 rounded-xl">
            <input
              id="forceUpdateCheckbox"
              v-model="releaseForm.forceUpdate"
              type="checkbox"
              class="w-4 h-4 text-brand-600 rounded border-slate-300 focus:ring-brand-500 cursor-pointer"
            />
            <label for="forceUpdateCheckbox" class="text-xs font-bold text-slate-800 cursor-pointer select-none">
              要求所有低版本坐席必须强制升级方可登入系统
            </label>
          </div>
        </div>

        <div class="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
          <button
            type="button"
            @click="showRelease = false"
            class="px-4 py-2 text-slate-600 hover:text-slate-800 font-bold cursor-pointer transition text-xs"
          >
            取消
          </button>
          <button
            type="submit"
            :disabled="publishing"
            class="px-5 py-2.5 bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs"
          >
            {{ publishing ? '发布中...' : '确认发布' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
