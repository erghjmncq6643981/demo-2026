<script setup lang="ts">
import { useCdrReport } from '../features/cdr/composables/useCdrReport';
const props = withDefaults(defineProps<{
  initialTab?: 'records' | 'callback';
}>(), {
  initialTab: 'records'
});

const {
  activeSubTab,
  callRecords,
  filterAgentName,
  filterAgentWorkNo,
  filterPhone,
  filterDirection,
  filterCallId,
  filterDateRange,
  dateShortcuts,
  handleDateRangeChange,
  cdrPageNum,
  cdrPageSize,
  cdrTotal,
  cdrLoading,
  handleSearchCall,
  resetCallFilter,
  callbackRecords,
  filterCbCustomerPhone,
  filterCbAgentWorkNo,
  filteredCallbackRecords,
  cbPageNum,
  cbPageSize,
  pagedCallbackRecords,
  resetCallbackFilter,
  handleDispatch,
  handleCallbackCall,
  totalCallsCount,
  answeredCallsCount,
  answerRate,
  totalTalkDurationSec,
  inboundTalkSec,
  outboundTalkSec,
  formatDurationDisplay,
  onlineExtensionsCount,
  currentDateStr,
  playingAudio,
  currentAudioUrl,
  currentAudioInfo,
  playAudio,
  closeAudio,
  showDetailModal,
  selectedCdr,
  openDetail,
  handleSearchCallback,
  exporting,
  exportCdrCsv
} = useCdrReport(props);
</script>

