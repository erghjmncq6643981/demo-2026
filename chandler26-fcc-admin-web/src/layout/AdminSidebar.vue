<script setup lang="ts">
defineProps<{ activeTab: string; canManageBusiness: boolean }>();
const emit = defineEmits<{ (event: "select", tab: string): void }>();
</script>

<template>
  <aside
    class="w-56 bg-white border-r border-slate-100 flex flex-col justify-between p-4 shrink-0 z-20 h-full overflow-y-auto select-none"
  >
    <div>
      <!-- 品牌 LOGO -->
      <div class="flex items-center gap-2.5 mb-5 px-1">
        <div
          class="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-purple-600 flex items-center justify-center text-white text-lg shadow-md shadow-indigo-500/20 shrink-0"
        >
          📦
        </div>
        <div>
          <div
            class="font-black text-base text-slate-900 tracking-tight leading-tight"
          >
            箱箱通讯
          </div>
          <div class="text-[11px] font-semibold text-slate-400 mt-0.5">
            FCC Cloud 2.6
          </div>
        </div>
      </div>

      <!-- 业务导航菜单 -->
      <div class="space-y-4">
        <!-- 分组 1: 话务与监控 -->
        <div>
          <div
            class="flex items-center gap-2 px-2.5 py-1.5 mb-2.5 rounded-xl bg-slate-100/80 border border-slate-200/60 shadow-2xs"
          >
            <div class="w-1.5 h-3.5 bg-brand-600 rounded-full shrink-0"></div>
            <span class="text-xs font-black text-slate-800 tracking-wider">
              话务与监控
            </span>
          </div>

          <div class="space-y-1 font-bold">
            <!-- 1. 通话与回拨记录 -->
            <button
              @click="emit('select', 'routes')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'routes' || activeTab === 'callback'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'routes' || activeTab === 'callback'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"
                  />
                </svg>
                <span>通话与回拨记录</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'routes' || activeTab === 'callback'
                    ? 'bg-brand-500'
                    : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 2. 监控大盘 (原 系统监控大盘) -->
            <button
              @click="emit('select', 'running')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'running'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'running'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                  />
                </svg>
                <span>监控大盘</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'running' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 3. 自动外呼 -->
            <button
              v-if="canManageBusiness"
              @click="emit('select', 'dial-jobs')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'dial-jobs'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'dial-jobs'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.5 19.5 0 01-6-6A19.79 19.79 0 012.12 4.18 2 2 0 014.11 2h3a2 2 0 012 1.72c.13.96.36 1.9.69 2.8a2 2 0 01-.45 2.11L8.09 9.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45c.9.33 1.84.56 2.8.69A2 2 0 0122 16.92zM15 2h6v6m0-6l-7 7"
                  />
                </svg>
                <span>自动外呼</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'dial-jobs' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 4. 客服组管理 -->
            <button
              @click="emit('select', 'groups')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'groups'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'groups' ? 'text-brand-500' : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
                  />
                </svg>
                <span>客服组管理</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'groups' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 5. 坐席管理 (原 坐席人员) -->
            <button
              @click="emit('select', 'agents')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'agents'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'agents' ? 'text-brand-500' : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                  />
                </svg>
                <span>坐席管理</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'agents' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 6. 组织效能报表 -->
            <button
              @click="emit('select', 'orgreport')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'orgreport'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'orgreport'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                  />
                </svg>
                <span>组织效能报表</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'orgreport' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 7. IVR流程 -->
            <button
              @click="emit('select', 'flows')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'flows'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'flows' ? 'text-brand-500' : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
                <span>IVR流程</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'flows' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>
          </div>
        </div>

        <!-- 分组 2: 系统运维 -->
        <div class="pt-4 border-t border-slate-100 mt-4">
          <div
            class="flex items-center gap-2 px-2.5 py-1.5 mb-2.5 rounded-xl bg-slate-100/80 border border-slate-200/60 shadow-2xs"
          >
            <div class="w-1.5 h-3.5 bg-emerald-600 rounded-full shrink-0"></div>
            <span class="text-xs font-black text-slate-800 tracking-wider">
              系统运维
            </span>
          </div>

          <div class="space-y-1 font-bold">
            <!-- 1. 客户端管理 -->
            <button
              @click="emit('select', 'clients')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'clients'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'clients'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
                  />
                </svg>
                <span>客户端管理</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'clients' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 2. 分机管理 -->
            <button
              @click="emit('select', 'extensions')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'extensions'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'extensions'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"
                  />
                </svg>
                <span>分机管理</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'extensions' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>

            <!-- 3. 系统配置 -->
            <button
              @click="emit('select', 'sysvars')"
              class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
              :class="
                activeTab === 'sysvars'
                  ? 'bg-brand-50 text-brand-600 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              <div class="flex items-center gap-2.5">
                <svg
                  class="w-4 h-4 shrink-0"
                  :class="
                    activeTab === 'sysvars'
                      ? 'text-brand-500'
                      : 'text-slate-400'
                  "
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"
                  />
                </svg>
                <span>系统配置</span>
              </div>
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="
                  activeTab === 'sysvars' ? 'bg-brand-500' : 'bg-transparent'
                "
              ></span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>