<template>
  <div class="h-full flex-1 flex flex-col gap-6 overflow-y-auto pr-1">
    
    <!-- 提示已统一收敛到全局反馈层 (src/utils/feedback.ts) -->

    <!-- 1. 顶部 Bento Grid 4 张话务与回拨指标卡片 -->
    <div class="grid grid-cols-12 gap-5 shrink-0">
      <!-- 指标 1 -->
      <div class="col-span-3 bg-white p-6 rounded-3xl border border-slate-100 shadow-card flex flex-col justify-between hover:shadow-card-hover transition-all">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-bold text-slate-500">今日呼叫总数</span>
          <div class="w-9 h-9 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center text-sm font-black">↗</div>
        </div>
        <div>
          <div class="text-3xl font-black text-slate-900 tracking-tight">{{ totalCallsCount }} <span class="text-sm font-medium text-slate-400">通</span></div>
          <div class="text-xs text-slate-500 mt-1 flex items-center gap-2 font-semibold">
            <span class="text-emerald-600 font-extrabold">{{ answerRate }} 接通率</span>
            <span class="text-slate-300">•</span>
            <span>成功 {{ answeredCallsCount }}通</span>
          </div>
        </div>
      </div>

      <!-- 指标 2: 核心中继卡片 -->
      <div class="col-span-3 bg-gradient-to-tr from-amber-200 via-amber-300 to-yellow-400 p-6 rounded-3xl shadow-card text-amber-950 flex flex-col justify-between relative overflow-hidden">
        <div class="absolute -right-4 -bottom-6 w-28 h-28 bg-white/20 rounded-full blur-xl pointer-events-none"></div>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-xs font-black tracking-wider uppercase opacity-75">SIP TRUNK POOL</span>
            <div class="text-sm font-black text-amber-950">移动 / 电信智能中继</div>
          </div>
          <span class="text-xs font-extrabold bg-amber-900/15 px-2.5 py-0.5 rounded-full">主节点</span>
        </div>
        <div>
          <div class="font-mono text-base font-black tracking-widest my-1">127.0.0.1 (FreeSWITCH)</div>
          <div class="flex items-center justify-between text-xs font-bold opacity-85">
            <span>实时通道: {{ answeredCallsCount > 0 ? '通道活跃' : '就绪待命' }} (在线分机: {{ onlineExtensionsCount }})</span>
            <span class="bg-white/40 px-2.5 py-0.5 rounded-full">ACTIVE</span>
          </div>
        </div>
      </div>

      <!-- 指标 3: 未接待漏话回拨池卡片 -->
      <div class="col-span-3 bg-white p-6 rounded-3xl border border-slate-100 shadow-card flex flex-col justify-between hover:shadow-card-hover transition-all">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-bold text-slate-500">未接待漏话总池</span>
          <div class="w-9 h-9 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center text-sm font-black">↩</div>
        </div>
        <div>
          <div class="text-3xl font-black text-rose-600 tracking-tight">
            {{ callbackRecords.filter(c => c.status === 'PENDING').length }} <span class="text-sm font-medium text-slate-400">单待回访</span>
          </div>
          <div class="text-xs text-slate-500 mt-1 flex items-center gap-2 font-semibold">
            <span class="text-indigo-600 font-extrabold">{{ callbackRecords.filter(c => c.status === 'ASSIGNED').length }} 单已派单</span>
            <span class="text-slate-300">•</span>
            <span class="text-emerald-600 font-extrabold">{{ callbackRecords.filter(c => c.status === 'CALLED').length }} 单已完成</span>
          </div>
        </div>
      </div>

      <!-- 指标 4 -->
      <div class="col-span-3 bg-white p-6 rounded-3xl border border-slate-100 shadow-card flex flex-col justify-between hover:shadow-card-hover transition-all">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-bold text-slate-500">总通话时长</span>
          <div class="w-9 h-9 rounded-2xl bg-purple-50 text-purple-600 flex items-center justify-center text-sm font-black">⏱</div>
        </div>
        <div>
          <div class="text-3xl font-black text-slate-900 tracking-tight">{{ formatDurationDisplay(totalTalkDurationSec) }}</div>
          <div class="text-xs text-slate-500 mt-1 flex items-center gap-2 font-semibold">
            <span>呼入 {{ formatDurationDisplay(inboundTalkSec) }}</span>
            <span class="text-slate-300">•</span>
            <span>呼出 {{ formatDurationDisplay(outboundTalkSec) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 主内容卡片：整合【通话记录】与【未接待回拨】双 Tab -->
    <div class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 flex flex-col flex-1 min-h-[560px]">
      
      <!-- 顶栏：两个核心 Tab 切换器 + 统计日期与导出 -->
      <div class="flex items-center justify-between pb-4 border-b border-slate-100 mb-4 shrink-0">
        <!-- 核心 Tab 切换栏 -->
        <div class="flex items-center gap-8">
          <button
            @click="activeSubTab = 'records'"
            class="pb-2.5 transition-all cursor-pointer flex items-center gap-2.5 relative"
            :class="activeSubTab === 'records' ? 'font-black text-slate-900 text-lg border-b-2 border-brand-500' : 'font-bold text-slate-400 hover:text-slate-700 text-base'"
          >
            <span>📞 通话记录</span>
            <span
              class="px-2.5 py-0.5 rounded-full text-xs font-bold font-mono"
              :class="activeSubTab === 'records' ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-500'"
            >
              {{ callRecords.length }}
            </span>
          </button>

          <button
            @click="activeSubTab = 'callback'"
            class="pb-2.5 transition-all cursor-pointer flex items-center gap-2.5 relative"
            :class="activeSubTab === 'callback' ? 'font-black text-slate-900 text-lg border-b-2 border-brand-500' : 'font-bold text-slate-400 hover:text-slate-700 text-base'"
          >
            <span>↩️ 未接待回拨</span>
            <span class="px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-600 font-black text-xs font-mono">
              {{ callbackRecords.filter(c => c.status === 'PENDING').length }} 待办
            </span>
          </button>
        </div>

        <!-- 右侧辅助工具 -->
        <div class="flex items-center gap-3 text-xs">
          <div class="flex items-center gap-2 bg-slate-50 border border-slate-200/80 px-3.5 py-1.5 rounded-full text-slate-700 font-semibold">
            <span>📅</span>
            <span class="font-mono font-bold">{{ currentDateStr }}</span>
          </div>
          <button
            @click="exportCdrCsv"
            :disabled="exporting"
            class="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-full transition-all cursor-pointer text-xs disabled:opacity-60 disabled:cursor-not-allowed"
            title="按当前筛选条件导出全部匹配的通话记录 (CSV)"
          >
            {{ exporting ? '导出中…' : '导出数据' }}
          </button>
        </div>
      </div>

      <!-- ==================== TAB 1: 通话记录内容区 ==================== -->
      <div v-if="activeSubTab === 'records'" class="flex-1 flex flex-col overflow-hidden">
        
        <!-- 🔍 通话记录过滤条件 (严格按要求：坐席姓名、坐席工号、号码、方向、通话ID、通话时间) -->
        <div class="flex flex-wrap items-end gap-3 text-sm mb-4 bg-slate-50/70 p-3.5 rounded-2xl border border-slate-100 shrink-0">
          <!-- 1. 坐席姓名 -->
          <div class="w-32">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">坐席姓名</label>
            <input
              v-model="filterAgentName"
              type="text"
              placeholder="舒欣 / 陈松"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium shadow-2xs"
            >
          </div>

          <!-- 2. 坐席工号 -->
          <div class="w-28">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">坐席工号</label>
            <input
              v-model="filterAgentWorkNo"
              type="text"
              placeholder="例如: 901415"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 shadow-2xs"
            >
          </div>

          <!-- 3. 号码 -->
          <div class="w-36">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">号码 (主叫/被叫)</label>
            <input
              v-model="filterPhone"
              type="text"
              placeholder="手机号或分机号..."
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-mono shadow-2xs"
            >
          </div>

          <!-- 4. 方向 -->
          <div class="w-28">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">通话方向</label>
            <select v-model="filterDirection" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-700 text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium cursor-pointer shadow-2xs">
              <option value="">全部方向</option>
              <option value="OUTBOUND">呼出 (↗)</option>
              <option value="INBOUND">呼入 (↙)</option>
              <option value="INTERNAL">内部 (↔)</option>
            </select>
          </div>

          <!-- 5. 通话ID -->
          <div class="w-36">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">通话 ID</label>
            <input
              v-model="filterCallId"
              type="text"
              placeholder="CALL-..."
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 shadow-2xs"
            >
          </div>

          <!-- 6. 通话时间 (Element Plus 专业级日期时间范围选择器) -->
          <div class="shrink-0">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">通话时间范围</label>
            <div class="custom-datepicker-wrap">
              <el-date-picker
                v-model="filterDateRange"
                type="datetimerange"
                :shortcuts="dateShortcuts"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                size="default"
                @change="handleDateRangeChange"
              />
            </div>
          </div>

          <!-- 操作按钮组：重置 + 明亮的查询按钮 -->
          <div class="flex items-center gap-2 shrink-0">
            <!-- 重置按钮 -->
            <button
              @click="resetCallFilter"
              class="px-4 py-2 bg-white border border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-bold transition-all cursor-pointer shadow-2xs"
            >
              重置
            </button>

            <!-- 明亮的查询按钮 -->
            <button
              @click="handleSearchCall"
              class="px-5 py-2 bg-[#1677FF] hover:bg-blue-600 text-white rounded-xl text-xs font-black shadow-md shadow-blue-500/25 flex items-center gap-1.5 transition-all cursor-pointer"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              <span>查询</span>
            </button>
          </div>
        </div>

        <!-- 📋 通话记录数据表格 (严格落实字段：主叫姓名/号码、运营商、坐席姓名、坐席工号、方向、通话开始时间、通话结束时间、响铃时长、录音（显示时长）、状态) -->
        <div class="flex-1 overflow-x-auto overflow-y-auto">
          <table class="w-full text-sm text-left">
            <thead class="text-slate-400 border-b border-slate-100 pb-2 text-xs font-bold uppercase sticky top-0 bg-white z-10">
              <tr>
                <th class="pb-3 px-3.5">主叫姓名 / 号码</th>
                <th class="pb-3 px-3">运营商</th>
                <th class="pb-3 px-3">坐席姓名</th>
                <th class="pb-3 px-2.5">坐席工号</th>
                <th class="pb-3 px-2.5">方向</th>
                <th class="pb-3 px-3">通话开始时间</th>
                <th class="pb-3 px-3">通话结束时间</th>
                <th class="pb-3 px-2.5">响铃时长</th>
                <th class="pb-3 px-3">录音 (时长)</th>
                <th class="pb-3 px-2.5">状态</th>
                <th class="pb-3 px-3 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              <!-- 空状态提示 (真实测试环境引导) -->
              <tr v-if="callRecords.length === 0">
                <td colspan="11" class="py-16 text-center">
                  <div class="flex flex-col items-center justify-center">
                    <div class="w-14 h-14 rounded-2xl bg-blue-50 border border-blue-100 flex items-center justify-center text-2xl mb-3 shadow-xs">
                      📞
                    </div>
                    <p class="text-sm font-bold text-slate-700 mb-1">数据库已进入真实测试模式，暂无通话话单</p>
                    <p class="text-xs text-slate-400 max-w-sm leading-relaxed">请使用 PC 客户端或话机发起呼入/呼出通话，通话建立并挂断后，系统将实时生成完整 CDR 话单与录音流水</p>
                  </div>
                </td>
              </tr>
              <tr v-for="item in callRecords" :key="item.id" class="hover:bg-slate-50/80 transition-colors">
                <!-- 1. 主叫姓名/号码 -->
                <td class="py-3.5 px-3.5">
                  <div class="flex items-center gap-2.5">
                    <div class="w-8 h-8 rounded-full bg-blue-50 text-brand-600 font-black flex items-center justify-center text-xs shrink-0 border border-indigo-100">
                      {{ item.callerName.charAt(0) }}
                    </div>
                    <div>
                      <div class="font-bold text-slate-900 text-sm leading-snug">{{ item.callerName }}</div>
                      <div class="font-mono text-slate-400 text-xs">{{ item.callerPhone }}</div>
                    </div>
                  </div>
                </td>

                <!-- 2. 运营商 -->
                <td class="py-3.5 px-3">
                  <span class="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold font-sans">
                    {{ item.carrier }}
                  </span>
                </td>

                <!-- 3. 坐席姓名 -->
                <td class="py-3.5 px-3 font-bold text-slate-900 text-sm">
                  {{ item.agentName }}
                </td>

                <!-- 4. 坐席工号 -->
                <td class="py-3.5 px-2.5">
                  <span class="font-mono text-xs text-slate-600 font-bold bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                    {{ item.agentWorkNo }}
                  </span>
                </td>

                <!-- 5. 方向 -->
                <td class="py-3.5 px-2.5">
                  <span v-if="item.direction === 'OUTBOUND'" class="text-amber-600 font-black text-xs flex items-center gap-1">
                    <span class="text-sm">↗</span><span>呼出</span>
                  </span>
                  <span v-else-if="item.direction === 'INBOUND'" class="text-blue-600 font-black text-xs flex items-center gap-1">
                    <span class="text-sm">↙</span><span>呼入</span>
                  </span>
                  <span v-else class="text-indigo-600 font-black text-xs flex items-center gap-1">
                    <span class="text-sm">↔</span><span>内部</span>
                  </span>
                </td>

                <!-- 6. 通话开始时间 -->
                <td class="py-3.5 px-3 font-mono text-slate-600 text-xs">
                  {{ item.startTime }}
                </td>

                <!-- 7. 通话结束时间 -->
                <td class="py-3.5 px-3 font-mono text-slate-400 text-xs">
                  {{ item.endTime }}
                </td>

                <!-- 8. 响铃时长 -->
                <td class="py-3.5 px-2.5 font-mono text-amber-600 font-bold text-xs">
                  {{ item.ringDuration }}
                </td>

                <!-- 9. 录音（显示时长）：可复播时渲染播放按钮，仅有通话时长但无录音文件时只展示时长 -->
                <td class="py-3.5 px-3">
                  <button
                    v-if="item.recordingUrl"
                    @click="playAudio(item.callerName, item.callerPhone, item.audioDuration || '', item.recordingUrl, item.rawId)"
                    class="px-2.5 py-1 rounded-xl bg-brand-50 hover:bg-brand-500 text-brand-600 hover:text-white flex items-center gap-1.5 transition-all cursor-pointer text-xs font-mono font-bold shadow-2xs group"
                    title="点击在线试听双轨录音"
                  >
                    <span class="text-[10px] text-brand-500 group-hover:text-white">▶</span>
                    <span>{{ item.audioDuration }}</span>
                  </button>
                  <span
                    v-else-if="item.audioDuration"
                    class="text-slate-400 font-mono text-xs"
                    title="该通话未产生录音文件"
                  >
                    {{ item.audioDuration }}
                  </span>
                  <span v-else class="text-slate-300 font-mono text-xs">-</span>
                </td>

                <!-- 10. 状态 -->
                <td class="py-3.5 px-2.5">
                  <span v-if="item.status === 'ANSWERED'" class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold text-xs">
                    已接通
                  </span>
                  <span v-else-if="item.status === 'MISSED'" class="px-2.5 py-1 rounded-full bg-rose-50 text-rose-600 font-bold text-xs">
                    未接听
                  </span>
                  <span v-else-if="item.status === 'BUSY'" class="px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 font-bold text-xs">
                    坐席忙
                  </span>
                  <span v-else class="px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 font-bold text-xs">
                    拒接
                  </span>
                </td>

                <!-- 操作 -->
                <td class="py-3.5 px-3 text-right">
                  <button
                    @click="openDetail(item)"
                    class="px-3 py-1 bg-indigo-50 hover:bg-indigo-100 text-brand-600 rounded-xl text-xs font-bold transition cursor-pointer"
                  >
                    过程详情
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 底部分页 (通话记录)：总数与分页均由数据库 COUNT + LIMIT 驱动 -->
        <div class="pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500 shrink-0">
          <span class="text-xs text-slate-600 font-medium">
            共 {{ cdrTotal }} 条通话记录数据
            <span v-if="cdrLoading" class="text-slate-400 font-normal">· 正在查询…</span>
          </span>
          <div v-if="cdrTotal > 0">
            <el-pagination
              v-model:current-page="cdrPageNum"
              v-model:page-size="cdrPageSize"
              :total="cdrTotal"
              :page-sizes="[10, 20, 50]"
              layout="sizes, prev, pager, next"
              size="small"
              background
            />
          </div>
          <span v-else class="text-slate-400 text-xs font-mono">第 0 / 0 页</span>
        </div>
      </div>

      <!-- ==================== TAB 2: 未接待回拨内容区 ==================== -->
      <div v-else-if="activeSubTab === 'callback'" class="flex-1 flex flex-col overflow-hidden">
        
        <!-- 🔍 未接待回拨过滤条件 (严格按照用户第5条指令：客户号码、坐席工号) -->
        <div class="flex items-end gap-3 text-sm mb-4 bg-slate-50/70 p-3.5 rounded-2xl border border-slate-100 shrink-0">
          <!-- 1. 客户号码 -->
          <div class="w-64">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">客户号码</label>
            <input
              v-model="filterCbCustomerPhone"
              type="text"
              placeholder="搜索客户手机号码..."
              class="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium shadow-2xs"
            >
          </div>

          <!-- 2. 坐席工号 -->
          <div class="w-48">
            <label class="block text-xs font-extrabold text-slate-700 mb-1.5">坐席工号</label>
            <input
              v-model="filterCbAgentWorkNo"
              type="text"
              placeholder="例如: 901415 / 901473"
              class="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2 text-slate-800 font-mono text-xs focus:outline-none focus:border-[#1677ff] focus:ring-1 focus:ring-[#1677ff]/20 font-medium shadow-2xs"
            >
          </div>

          <!-- 操作按钮组：重置 + 明亮的查询按钮 -->
          <div class="flex items-center gap-2 shrink-0">
            <button
              @click="resetCallbackFilter"
              class="px-4 py-2 bg-white border border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-bold transition-all cursor-pointer shadow-2xs"
            >
              重置
            </button>
            <button
              @click="handleSearchCallback"
              class="px-5 py-2 bg-[#1677FF] hover:bg-blue-600 text-white rounded-xl text-xs font-black shadow-md shadow-blue-500/25 flex items-center gap-1.5 transition-all cursor-pointer"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              <span>查询</span>
            </button>
          </div>

          <div class="ml-auto text-xs text-slate-400 font-medium self-center">
            系统已自动将排队超时或未接通话拦截进入漏话回拨池
          </div>
        </div>

        <!-- 📋 未接待回拨数据表格 (严格落实字段：客户号码、运营商、坐席姓名、坐席工号、进线时间、排队放弃原因、等待耗时、状态) -->
        <div class="flex-1 overflow-x-auto overflow-y-auto">
          <table class="w-full text-sm text-left">
            <thead class="text-slate-400 border-b border-slate-100 pb-2 text-xs font-bold uppercase sticky top-0 bg-white z-10">
              <tr>
                <th class="pb-3 px-3.5">客户号码</th>
                <th class="pb-3 px-3">运营商</th>
                <th class="pb-3 px-3">坐席姓名</th>
                <th class="pb-3 px-2.5">坐席工号</th>
                <th class="pb-3 px-3.5">进线时间</th>
                <th class="pb-3 px-3.5">排队放弃原因</th>
                <th class="pb-3 px-3">等待耗时</th>
                <th class="pb-3 px-3">状态</th>
                <th class="pb-3 px-4 text-right">回访调度 / 操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              <!-- 空状态提示 (真实测试环境引导) -->
              <tr v-if="filteredCallbackRecords.length === 0">
                <td colspan="9" class="py-16 text-center">
                  <div class="flex flex-col items-center justify-center">
                    <div class="w-14 h-14 rounded-2xl bg-amber-50 border border-amber-100 flex items-center justify-center text-2xl mb-3 shadow-xs">
                      ⏳
                    </div>
                    <p class="text-sm font-bold text-slate-700 mb-1">数据库已进入真实测试模式，暂无未接待回拨任务</p>
                    <p class="text-xs text-slate-400 max-w-sm leading-relaxed">当有客户呼入因排队超时、坐席全忙或放弃未接听时，系统将自动产生真实待回拨工单并在此呈现</p>
                  </div>
                </td>
              </tr>
              <tr v-for="item in pagedCallbackRecords" :key="item.id" class="hover:bg-slate-50/80 transition-colors">
                <!-- 1. 客户号码 -->
                <td class="py-4 px-3.5 font-mono font-bold text-slate-900 text-sm">
                  {{ item.customerPhone }}
                </td>

                <!-- 2. 运营商 -->
                <td class="py-4 px-3">
                  <span class="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold">
                    {{ item.carrier }}
                  </span>
                </td>

                <!-- 3. 坐席姓名 -->
                <td class="py-4 px-3 font-bold text-slate-800 text-sm">
                  {{ item.agentName }}
                </td>

                <!-- 4. 坐席工号 -->
                <td class="py-4 px-2.5">
                  <span class="font-mono text-xs text-slate-600 font-bold bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                    {{ item.agentWorkNo }}
                  </span>
                </td>

                <!-- 5. 进线时间 -->
                <td class="py-4 px-3.5 font-mono text-slate-500 text-xs">
                  {{ item.inboundTime }}
                </td>

                <!-- 6. 排队放弃原因 -->
                <td class="py-4 px-3.5 text-slate-700 text-sm font-medium">
                  {{ item.abandonReason }}
                </td>

                <!-- 7. 等待耗时 -->
                <td class="py-4 px-3 font-mono text-amber-600 font-black text-sm">
                  {{ item.waitDuration }}
                </td>

                <!-- 8. 状态 -->
                <td class="py-4 px-3">
                  <span v-if="item.status === 'PENDING'" class="px-2.5 py-1 rounded-full bg-rose-50 text-rose-600 font-bold text-xs">
                    待回拨
                  </span>
                  <span v-else-if="item.status === 'ASSIGNED'" class="px-2.5 py-1 rounded-full bg-indigo-50 text-brand-600 font-bold text-xs">
                    已派单 ({{ item.assignee }})
                  </span>
                  <span v-else class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold text-xs">
                    已回呼
                  </span>
                </td>

                <!-- 操作栏：回访调度 -->
                <td class="py-4 px-4 text-right">
                  <div v-if="item.status === 'PENDING'" class="flex items-center justify-end gap-2">
                    <button
                      @click="handleDispatch(item, '舒欣', '901415')"
                      class="px-3 py-1.5 bg-brand-500 hover:bg-brand-600 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs"
                    >
                      派给舒欣
                    </button>
                    <button
                      @click="handleDispatch(item, '陈松', '901473')"
                      class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 rounded-xl font-bold transition cursor-pointer text-xs"
                    >
                      派给陈松
                    </button>
                    <button
                      @click="handleCallbackCall(item)"
                      class="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-xl font-bold transition cursor-pointer text-xs"
                    >
                      一键回呼
                    </button>
                  </div>
                  <div v-else class="flex items-center justify-end gap-2">
                    <button
                      @click="handleCallbackCall(item)"
                      class="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-bold transition cursor-pointer text-xs"
                    >
                      再次回呼
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 底部分页 (未接待回拨) -->
        <div class="pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500 shrink-0">
          <span class="text-xs text-slate-600 font-medium">共 {{ filteredCallbackRecords.length }} 条漏话回访任务</span>
          <div v-if="filteredCallbackRecords.length > 0">
            <el-pagination
              v-model:current-page="cbPageNum"
              v-model:page-size="cbPageSize"
              :total="filteredCallbackRecords.length"
              :page-sizes="[10, 20, 50]"
              layout="sizes, prev, pager, next"
              size="small"
              background
            />
          </div>
          <span v-else class="text-slate-400 text-xs font-mono">第 0 / 0 页</span>
        </div>
      </div>
    </div>

    <!-- 3. 录音在线试听悬浮播放器条 -->
    <div v-if="playingAudio" class="fixed bottom-8 inset-x-0 mx-auto max-w-2xl bg-white rounded-full shadow-popover border border-slate-200 p-3 px-6 flex items-center justify-between z-50 animate-fadeIn">
      <div class="flex items-center gap-3.5">
        <div class="w-10 h-10 rounded-full bg-brand-500 text-white flex items-center justify-center shadow-pill text-sm font-bold">
          🎧
        </div>
        <div>
          <div class="text-sm font-bold text-slate-900 flex items-center gap-2">
            <span>{{ currentAudioInfo?.title }}</span>
            <span class="text-xs text-slate-400 font-mono">({{ currentAudioInfo?.phone }})</span>
            <span class="text-xs text-indigo-500 font-mono bg-indigo-50 px-2 py-0.5 rounded font-bold">电信级高清</span>
          </div>
          <div class="text-xs text-slate-400 font-mono font-bold mt-0.5">总时长: {{ currentAudioInfo?.duration }}</div>
        </div>
      </div>

      <!-- 真实 HTML5 音频流播放器 -->
      <div class="flex items-center gap-3">
        <audio
          v-if="currentAudioUrl"
          :src="currentAudioUrl"
          controls
          autoplay
          class="h-9 w-72 outline-none"
        ></audio>
        <button @click="closeAudio" class="text-slate-400 hover:text-slate-600 font-bold text-sm cursor-pointer px-2 py-1">
          ✕
        </button>
      </div>
    </div>

    <!-- 4. 弹窗: 全生命周期过程详情追踪 (Stage + Action 时序流水线卡片) -->
    <div v-if="showDetailModal && selectedCdr" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="w-full max-w-4xl max-h-[92vh] bg-white rounded-3xl shadow-2xl border border-slate-100 overflow-hidden flex flex-col p-6 space-y-4">
        
        <!-- 弹窗顶部标题 -->
        <div class="flex items-center justify-between pb-3 border-b border-slate-100 shrink-0">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-xl bg-indigo-50 border border-indigo-100 text-[#1677ff] font-bold flex items-center justify-center text-base">
              🧭
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h2 class="text-base font-black text-slate-900">通话全生命周期过程详情追踪 (Call Journey Trace)</h2>
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border"
                  :class="selectedCdr.status === 'ANSWERED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-rose-50 text-rose-700 border-rose-200'"
                >
                  {{ selectedCdr.status === 'ANSWERED' ? '● 通话已接通' : '● 通话未接起/异常' }}
                </span>
              </div>
              <p class="text-xs text-slate-400 mt-0.5 font-mono">
                流水号: <strong class="text-slate-700">{{ selectedCdr.id }}</strong>
                <span class="mx-1.5 text-slate-300">|</span>
                呼叫方向: <span class="font-bold text-slate-700">{{ selectedCdr.direction === 'OUTBOUND' ? '外呼' : selectedCdr.direction === 'INBOUND' ? '呼入' : '内线' }}</span>
                <span class="mx-1.5 text-slate-300">|</span>
                引擎: FreeSWITCH 1.11.3 (SIP/WebRTC)
              </p>
            </div>
          </div>
          <button @click="showDetailModal = false" class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-sm font-bold cursor-pointer">✕</button>
        </div>

        <!-- 通话关键元数据 Bento 汇总卡片 -->
        <div class="grid grid-cols-4 gap-3 text-xs shrink-0">
          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">主叫客户</span>
            <div class="font-bold text-slate-900 truncate">{{ selectedCdr.callerName }}</div>
            <div class="font-mono text-slate-500 text-[11px]">{{ selectedCdr.callerPhone }} ({{ selectedCdr.carrier }})</div>
          </div>

          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">服务坐席</span>
            <div class="font-bold text-slate-900 truncate">{{ selectedCdr.agentName }}</div>
            <div class="font-mono text-slate-500 text-[11px]">工号: {{ selectedCdr.agentWorkNo }}</div>
          </div>

          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">时延与录音</span>
            <div class="flex items-center gap-1.5 font-bold text-slate-900">
              <span>振铃: {{ selectedCdr.ringDuration }}</span>
            </div>
            <div class="font-mono text-brand-600 font-bold text-[11px]">
              录音: {{ selectedCdr.audioDuration || '无录音' }}
            </div>
          </div>

          <div class="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span class="text-slate-400 text-[11px]">路由策略与评价</span>
            <div class="font-bold text-indigo-700 truncate">
              {{ selectedCdr.routeMode === 'DID_DIRECT' ? 'DID 直达专席' : selectedCdr.routeMode === 'RULE_ENGINE' ? '多维规则引擎决策' : selectedCdr.routeMode === 'HTTP_CALLBACK' ? '业务接口动态回调' : '标准流程路由' }}
            </div>
            <div class="font-mono text-[11px] text-amber-600 font-bold">
              {{ selectedCdr.satisfactionScore ? `客户评价: ${selectedCdr.satisfactionScore} 星 ★★★★★` : '未评价 / 异常挂断' }}
            </div>
          </div>
        </div>

        <div class="flex-1 flex items-center justify-center border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-slate-500">
          动作执行轨迹尚未接入持久化查询。当前仅展示话单、录音和通道事实，不推测呼叫过程。
        </div>

        <!-- 弹窗底部操作栏 -->
        <div class="pt-3 border-t border-slate-100 flex items-center justify-between shrink-0">
          <div class="flex items-center gap-2">
            <button
              v-if="selectedCdr.recordingUrl"
              @click="playAudio(selectedCdr.callerName, selectedCdr.callerPhone, selectedCdr.audioDuration || '', selectedCdr.recordingUrl, selectedCdr.rawId)"
              class="px-4 py-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-xs font-bold rounded-xl flex items-center gap-1.5 transition cursor-pointer"
            >
              <span>▶️</span>
              <span>试听双轨录音 ({{ selectedCdr.audioDuration }})</span>
            </button>
            <span v-else-if="selectedCdr.audioDuration" class="text-xs text-slate-400">
              该通话未产生录音文件（通话 {{ selectedCdr.audioDuration }}）
            </span>
          </div>

          <button @click="showDetailModal = false" class="px-6 py-2 bg-[#1677ff] hover:bg-blue-600 text-white text-xs font-bold rounded-xl cursor-pointer shadow-xs transition">
            关闭
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
:deep(.el-date-editor--datetimerange.el-input__wrapper) {
  border-radius: 0.75rem !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 12px !important;
  height: 38px !important;
  background-color: #ffffff !important;
  transition: all 0.2s ease;
}
:deep(.el-date-editor--datetimerange.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
}
:deep(.el-date-editor--datetimerange.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px #1677ff inset !important;
}
:deep(.el-range-input) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace !important;
  font-size: 12px !important;
  color: #1e293b !important;
}
:deep(.el-range-separator) {
  font-size: 12px !important;
  color: #94a3b8 !important;
  font-weight: 700 !important;
}
</style>

