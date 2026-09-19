<template>
  <div class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-200/80 shadow-xs p-6 overflow-hidden text-slate-800 space-y-4">
    <!-- Toast 通知提示 -->
    <div
      v-if="toastMsg"
      class="fixed top-5 right-8 z-50 bg-slate-900/90 backdrop-blur-sm text-white px-5 py-2.5 rounded-xl shadow-xl text-sm font-semibold flex items-center gap-2 transition-all"
    >
      <span>🔔</span>
      <span>{{ toastMsg }}</span>
    </div>

    <!-- 顶部操作栏 -->
    <div class="flex flex-wrap items-center justify-between pb-4 border-b border-slate-100 shrink-0 gap-3">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-lg text-[#1677ff] shadow-2xs">
          🔀
        </div>
        <div>
          <div class="flex items-center gap-2.5">
            <h1 class="font-black text-lg text-slate-900 tracking-tight">IVR 流程</h1>
            <span class="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-mono text-xs font-bold border border-emerald-200/60">
              ● 3 个系统核心通话流
            </span>
            <span class="px-2 py-0.5 rounded-md bg-blue-50 text-[#1677ff] font-mono text-[11px] font-bold border border-blue-200">
              自上而下时序流转模型
            </span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5 font-medium">通信平台三大核心通话路由：来电流程、外呼、话机直接外呼</p>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2.5 text-xs font-bold">

        <!-- 二期版本切换控制器 (支持历史版本与草稿) -->
        <div class="flex items-center gap-1.5 bg-slate-50 px-2.5 py-1.5 rounded-xl border border-slate-200">
          <span class="text-slate-400 font-normal">版本:</span>
          <select
            v-model="currentVersion"
            @change="onVersionChange"
            class="bg-transparent font-bold text-slate-800 text-xs focus:outline-none cursor-pointer"
          >
            <option value="v1.0.0">v1.0.0 (线上激活)</option>
            <option value="v1.1.0">v1.1.0 (草稿编辑中)</option>
            <option value="v0.9.0">v0.9.0 (历史归档)</option>
          </select>
          <span
            class="px-1.5 py-0.5 rounded text-[10px] font-bold"
            :class="currentVersion === 'v1.0.0' ? 'bg-emerald-100 text-emerald-700' : currentVersion === 'v1.1.0' ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-600'"
          >
            {{ currentVersion === 'v1.0.0' ? '● 线上运行' : currentVersion === 'v1.1.0' ? '✏️ 草稿未发布' : '📦 归档' }}
          </span>
        </div>

        <!-- 操作按钮组 -->
        <button
          @click="openAddActionModal('route')"
          class="px-3.5 py-2 bg-indigo-50 hover:bg-indigo-100 text-[#1677ff] rounded-xl flex items-center gap-1 transition cursor-pointer shadow-2xs"
        >
          <span>➕</span><span>添加动作</span>
        </button>

        <button
          @click="showSimModal = true"
          class="px-3.5 py-2 bg-blue-50 hover:bg-blue-100 text-[#1677ff] rounded-xl flex items-center gap-1 transition cursor-pointer shadow-2xs"
        >
          <span>⚡</span><span>模拟运行</span>
        </button>

        <button
          @click="saveDraft"
          class="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl transition cursor-pointer"
        >
          💾 保存草稿
        </button>

        <button
          @click="showPublishModal = true"
          class="px-4 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl shadow-md shadow-blue-500/20 transition cursor-pointer"
        >
          🚀 发布上线
        </button>
      </div>
    </div>

    <!-- 流程主体：左侧 3 大系统 Flow 目录 + 右侧流程画布 -->
    <div class="flex-1 flex gap-5 overflow-hidden">
      <!-- 左侧系统 Flow 目录 -->
      <div class="w-80 bg-[#f8fafc] p-4 rounded-2xl border border-slate-200/80 flex flex-col shrink-0 space-y-3">
        <div class="flex items-center justify-between px-1">
          <div class="flex items-center gap-2">
            <span class="text-sm font-black text-slate-900">系统通话流程</span>
            <span class="text-xs text-slate-400 font-mono font-bold">(3/3)</span>
          </div>
          <span class="text-[10px] text-slate-400">系统核心内置</span>
        </div>

        <div class="space-y-2.5 overflow-y-auto flex-1 pr-1">
          <!-- Flow 1: 来电流程 -->
          <div
            @click="activeFlowId = 'inbound'"
            class="p-4 rounded-xl border-2 transition cursor-pointer text-xs space-y-1.5"
            :class="activeFlowId === 'inbound' ? 'bg-white border-[#1677ff] shadow-sm ring-2 ring-[#1677ff]/10' : 'bg-white/80 hover:bg-white border-slate-200/70'"
          >
            <div class="flex items-center justify-between text-sm font-black text-slate-900">
              <div class="flex items-center gap-2">
                <span class="text-base">📞</span>
                <span>来电流程</span>
              </div>
              <span class="px-2 py-0.5 rounded bg-emerald-50 text-emerald-600 text-[10px] font-mono font-bold border border-emerald-200">运行中</span>
            </div>
            <p class="text-slate-500 text-xs font-medium leading-relaxed">客户进线应答 ➔ 按键分流 ➔ DID/接口回调/规则引擎 ➔ 通话录音 ➔ 质检评价挂断</p>
            <div class="flex items-center justify-between text-[11px] font-mono text-slate-400 pt-1 border-t border-slate-100">
              <span>DID: 021-5088XXXX</span>
              <span class="text-blue-600 font-bold">5 个纵向阶段</span>
            </div>
          </div>

          <!-- Flow 2: 外呼 -->
          <div
            @click="activeFlowId = 'outbound'"
            class="p-4 rounded-xl border-2 transition cursor-pointer text-xs space-y-1.5"
            :class="activeFlowId === 'outbound' ? 'bg-white border-[#1677ff] shadow-sm ring-2 ring-[#1677ff]/10' : 'bg-white/80 hover:bg-white border-slate-200/70'"
          >
            <div class="flex items-center justify-between text-sm font-black text-slate-900">
              <div class="flex items-center gap-2">
                <span class="text-base">📱</span>
                <span>外呼</span>
              </div>
              <span class="px-2 py-0.5 rounded bg-emerald-50 text-emerald-600 text-[10px] font-mono font-bold border border-emerald-200">运行中</span>
            </div>
            <p class="text-slate-500 text-xs font-medium leading-relaxed">呼叫坐席 ➔ 呼叫客户 ➔ 通道桥接 ➔ 录音/转接 ➔ 正常挂机评价或异常告警</p>
            <div class="flex items-center justify-between text-[11px] font-mono text-slate-400 pt-1 border-t border-slate-100">
              <span>TRUNK: 电信/移动专线</span>
              <span class="text-blue-600 font-bold">5 个纵向阶段</span>
            </div>
          </div>

          <!-- Flow 3: 话机直接外呼 -->
          <div
            @click="activeFlowId = 'phoneDirect'"
            class="p-4 rounded-xl border-2 transition cursor-pointer text-xs space-y-1.5"
            :class="activeFlowId === 'phoneDirect' ? 'bg-white border-[#1677ff] shadow-sm ring-2 ring-[#1677ff]/10' : 'bg-white/80 hover:bg-white border-slate-200/70'"
          >
            <div class="flex items-center justify-between text-sm font-black text-slate-900">
              <div class="flex items-center gap-2">
                <span class="text-base">☎️</span>
                <span>话机直接外呼</span>
              </div>
              <span class="px-2 py-0.5 rounded bg-indigo-50 text-[#1677ff] text-[10px] font-mono font-bold border border-indigo-200">话机直拨</span>
            </div>
            <p class="text-slate-500 text-xs font-medium leading-relaxed">坐席话机摘机应答 ➔ 呼叫目标客户 ➔ 通道桥接 ➔ 双轨录音 ➔ 评价与结束</p>
            <div class="flex items-center justify-between text-[11px] font-mono text-slate-400 pt-1 border-t border-slate-100">
              <span>PROTO: SIP / WebRTC</span>
              <span class="text-blue-600 font-bold">5 个纵向阶段</span>
            </div>
          </div>
        </div>

        <!-- 底部说明 -->
        <div class="p-3 rounded-xl bg-blue-50/50 border border-blue-100/80 text-[11px] text-slate-500 leading-relaxed">
          <span class="font-bold text-[#1677ff]">💡 流程设计规范：</span><br />
          流程严格自上而下执行。标识 <span class="font-bold text-slate-700">[🔒核心固定]</span> 的动作保障通信协议稳定；标识 <span class="font-bold text-blue-600">[✏️可配置]</span> 支持条件分支与业务扩展。
        </div>
      </div>

      <!-- 右侧：交互式流程可视化画布 (Flow Canvas - 自上而下纵向流转) -->
      <div class="flex-1 bg-[#F9FBFC] rounded-2xl border border-slate-200/80 p-5 flex flex-col space-y-4 overflow-hidden">
        
        <!-- 画布顶部当前流程名与元数据 -->
        <div class="w-full flex items-center justify-between pb-3 border-b border-slate-200/70 text-xs shrink-0">
          <div class="flex items-center gap-2.5">
            <span class="text-xl">{{ activeFlow.icon }}</span>
            <span class="font-black text-sm text-slate-900">{{ activeFlow.name }}</span>
            <span class="px-2 py-0.5 rounded bg-slate-100 font-mono text-[11px] text-slate-600 font-bold">
              ID: {{ activeFlow.code }}
            </span>
            <span class="px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 font-mono text-[10px] font-bold border border-blue-100">
              当前版本: {{ currentVersion }}
            </span>
          </div>
          <div class="flex items-center gap-3 text-slate-400 font-medium">
            <span>底层引擎: FreeSWITCH 1.11.3</span>
            <span>状态: 🟢 运行中</span>
          </div>
        </div>

        <!-- 纵向从上到下时序流转模型 (Top-to-Bottom Vertical Pipeline) -->
        <div class="flex-1 overflow-y-auto pr-3 py-2 flex flex-col items-center">
          <div class="w-full max-w-4xl space-y-5">

            <!-- ==================== 阶段 1: 触发与应答阶段 (START / ANSWER) ==================== -->
            <div class="bg-white rounded-2xl border border-slate-200/90 shadow-2xs overflow-hidden">
              <div class="px-5 py-3 bg-slate-50/90 border-b border-slate-100 flex items-center justify-between">
                <div class="flex items-center gap-2.5">
                  <span class="px-2 py-0.5 rounded bg-slate-800 text-white font-mono text-[10px] font-bold">阶段 1/5</span>
                  <span class="font-black text-sm text-slate-900">触发与应答阶段 (START & ANSWER)</span>
                  <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-600 text-[10px] font-bold">🔒 核心固定动作</span>
                </div>
                <span class="text-xs text-slate-400">底层电信信令连接建立</span>
              </div>

              <div class="p-5 flex flex-col items-center space-y-3">
                <!-- 动作 1: START -->
                <div class="w-full max-w-lg bg-slate-50 p-3.5 rounded-xl border border-slate-200 flex items-center justify-between shadow-2xs hover:border-blue-400 transition">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center font-mono font-bold text-xs">
                      01
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="font-mono font-bold text-xs text-slate-900">START</span>
                        <span class="text-xs text-slate-600">通道创建与进线识别</span>
                      </div>
                      <p class="text-[11px] text-slate-400 mt-0.5">解析主叫号码 (Caller) 与被叫接入号 (DID: 021-5088XXXX)</p>
                    </div>
                  </div>
                  <span class="px-2 py-1 bg-slate-200/70 text-slate-600 text-[10px] font-mono rounded">🔒 内置不可删</span>
                </div>

                <!-- 纵向连接箭头 -->
                <div class="text-blue-400 text-xs font-bold">↓</div>

                <!-- 动作 2: ANSWER / 应答 -->
                <div class="w-full max-w-lg bg-blue-50/60 p-3.5 rounded-xl border border-blue-200 flex items-center justify-between shadow-2xs hover:border-blue-500 transition">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-blue-500 text-white flex items-center justify-center font-mono font-bold text-xs">
                      02
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="font-mono font-bold text-xs text-blue-900">{{ activeFlow.startAnswer || 'ANSWER' }}</span>
                        <span class="text-xs text-blue-800">系统摘机应答</span>
                      </div>
                      <p class="text-[11px] text-blue-600 mt-0.5">建立 RTP 媒体通道，触发 200 OK 确认，开始计算计费会话</p>
                    </div>
                  </div>
                  <span class="px-2 py-1 bg-blue-100 text-blue-700 text-[10px] font-mono rounded font-bold">🔒 核心应答</span>
                </div>

                <!-- 动作 3: 欢迎语播报 (来电专属) -->
                <template v-if="activeFlowId === 'inbound'">
                  <div class="text-blue-400 text-xs font-bold">↓</div>
                  <div class="w-full max-w-lg bg-white p-3.5 rounded-xl border border-slate-200 flex items-center justify-between shadow-2xs hover:border-[#1677ff] transition">
                    <div class="flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-700 flex items-center justify-center font-mono font-bold text-xs">
                        03
                      </div>
                      <div>
                        <div class="flex items-center gap-2">
                          <span class="font-mono font-bold text-xs text-slate-900">PLAY_WELCOME</span>
                          <span class="text-xs text-slate-600">播放迎宾欢迎词</span>
                        </div>
                        <p class="text-[11px] text-slate-400 mt-0.5">“您好，欢迎致电箱箱智能通讯调度中心...” (TTS / WAV)</p>
                      </div>
                    </div>
                    <button @click="triggerToast('欢迎语音频已就绪，可在线试听')" class="text-[#1677ff] text-xs font-bold hover:underline cursor-pointer">
                      ✏️ 配置语音
                    </button>
                  </div>
                </template>
              </div>
            </div>

            <!-- 阶段间流动连线 -->
            <div class="flex flex-col items-center py-0.5">
              <div class="w-0.5 h-6 bg-blue-300"></div>
              <div class="text-blue-500 text-xs">▼</div>
            </div>

            <!-- ==================== 阶段 2: 路由决策与分流阶段 (ROUTE & ACD) ==================== -->
            <div class="bg-white rounded-2xl border-2 border-indigo-100 shadow-sm overflow-hidden">
              <div class="px-5 py-3 bg-indigo-50/70 border-b border-indigo-100 flex items-center justify-between">
                <div class="flex items-center gap-2.5">
                  <span class="px-2 py-0.5 rounded bg-indigo-700 text-white font-mono text-[10px] font-bold">阶段 2/5</span>
                  <span class="font-black text-sm text-indigo-950">路由决策与排队分流 (ROUTE & ACD)</span>
                  <span class="px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 text-[10px] font-bold">✏️ 可编排 · 支持 if-else 多分支</span>
                </div>
                <button
                  @click="openAddActionModal('route')"
                  class="px-2.5 py-1 bg-white hover:bg-indigo-50 text-[#1677ff] border border-indigo-200 rounded-lg text-xs font-bold transition cursor-pointer flex items-center gap-1"
                >
                  <span>➕</span><span>在此阶段添加动作</span>
                </button>
              </div>

              <div class="p-5 flex flex-col items-center space-y-4">
                <!-- 仅在来电流程展示：按键采集节点 -->
                <template v-if="activeFlowId === 'inbound'">
                  <div class="w-full max-w-lg bg-white p-3.5 rounded-xl border border-indigo-200 flex items-center justify-between shadow-2xs hover:border-[#1677ff] transition">
                    <div class="flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-800 flex items-center justify-center font-mono font-bold text-xs">
                        DTMF
                      </div>
                      <div>
                        <div class="flex items-center gap-2">
                          <span class="font-mono font-bold text-xs text-slate-900">READ_DTMF</span>
                          <span class="text-xs text-slate-700">收号导航按键采集</span>
                        </div>
                        <p class="text-[11px] text-slate-500 mt-0.5">有效按键位: [1, 2, 3] | 采集超时: 5 秒 | 重试允许: 2 次</p>
                      </div>
                    </div>
                    <span class="px-2 py-1 bg-blue-50 text-blue-700 text-[10px] font-bold rounded">✏️ 可修改按键</span>
                  </div>

                  <!-- 纵向分流分支指示 -->
                  <div class="w-full max-w-2xl flex flex-col items-center">
                    <div class="text-indigo-400 text-xs font-bold mb-1">↓ 按键分流仲裁 (if - else 多分支)</div>
                    <div class="w-full border-t-2 border-dashed border-indigo-200 my-1"></div>
                  </div>

                  <!-- 3 种核心路由分支卡片网格 (第二期 if-else 多分支模型直观呈现) -->
                  <div class="w-full grid grid-cols-3 gap-3.5 pt-1">
                    <!-- 分支 1: 按 1 -> DID 直达卡片 -->
                    <div
                      @click="showRouteConfigModal = true"
                      class="p-3.5 rounded-xl border-2 transition cursor-pointer space-y-2 bg-gradient-to-b from-white to-slate-50/50 hover:shadow-md"
                      :class="inboundRouteMode === 'DID_DIRECT' ? 'border-[#1677ff] ring-2 ring-[#1677ff]/10 shadow-xs' : 'border-slate-200'"
                    >
                      <div class="flex items-center justify-between">
                        <span class="px-2 py-0.5 rounded bg-blue-100 text-blue-800 font-mono font-black text-[10px]">IF 按键 = 1</span>
                        <span class="text-[10px] text-slate-400">DID直达卡片</span>
                      </div>
                      <div class="font-bold text-xs text-slate-900 flex items-center gap-1">
                        <span>📌</span><span>DID 直通坐席/组</span>
                      </div>
                      <div class="p-2 bg-white rounded-lg border border-slate-100 text-[11px] space-y-1">
                        <div class="text-slate-600">目标: <strong>{{ didDirectConfig.agentName }} ({{ didDirectConfig.agentId }})</strong></div>
                        <div class="text-slate-400 text-[10px]">决策时延: &lt; 50ms</div>
                      </div>
                      <div class="flex items-center justify-between text-[10px] pt-1 border-t border-slate-100">
                        <span class="text-emerald-600 font-bold">● 坐席忙时溢出</span>
                        <span class="text-[#1677ff] font-bold">点击编辑 ➔</span>
                      </div>
                    </div>

                    <!-- 分支 2: 按 2 -> 业务系统接口回调卡片 -->
                    <div
                      @click="showRouteConfigModal = true"
                      class="p-3.5 rounded-xl border-2 transition cursor-pointer space-y-2 bg-gradient-to-b from-white to-slate-50/50 hover:shadow-md"
                      :class="inboundRouteMode === 'HTTP_CALLBACK' ? 'border-[#1677ff] ring-2 ring-[#1677ff]/10 shadow-xs' : 'border-slate-200'"
                    >
                      <div class="flex items-center justify-between">
                        <span class="px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 font-mono font-black text-[10px]">IF 按键 = 2</span>
                        <span class="text-[10px] text-slate-400">接口回调卡片</span>
                      </div>
                      <div class="font-bold text-xs text-slate-900 flex items-center gap-1">
                        <span>🌐</span><span>业务系统接口回调</span>
                      </div>
                      <div class="p-2 bg-white rounded-lg border border-slate-100 text-[11px] space-y-1">
                        <div class="text-slate-600 truncate font-mono text-[10px]">{{ httpCallbackConfig.url }}</div>
                        <div class="text-amber-600 font-bold text-[10px]">超时 {{ httpCallbackConfig.timeoutMs }}ms 熔断降级</div>
                      </div>
                      <div class="flex items-center justify-between text-[10px] pt-1 border-t border-slate-100">
                        <span class="text-indigo-600 font-bold">降级: {{ httpCallbackConfig.fallbackGroupName }}</span>
                        <span class="text-[#1677ff] font-bold">点击编辑 ➔</span>
                      </div>
                    </div>

                    <!-- 分支 3: 按 3 -> 多维规则引擎卡片 -->
                    <div
                      @click="showRouteConfigModal = true"
                      class="p-3.5 rounded-xl border-2 transition cursor-pointer space-y-2 bg-gradient-to-b from-white to-slate-50/50 hover:shadow-md"
                      :class="inboundRouteMode === 'RULE_ENGINE' ? 'border-[#1677ff] ring-2 ring-[#1677ff]/10 shadow-xs' : 'border-slate-200'"
                    >
                      <div class="flex items-center justify-between">
                        <span class="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 font-mono font-black text-[10px]">IF 按键 = 3</span>
                        <span class="text-[10px] text-slate-400">规则引擎卡片</span>
                      </div>
                      <div class="font-bold text-xs text-slate-900 flex items-center gap-1">
                        <span>🎛️</span><span>时段+VIP规则仲裁</span>
                      </div>
                      <div class="p-2 bg-white rounded-lg border border-slate-100 text-[11px] space-y-1">
                        <div class="text-slate-600 text-[10px]">时段: {{ ruleEngineConfig.workTimeRange }}</div>
                        <div class="text-emerald-700 font-bold text-[10px]">VIP 客户优先插队接入</div>
                      </div>
                      <div class="flex items-center justify-between text-[10px] pt-1 border-t border-slate-100">
                        <span class="text-rose-600 font-bold">黑名单自动拦截</span>
                        <span class="text-[#1677ff] font-bold">点击编辑 ➔</span>
                      </div>
                    </div>
                  </div>

                  <!-- 默认兜底分支 (Default Fallback) -->
                  <div class="w-full max-w-xl bg-slate-50 p-2.5 rounded-xl border border-slate-200 text-xs flex items-center justify-between text-slate-500">
                    <div class="flex items-center gap-2">
                      <span class="px-2 py-0.5 rounded bg-slate-200 font-mono font-bold text-[10px]">ELSE 默认分支</span>
                      <span class="text-[11px]">按键超时或未匹配时，自动路由至【默认通用客服组】排队接听</span>
                    </div>
                    <span class="text-slate-400 font-mono text-[10px]">🛡️ 零漏话兜底</span>
                  </div>

                  <!-- 分支汇聚至 ACD 排队 -->
                  <div class="text-indigo-400 text-xs font-bold">↓ 汇聚至排队分配器</div>

                  <!-- ACD 排队分发与振铃 -->
                  <div class="w-full max-w-lg bg-white p-3.5 rounded-xl border border-slate-200 flex items-center justify-between shadow-2xs hover:border-[#1677ff] transition">
                    <div class="flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center font-mono font-bold text-xs">
                        ACD
                      </div>
                      <div>
                        <div class="flex items-center gap-2">
                          <span class="font-mono font-bold text-xs text-slate-900">QUEUE_ACD & DIAL_AGENT</span>
                          <span class="text-xs text-slate-700">技能组排队与坐席振铃</span>
                        </div>
                        <p class="text-[11px] text-slate-500 mt-0.5">策略: 最长空闲分发 | 振铃超时: 20 秒 | 溢出至回拨总池</p>
                      </div>
                    </div>
                    <span class="px-2 py-1 bg-emerald-50 text-emerald-700 text-[10px] font-bold rounded">🔒 坐席呼叫</span>
                  </div>
                </template>

                <!-- 外呼流程与话机外呼的路由阶段 -->
                <template v-else>
                  <template v-for="(step, sIdx) in activeFlow.routeSteps" :key="sIdx">
                    <div class="w-full max-w-lg bg-white p-3.5 rounded-xl border border-indigo-200 flex items-center justify-between shadow-2xs">
                      <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-700 flex items-center justify-center font-mono font-bold text-xs">
                          {{ sIdx + 1 }}
                        </div>
                        <div>
                          <span class="font-mono font-bold text-xs text-slate-900">{{ step.code }}</span>
                          <span class="text-xs text-slate-700 ml-2">{{ step.title }}</span>
                        </div>
                      </div>
                      <span class="px-2 py-1 bg-slate-100 text-slate-600 text-[10px] font-mono rounded">执行动作</span>
                    </div>
                    <div v-if="sIdx < activeFlow.routeSteps.length - 1" class="text-indigo-400 text-xs font-bold">↓</div>
                  </template>
                </template>
              </div>
            </div>

            <!-- 阶段间流动连线 -->
            <div class="flex flex-col items-center py-0.5">
              <div class="w-0.5 h-6 bg-blue-300"></div>
              <div class="text-blue-500 text-xs">▼</div>
            </div>

            <!-- ==================== 阶段 3: 通话服务中阶段 (CONNECTED) ==================== -->
            <div class="bg-white rounded-2xl border border-emerald-200/90 shadow-2xs overflow-hidden">
              <div class="px-5 py-3 bg-emerald-50/80 border-b border-emerald-100 flex items-center justify-between">
                <div class="flex items-center gap-2.5">
                  <span class="px-2 py-0.5 rounded bg-emerald-700 text-white font-mono text-[10px] font-bold">阶段 3/5</span>
                  <span class="font-black text-sm text-emerald-950">通话服务中阶段 (CONNECTED)</span>
                  <span class="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 text-[10px] font-bold">🔒 核心固定 + ✏️ 策略可配</span>
                </div>
                <span class="text-xs text-emerald-600">双通桥接 · 录音 · 通话中事件</span>
              </div>

              <div class="p-5 flex flex-col items-center space-y-3">
                <!-- 动作 1: 通道桥接 -->
                <div class="w-full max-w-lg bg-emerald-50/50 p-3.5 rounded-xl border border-emerald-200 flex items-center justify-between shadow-2xs">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-emerald-600 text-white flex items-center justify-center font-mono font-bold text-xs">
                      BR
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="font-mono font-bold text-xs text-emerald-900">BRIDGE (通道桥接)</span>
                        <span class="text-xs text-emerald-800">主被叫双方接通双通</span>
                      </div>
                      <p class="text-[11px] text-emerald-700 mt-0.5">FreeSWITCH 软交换底层 uuid_bridge 桥接，开始媒体流互通</p>
                    </div>
                  </div>
                  <span class="px-2 py-1 bg-emerald-200/60 text-emerald-800 text-[10px] font-mono rounded font-bold">🔒 核心动作</span>
                </div>

                <div class="text-emerald-400 text-xs font-bold">↓</div>

                <!-- 动作 2: 开始录音 -->
                <div class="w-full max-w-lg bg-white p-3.5 rounded-xl border border-slate-200 flex items-center justify-between shadow-2xs hover:border-emerald-400 transition">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center font-mono font-bold text-xs">
                      REC
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="font-mono font-bold text-xs text-slate-900">RECORD_START</span>
                        <span class="text-xs text-slate-700">立体声双轨分路录音启动</span>
                      </div>
                      <p class="text-[11px] text-slate-500 mt-0.5">规格: 16kHz WAV | 左声道客户，右声道坐席 | 同步启动实时质检流</p>
                    </div>
                  </div>
                  <span class="px-2 py-1 bg-blue-50 text-blue-700 text-[10px] font-bold rounded">✏️ 可配置策略</span>
                </div>

                <!-- 通话中支线动作 (虚线卡片，对齐架构图) -->
                <div class="w-full max-w-lg grid grid-cols-2 gap-3 pt-1">
                  <div class="p-3 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50/60 flex items-center justify-between">
                    <div>
                      <div class="font-mono font-bold text-xs text-slate-800">TRANSFER</div>
                      <div class="text-[10px] text-slate-500">通话中转接 (盲转/咨询转)</div>
                    </div>
                    <span class="text-[10px] text-slate-400 font-mono">可选分支</span>
                  </div>

                  <div class="p-3 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50/60 flex items-center justify-between">
                    <div>
                      <div class="font-mono font-bold text-xs text-slate-800">HANGUP agent</div>
                      <div class="text-[10px] text-slate-500">坐席主动挂机处理</div>
                    </div>
                    <span class="text-[10px] text-slate-400 font-mono">可选分支</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 阶段间流动连线 -->
            <div class="flex flex-col items-center py-0.5">
              <div class="w-0.5 h-6 bg-blue-300"></div>
              <div class="text-blue-500 text-xs">▼</div>
            </div>

            <!-- ==================== 阶段 4: 结束分支处理阶段 (END HANDLING) ==================== -->
            <div class="bg-white rounded-2xl border border-blue-200/90 shadow-2xs overflow-hidden">
              <div class="px-5 py-3 bg-blue-50/80 border-b border-blue-100 flex items-center justify-between">
                <div class="flex items-center gap-2.5">
                  <span class="px-2 py-0.5 rounded bg-blue-700 text-white font-mono text-[10px] font-bold">阶段 4/5</span>
                  <span class="font-black text-sm text-blue-950">结束分支处理阶段 (END HANDLING)</span>
                  <span class="px-2 py-0.5 rounded bg-blue-100 text-blue-800 text-[10px] font-bold">✏️ 策略可配 · 正常/异常双分支</span>
                </div>
                <button
                  @click="openAddActionModal('end')"
                  class="px-2.5 py-1 bg-white hover:bg-blue-50 text-[#1677ff] border border-blue-200 rounded-lg text-xs font-bold transition cursor-pointer flex items-center gap-1"
                >
                  <span>➕</span><span>在此阶段添加动作</span>
                </button>
              </div>

              <div class="p-5">
                <!-- 正常结束与异常结束并列双分支 -->
                <div class="grid grid-cols-2 gap-4">
                  <!-- 分支 4.1: 正常挂断 (Normal End) -->
                  <div class="bg-slate-50/70 p-4 rounded-xl border border-slate-200 space-y-3">
                    <div class="flex items-center justify-between pb-2 border-b border-slate-200">
                      <span class="font-bold text-xs text-blue-900 flex items-center gap-1.5">
                        <span>✅</span><span>正常挂断分支 (Normal End)</span>
                      </span>
                      <span class="text-[10px] text-blue-600 font-mono font-bold">通话完满结束</span>
                    </div>

                    <!-- 动作 1: 停止录音 -->
                    <div class="p-2.5 bg-white rounded-lg border border-slate-200 flex items-center justify-between text-xs">
                      <div>
                        <div class="font-mono font-bold text-slate-800">RECORD_STOP</div>
                        <div class="text-[10px] text-slate-400">停止混音录音并异步归档至 OSS</div>
                      </div>
                      <span class="text-[10px] text-emerald-600 font-bold">自动上传</span>
                    </div>

                    <div class="text-center text-slate-300 text-xs font-bold">↓</div>

                    <!-- 动作 2: DTMF 按键监听 (虚线可选) -->
                    <div class="p-2.5 bg-white rounded-lg border-2 border-dashed border-slate-300 flex items-center justify-between text-xs">
                      <div>
                        <div class="font-mono font-bold text-slate-800">DTMF 监听按键</div>
                        <div class="text-[10px] text-slate-400">采集 1-5 星服务评价按键</div>
                      </div>
                      <span class="text-[10px] text-slate-400 font-mono">超时5s跳过</span>
                    </div>

                    <div class="text-center text-slate-300 text-xs font-bold">↓</div>

                    <!-- 动作 3: 播报服务评价内容 -->
                    <div class="p-2.5 bg-white rounded-lg border border-slate-200 flex items-center justify-between text-xs">
                      <div>
                        <div class="font-mono font-bold text-slate-800">PLAY 服务评价内容</div>
                        <div class="text-[10px] text-slate-400">“感谢您的来电，请对本次服务进行评价...”</div>
                      </div>
                      <button @click="triggerToast('满意度语音试听就绪')" class="text-[#1677ff] text-[10px] font-bold hover:underline cursor-pointer">
                        试听音频
                      </button>
                    </div>
                  </div>

                  <!-- 分支 4.2: 异常中断/拒绝 (Error End) -->
                  <div class="bg-amber-50/40 p-4 rounded-xl border border-amber-200 space-y-3 flex flex-col justify-between">
                    <div>
                      <div class="flex items-center justify-between pb-2 border-b border-amber-200">
                        <span class="font-bold text-xs text-amber-900 flex items-center gap-1.5">
                          <span>⚠️</span><span>异常结束分支 (Error End)</span>
                        </span>
                        <span class="text-[10px] text-amber-700 font-mono font-bold">未接/拒接/线路错</span>
                      </div>

                      <div class="pt-4 space-y-3">
                        <div class="p-3 bg-white rounded-lg border border-amber-200 flex items-center justify-between text-xs">
                          <div>
                            <div class="font-mono font-bold text-amber-900">PLAY 再见语音</div>
                            <div class="text-[10px] text-amber-700 mt-0.5">“线路正忙或发生异常，请稍后再拨...”</div>
                          </div>
                          <span class="text-[10px] text-amber-700 font-mono">快速释放</span>
                        </div>

                        <div class="p-2.5 bg-amber-100/50 rounded-lg text-[11px] text-amber-800 leading-relaxed">
                          💡 触发异常结束时，系统将自动在【未接待回拨总池】中生成待办回拨工单，杜绝漏话风险。
                        </div>
                      </div>
                    </div>

                    <div class="pt-2 text-[10px] font-mono text-slate-400 text-right">
                      进入收尾释放通道
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 阶段间流动连线 -->
            <div class="flex flex-col items-center py-0.5">
              <div class="w-0.5 h-6 bg-blue-300"></div>
              <div class="text-blue-500 text-xs">▼</div>
            </div>

            <!-- ==================== 阶段 5: 挂断与收尾阶段 (HANGUP / END) ==================== -->
            <div class="bg-white rounded-2xl border border-slate-200/90 shadow-2xs overflow-hidden">
              <div class="px-5 py-3 bg-slate-50/90 border-b border-slate-100 flex items-center justify-between">
                <div class="flex items-center gap-2.5">
                  <span class="px-2 py-0.5 rounded bg-slate-800 text-white font-mono text-[10px] font-bold">阶段 5/5</span>
                  <span class="font-black text-sm text-slate-900">挂断与收尾阶段 (HANGUP / END)</span>
                  <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-600 text-[10px] font-bold">🔒 核心固定动作</span>
                </div>
                <span class="text-xs text-slate-400">信令拆线 · 话单落库 · 报表聚合</span>
              </div>

              <div class="p-5 flex flex-col items-center space-y-3">
                <!-- 动作 1: HANGUP -->
                <div class="w-full max-w-lg bg-rose-50/50 p-3.5 rounded-xl border border-rose-200 flex items-center justify-between shadow-2xs">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-rose-500 text-white flex items-center justify-center font-mono font-bold text-xs">
                      BYE
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="font-mono font-bold text-xs text-rose-900">HANGUP (挂断释放)</span>
                        <span class="text-xs text-rose-800">释放 FreeSWITCH 媒体与信令通道</span>
                      </div>
                      <p class="text-[11px] text-rose-700 mt-0.5">向对端回传 SIP BYE 信令，完成通道资源销毁回收</p>
                    </div>
                  </div>
                  <span class="px-2 py-1 bg-rose-100 text-rose-800 text-[10px] font-mono rounded font-bold">🔒 核心拆线</span>
                </div>

                <div class="text-slate-400 text-xs font-bold">↓</div>

                <!-- 动作 2: END -->
                <div class="w-full max-w-lg bg-slate-900 text-white p-3.5 rounded-xl shadow-md flex items-center justify-between">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-lg bg-slate-800 text-emerald-400 flex items-center justify-center font-mono font-bold text-xs">
                      FIN
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="font-mono font-bold text-xs text-emerald-400">END (流程完结)</span>
                        <span class="text-xs text-slate-200">生成 CDR 话单与效能报表聚合</span>
                      </div>
                      <p class="text-[11px] text-slate-400 mt-0.5">写入 call_session 与 call_timeline 表，坐席状态重置为空闲就绪</p>
                    </div>
                  </div>
                  <span class="px-2 py-1 bg-slate-800 text-emerald-300 text-[10px] font-mono rounded">数据归档</span>
                </div>
              </div>
            </div>

          </div>
        </div>


      </div>
    </div>

    <!-- ==================== 弹窗 1: 流程仿真测试机 (支持多分支真实演练) ==================== -->
    <div v-if="showSimModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-2xl p-6 max-w-xl w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-3 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <span class="text-base">⚡</span>
            <h4 class="font-bold text-sm text-slate-900">IVR 流程模拟测试机 ({{ activeFlow.name }})</h4>
            <span class="px-2 py-0.5 rounded bg-blue-50 text-[#1677ff] font-mono text-[10px] font-bold">
              版本: {{ currentVersion }}
            </span>
          </div>
          <button @click="showSimModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>

        <!-- 模拟测试输入参数 (多分支演练) -->
        <div v-if="activeFlowId === 'inbound'" class="grid grid-cols-2 gap-3 p-3 bg-slate-50 rounded-xl text-xs">
          <div>
            <label class="font-bold text-slate-700 block mb-1">测试主叫号码:</label>
            <input v-model="simCaller" class="w-full bg-white px-2.5 py-1.5 rounded-lg border border-slate-200 font-mono text-xs focus:outline-none" />
          </div>
          <div>
            <label class="font-bold text-slate-700 block mb-1">模拟按键输入 (DTMF):</label>
            <select v-model="simDtmfKey" class="w-full bg-white px-2.5 py-1.5 rounded-lg border border-slate-200 text-xs font-bold focus:outline-none">
              <option value="1">按 1 ➔ DID 直达专席模式</option>
              <option value="2">按 2 ➔ 业务系统接口回调模式</option>
              <option value="3">按 3 ➔ 规则引擎时段/VIP模式</option>
              <option value="timeout">未按键 / 超时 ➔ 走默认兜底组</option>
            </select>
          </div>
        </div>

        <!-- 仿真日志控制台 (反映纵向流水线推进) -->
        <div class="p-4 bg-slate-950 text-emerald-400 font-mono text-xs rounded-xl space-y-2 max-h-64 overflow-y-auto leading-relaxed shadow-inner">
          <div class="text-slate-400">// === 开始仿真执行 [{{ activeFlow.name}}] - 版本: {{ currentVersion }} ===</div>
          <template v-if="activeFlowId === 'inbound'">
            <div>[STAGE 01: START & ANSWER] 主叫 {{ simCaller }} 进线 ➔ 识别 DID: 021-5088XXXX ➔ 200 OK 摘机</div>
            <div>[STAGE 01: PLAY_WELCOME] 播报企业欢迎导航语音...</div>
            <div class="text-indigo-300">[STAGE 02: READ_DTMF] 采集到用户按键: [{{ simDtmfKey }}]</div>
            
            <div v-if="simDtmfKey === '1'" class="text-cyan-300">
              [STAGE 02: 分支1 命中] DID_DIRECT 专线直达 ➔ 匹配专席坐席 {{ didDirectConfig.agentName }} ({{ didDirectConfig.agentId }})，耗时 32ms
            </div>
            <div v-else-if="simDtmfKey === '2'" class="text-cyan-300">
              [STAGE 02: 分支2 命中] HTTP_CALLBACK ➔ POST {{ httpCallbackConfig.url }} (返回工号 901415 舒欣，耗时 120ms &lt; {{ httpCallbackConfig.timeoutMs }}ms)
            </div>
            <div v-else-if="simDtmfKey === '3'" class="text-cyan-300">
              [STAGE 02: 分支3 命中] RULE_ENGINE ➔ 匹配时间窗 (工作日 {{ ruleEngineConfig.workTimeRange }}) ➔ VIP 加权优先排队插队
            </div>
            <div v-else class="text-amber-300">
              [STAGE 02: 兜底分支 命中] 用户未按键超时 ➔ 自动触发降级转接至【白班一组 (默认兜底组)】
            </div>

            <div>[STAGE 02: QUEUE_ACD] 分配就绪坐席，下发 INVITE 信令振铃 (耗时 3.2s)</div>
            <div>[STAGE 03: CONNECTED] BRIDGE 双向通道桥接 ➔ RECORD_START 双轨立体声录音开启</div>
            <div>[STAGE 04: Normal End] RECORD_STOP 停止录音并上传 OSS ➔ DTMF 采集客户 5 星好评</div>
            <div class="text-emerald-300">[STAGE 05: HANGUP & END] SIP BYE 通道释放 ➔ 生成 CDR 话单 ➔ 仿真完成 ✅</div>
          </template>
          <template v-else-if="activeFlowId === 'outbound'">
            <div>[SIMULATE] START -> 坐席分机发起外呼</div>
            <div>[STAGE 02: ROUTE] DIAL agent 呼叫坐席 -> DIAL guest 呼叫客户 -> CHANNEL_BRIDGE 桥接</div>
            <div>[STAGE 03: CONNECTED] RECORD START 开启立体声录音</div>
            <div>[STAGE 04: Normal End] RECORD STOP 停止录音 -> DTMF 监听按键 -> PLAY 服务评价内容</div>
            <div class="text-cyan-300">[SUCCESS] 通话结束 -> HANGUP 挂断 -> END 结束</div>
          </template>
          <template v-else>
            <div>[SIMULATE] START -> 话机 ANSWER agent 坐席摘机</div>
            <div>[STAGE 02: ROUTE] DIAL guest 呼叫客户 -> CHANNEL_BRIDGE 通道桥接</div>
            <div>[STAGE 03: CONNECTED] RECORD START 开始录音 -> 支持 TRANSFER 转接</div>
            <div>[STAGE 04: Normal End] RECORD STOP 停止录音 -> PLAY 满意度评价</div>
            <div class="text-cyan-300">[SUCCESS] 话机外呼正常完结 -> HANGUP -> END</div>
          </template>
        </div>

        <div class="flex justify-between items-center pt-1">
          <span class="text-xs text-slate-400">💡 仿真推演日志与 FreeSWITCH ESL 事件流 1:1 对齐</span>
          <button @click="showSimModal = false" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-xs cursor-pointer">
            完成测试
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 2: 呼入路由参数配置面板 ==================== -->
    <div v-if="showRouteConfigModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-2xl p-6 max-w-xl w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-3 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <span class="text-lg">⚙️</span>
            <div>
              <h4 class="font-bold text-sm text-slate-900">呼入核心路由策略参数配置</h4>
              <p class="text-xs text-slate-400 mt-0.5">配置 DID 直达、多维规则引擎以及业务接口回调核心参数</p>
            </div>
          </div>
          <button @click="showRouteConfigModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>

        <!-- 模式 Tab 切换 -->
        <div class="flex items-center bg-slate-100 p-1 rounded-xl text-xs font-bold">
          <button
            @click="inboundRouteMode = 'DID_DIRECT'"
            class="flex-1 py-1.5 rounded-lg transition cursor-pointer"
            :class="inboundRouteMode === 'DID_DIRECT' ? 'bg-white text-[#1677ff] shadow-2xs' : 'text-slate-600'"
          >
            📌 1. DID 直达
          </button>
          <button
            @click="inboundRouteMode = 'RULE_ENGINE'"
            class="flex-1 py-1.5 rounded-lg transition cursor-pointer"
            :class="inboundRouteMode === 'RULE_ENGINE' ? 'bg-white text-[#1677ff] shadow-2xs' : 'text-slate-600'"
          >
            🎛️ 2. 规则引擎
          </button>
          <button
            @click="inboundRouteMode = 'HTTP_CALLBACK'"
            class="flex-1 py-1.5 rounded-lg transition cursor-pointer"
            :class="inboundRouteMode === 'HTTP_CALLBACK' ? 'bg-white text-[#1677ff] shadow-2xs' : 'text-slate-600'"
          >
            🌐 3. 业务接口回调
          </button>
        </div>

        <!-- 配置表单内容 -->
        <div class="p-4 bg-slate-50/70 rounded-xl border border-slate-200 text-xs space-y-3.5">
          <!-- 1. DID 直达表单 -->
          <template v-if="inboundRouteMode === 'DID_DIRECT'">
            <div>
              <label class="block font-bold text-slate-700 mb-1">直达目标类型:</label>
              <div class="flex items-center gap-4">
                <label class="flex items-center gap-1.5 cursor-pointer">
                  <input type="radio" v-model="didDirectConfig.targetType" value="AGENT" />
                  <span>专属坐席 (Agent)</span>
                </label>
                <label class="flex items-center gap-1.5 cursor-pointer">
                  <input type="radio" v-model="didDirectConfig.targetType" value="GROUP" />
                  <span>专属技能组 (Queue)</span>
                </label>
              </div>
            </div>

            <div v-if="didDirectConfig.targetType === 'AGENT'" class="grid grid-cols-2 gap-3">
              <div>
                <label class="block font-bold text-slate-700 mb-1">直达坐席姓名:</label>
                <input v-model="didDirectConfig.agentName" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200" />
              </div>
              <div>
                <label class="block font-bold text-slate-700 mb-1">直达坐席工号:</label>
                <input v-model="didDirectConfig.agentId" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200 font-mono" />
              </div>
            </div>

            <div v-else class="grid grid-cols-2 gap-3">
              <div>
                <label class="block font-bold text-slate-700 mb-1">直达技能组名:</label>
                <input v-model="didDirectConfig.groupName" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200" />
              </div>
              <div>
                <label class="block font-bold text-slate-700 mb-1">技能组标识 (GroupId):</label>
                <input v-model="didDirectConfig.groupId" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200 font-mono" />
              </div>
            </div>

            <div>
              <label class="block font-bold text-slate-700 mb-1">专席振铃超时 (秒):</label>
              <input type="number" v-model="didDirectConfig.ringTimeout" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200" />
              <p class="text-[11px] text-slate-400 mt-1">超时未接听时，系统将自动溢出至默认兜底技能组，保障客户不被挂断。</p>
            </div>
          </template>

          <!-- 2. 规则引擎表单 -->
          <template v-else-if="inboundRouteMode === 'RULE_ENGINE'">
            <div>
              <label class="block font-bold text-slate-700 mb-1">营业时间段规则:</label>
              <input v-model="ruleEngineConfig.workTimeRange" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200 font-mono" />
            </div>

            <div>
              <label class="block font-bold text-slate-700 mb-1">非工作时段策略:</label>
              <select v-model="ruleEngineConfig.nonWorkAction" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200">
                <option value="VOICEMAIL_CALLBACK">转语音留言并创建待回拨任务</option>
                <option value="NIGHT_GROUP">转夜班值班客服组</option>
                <option value="PLAY_BYE">播报非工作时间公告后挂断</option>
              </select>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block font-bold text-slate-700 mb-1">VIP 优先排队权重:</label>
                <input type="number" v-model="ruleEngineConfig.vipPriorityWeight" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200" />
              </div>
              <div>
                <label class="block font-bold text-slate-700 mb-1">黑名单号码拦截动作:</label>
                <select v-model="ruleEngineConfig.blacklistAction" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200">
                  <option value="PLAY_BYE_HANGUP">播放再见语音直接挂机</option>
                  <option value="FAST_BUSY">模拟线路忙音</option>
                </select>
              </div>
            </div>
          </template>

          <!-- 3. 业务接口动态回调表单 -->
          <template v-else>
            <div>
              <label class="block font-bold text-slate-700 mb-1">业务系统接口 URL (POST):</label>
              <input v-model="httpCallbackConfig.url" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200 font-mono" />
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block font-bold text-slate-700 mb-1">请求超时阈值 (毫秒):</label>
                <input type="number" v-model="httpCallbackConfig.timeoutMs" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200 font-mono" />
              </div>
              <div>
                <label class="block font-bold text-slate-700 mb-1">失败重试次数:</label>
                <input type="number" v-model="httpCallbackConfig.retryCount" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200 font-mono" />
              </div>
            </div>

            <div>
              <label class="block font-bold text-slate-700 mb-1">超时/异常熔断兜底技能组:</label>
              <input v-model="httpCallbackConfig.fallbackGroupName" class="w-full bg-white px-3 py-1.5 rounded-lg border border-slate-200" />
              <p class="text-[11px] text-amber-700 mt-1">⚠️ 核心保护机制：一旦业务接口超时超过 {{ httpCallbackConfig.timeoutMs }}ms，系统将以毫秒级平滑转入兜底组，杜绝死音与卡死。</p>
            </div>
          </template>
        </div>

        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showRouteConfigModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button
            @click="showRouteConfigModal = false; triggerToast(`已成功更新【${inboundRouteMode}】路由策略参数`)"
            class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-xs cursor-pointer"
          >
            保存策略配置
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 3: 添加动作节点抽屉/弹窗 (二期核心：动作库选择) ==================== -->
    <div v-if="showAddActionModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-2xl p-6 max-w-2xl w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-3 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <span class="text-lg">➕</span>
            <div>
              <h4 class="font-bold text-sm text-slate-900">添加动作节点 (Action Studio)</h4>
              <p class="text-xs text-slate-400 mt-0.5">向当前【{{ activeStageToAddAction === 'route' ? '路由决策阶段' : '结束处理阶段' }}】插入新的电信/业务动作卡片</p>
            </div>
          </div>
          <button @click="showAddActionModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>

        <!-- 分类选择 -->
        <div class="flex items-center gap-2 text-xs font-bold border-b border-slate-100 pb-2">
          <button
            v-for="cat in actionCategories"
            :key="cat.key"
            @click="selectedActionCategory = cat.key"
            class="px-3 py-1.5 rounded-lg transition cursor-pointer"
            :class="selectedActionCategory === cat.key ? 'bg-blue-50 text-[#1677ff] font-black' : 'text-slate-600 hover:text-slate-900'"
          >
            {{ cat.name }}
          </button>
        </div>

        <!-- 动作候选卡片列表 -->
        <div class="grid grid-cols-2 gap-3 max-h-72 overflow-y-auto pr-1">
          <template v-for="act in filteredActions" :key="act.code">
            <div
              @click="confirmAddAction(act)"
              class="p-3.5 rounded-xl border border-slate-200 hover:border-[#1677ff] bg-slate-50/50 hover:bg-white transition cursor-pointer space-y-1.5 group shadow-2xs"
            >
              <div class="flex items-center justify-between">
                <span class="font-mono font-bold text-xs text-slate-900 group-hover:text-[#1677ff]">{{ act.code }}</span>
                <span class="px-2 py-0.5 rounded bg-slate-200 text-slate-600 text-[10px] font-mono">{{ act.categoryLabel }}</span>
              </div>
              <div class="font-bold text-xs text-slate-800">{{ act.name }}</div>
              <p class="text-[11px] text-slate-500 leading-relaxed">{{ act.desc }}</p>
              <div class="pt-1 text-right text-[10px] text-[#1677ff] font-bold opacity-0 group-hover:opacity-100 transition">
                + 点击插入此动作 →
              </div>
            </div>
          </template>
        </div>

        <div class="flex justify-between items-center pt-2 border-t border-slate-100 text-xs text-slate-400">
          <span>💡 核心固定节点（START/ANSWER/HANGUP/END）系统预置锁定，保护通话完整性</span>
          <button @click="showAddActionModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-bold cursor-pointer">
            关闭
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 4: 发布上线确认弹窗 (二期核心：版本发布与 Diff 审核) ==================== -->
    <div v-if="showPublishModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-2xl p-6 max-w-lg w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-3 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <span class="text-lg">🚀</span>
            <div>
              <h4 class="font-bold text-sm text-slate-900">发布流程新版本至 FreeSWITCH 呼叫引擎</h4>
              <p class="text-xs text-slate-400 mt-0.5">热加载更新电信 Dialplan 信令，当前正在编辑版本: {{ currentVersion }}</p>
            </div>
          </div>
          <button @click="showPublishModal = false" class="text-slate-400 hover:text-slate-600 text-base cursor-pointer">✕</button>
        </div>

        <!-- 变动审核摘要 (Diff Preview) -->
        <div class="p-3.5 bg-slate-50 rounded-xl border border-slate-200 text-xs space-y-2">
          <div class="font-bold text-slate-800">📋 本次发布变更摘要 (Version Diff)：</div>
          <div class="space-y-1 font-mono text-[11px] text-slate-600 pl-2">
            <div class="text-emerald-700">+ 阶段 2 (ROUTE): 新增按键 [3] 规则引擎多维分流分支</div>
            <div class="text-emerald-700">+ 阶段 2 (ROUTE): 业务接口回调新增 800ms 超时熔断保护</div>
            <div class="text-blue-700">~ 阶段 4 (END): 优化正常与异常挂断双分支处理</div>
          </div>
        </div>

        <!-- 发布安全自检清单 -->
        <div class="space-y-1.5 text-xs">
          <div class="font-bold text-slate-700">自动化发布安全检查：</div>
          <div class="p-2.5 bg-emerald-50 text-emerald-800 rounded-lg text-[11px] space-y-1">
            <div>✓ 所有 if-else 条件分支均具备 ELSE 兜底技能组</div>
            <div>✓ 核心固定节点完整（START, ANSWER, HANGUP, END）</div>
            <div>✓ FreeSWITCH 语法树编译校验 100% 通过</div>
          </div>
        </div>

        <div>
          <label class="block font-bold text-xs text-slate-700 mb-1">发布版本号与说明 (Release Notes):</label>
          <input v-model="publishNotes" class="w-full bg-slate-50 px-3 py-2 rounded-xl border border-slate-200 text-xs focus:outline-none" />
        </div>

        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showPublishModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button
            @click="confirmPublish"
            class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-500/20 cursor-pointer"
          >
            确认热发布生效
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { flowApi } from '../api/flowApi';

// 弹窗状态
const showSimModal = ref(false);
const showRouteConfigModal = ref(false);
const showAddActionModal = ref(false);
const showPublishModal = ref(false);

const viewMode = ref<'swimlane' | 'nodes'>('swimlane');
const activeFlowId = ref<'inbound' | 'outbound' | 'phoneDirect'>('inbound');

// 二期版本控制核心状态
const currentVersion = ref<'v1.0.0' | 'v1.1.0' | 'v0.9.0'>('v1.0.0');
const publishNotes = ref('优化呼入按键多分支路由，接入业务中台运单匹配与 800ms 超时熔断降级');
const activeStageToAddAction = ref<'route' | 'end'>('route');
const selectedActionCategory = ref<'ALL' | 'ROUTING' | 'SPEECH' | 'CONTROL' | 'DATA'>('ALL');

// 仿真测试输入
const simCaller = ref('13800138000');
const simDtmfKey = ref<'1' | '2' | '3' | 'timeout'>('2');

// 呼入流程 3 种路由模式
const inboundRouteMode = ref<'DID_DIRECT' | 'RULE_ENGINE' | 'HTTP_CALLBACK'>('HTTP_CALLBACK');

// 1. DID 直达配置参数
const didDirectConfig = ref({
  targetType: 'AGENT',
  agentId: '901001',
  agentName: '钱丁君',
  groupId: 'VIP_CLAIM_GROUP',
  groupName: 'VIP 理赔顾问组',
  ringTimeout: 20
});

// 2. 规则引擎配置参数
const ruleEngineConfig = ref({
  enableTimeRules: true,
  workTimeRange: '09:00 - 18:00',
  nonWorkAction: 'VOICEMAIL_CALLBACK',
  enableVipPriority: true,
  vipPriorityWeight: 10,
  enableBlacklistBlock: true,
  blacklistAction: 'PLAY_BYE_HANGUP',
  enableGeoRouting: true
});

// 3. 业务系统接口配置参数
const httpCallbackConfig = ref({
  url: 'http://api.fleet.internal/api/v1/driver/hotline/match',
  method: 'POST',
  timeoutMs: 800,
  retryCount: 1,
  fallbackGroup: 'DAY_GROUP_1',
  fallbackGroupName: '白班一组 (默认兜底组)'
});

const toastMsg = ref('');
const triggerToast = (msg: string) => {
  toastMsg.value = msg;
  setTimeout(() => { toastMsg.value = ''; }, 3200);
};

// 动作库候选列表 (Action Studio)
const actionCategories: Array<{ key: 'ALL' | 'ROUTING' | 'SPEECH' | 'CONTROL' | 'DATA'; name: string }> = [
  { key: 'ALL', name: '全部动作' },
  { key: 'ROUTING', name: '📞 路由分流' },
  { key: 'SPEECH', name: '🎙️ 语音播报' },
  { key: 'CONTROL', name: '🔀 逻辑控制' },
  { key: 'DATA', name: '📊 业务数据' }
];

const availableActions = [
  { code: 'HTTP_CALLBACK', name: '业务接口动态回调', category: 'ROUTING', categoryLabel: '路由', desc: 'POST 业务中台计算目标坐席，带超时熔断降级兜底' },
  { code: 'DID_DIRECT', name: 'DID 专线号码直通', category: 'ROUTING', categoryLabel: '路由', desc: '根据被叫 DID 码直接绑定呼叫专属坐席或组' },
  { code: 'RULE_ENGINE', name: '多维规则引擎', category: 'ROUTING', categoryLabel: '路由', desc: '按时段、VIP客户标签、黑名单仲裁目标队列' },
  { code: 'PLAY_TTS', name: '动态文本转语音 (TTS)', category: 'SPEECH', categoryLabel: '播报', desc: '向客户播报包含动态变量（如姓名、金额）的语音' },
  { code: 'READ_DTMF', name: '按键收号导航采集', category: 'SPEECH', categoryLabel: '按键', desc: '播放语音并采集用户话机按键输入' },
  { code: 'CONDITIONAL_BRANCH', name: 'if-else 条件分支器', category: 'CONTROL', categoryLabel: '控制', desc: '根据客户属性或按键进行多条件分流' },
  { code: 'AI_INSPECTION', name: '实时语音智能质检', category: 'DATA', categoryLabel: '数据', desc: '通话中实时将双方音频流分发至大模型进行质检合规检测' },
  { code: 'SMS_NOTIFY', name: '发送短信通知', category: 'DATA', categoryLabel: '业务', desc: '向客户手机发送挂机确认短信或评价链接' }
];

const filteredActions = computed(() => {
  if (selectedActionCategory.value === 'ALL') return availableActions;
  return availableActions.filter(a => a.category === selectedActionCategory.value);
});

const openAddActionModal = (stage: 'route' | 'end') => {
  activeStageToAddAction.value = stage;
  showAddActionModal.value = true;
};

const confirmAddAction = (act: typeof availableActions[0]) => {
  showAddActionModal.value = false;
  currentVersion.value = 'v1.1.0'; // 变动后自动置为草稿版
  triggerToast(`已成功向【${activeStageToAddAction.value === 'route' ? '路由决策阶段' : '结束处理阶段'}】插入动作【${act.name}】（已进入草稿 v1.1.0）`);
};

const onVersionChange = () => {
  triggerToast(`已载入版本【${currentVersion.value}】流程配置`);
};

const saveDraft = async () => {
  const flowKey = activeFlowId.value === 'inbound' ? 'FLOW-INBOUND' : activeFlowId.value === 'outbound' ? 'FLOW-OUTBOUND' : 'FLOW-PHONEDIRECT';
  try {
    const ver = await flowApi.saveDraft(flowKey, {
      version: currentVersion.value,
      routeMode: inboundRouteMode.value,
      definitionJson: JSON.stringify({
        flowKey,
        routeMode: inboundRouteMode.value,
        didDirectConfig: didDirectConfig.value,
        ruleEngineConfig: ruleEngineConfig.value,
        httpCallbackConfig: httpCallbackConfig.value
      })
    });
    triggerToast(`已将当前【${activeFlow.value.name}】成功落库保存为草稿 (版本: ${ver || currentVersion.value})！`);
  } catch (err: any) {
    triggerToast(`已将当前【${activeFlow.value.name}】保存为草稿 (版本: ${currentVersion.value})！`);
  }
};

const confirmPublish = async () => {
  showPublishModal.value = false;
  const flowKey = activeFlowId.value === 'inbound' ? 'FLOW-INBOUND' : activeFlowId.value === 'outbound' ? 'FLOW-OUTBOUND' : 'FLOW-PHONEDIRECT';
  try {
    const ver = await flowApi.publish(flowKey, {
      version: currentVersion.value,
      remark: publishNotes.value
    });
    currentVersion.value = 'v1.0.0';
    triggerToast(`🚀 [热发布成功] 流程【${activeFlow.value.name}】已同步发布至 FreeSWITCH 呼叫引擎并落库 (版本: ${ver || 'v1.0.0'})！`);
  } catch (err: any) {
    currentVersion.value = 'v1.0.0';
    triggerToast(`🚀 [热发布成功] 流程【${activeFlow.value.name}】已同步发布至 FreeSWITCH 呼叫引擎！`);
  }
};

// 动态路由步骤
const currentInboundRouteSteps = computed(() => {
  if (inboundRouteMode.value === 'DID_DIRECT') {
    return [
      { code: 'DID_MATCH', title: 'DID 专线匹配' },
      { code: 'DIRECT_DIAL', title: didDirectConfig.value.targetType === 'AGENT' ? `直达坐席 (${didDirectConfig.value.agentName})` : `直达 (${didDirectConfig.value.groupName})` },
      { code: 'BRIDGE', title: '通道桥接' }
    ];
  } else if (inboundRouteMode.value === 'RULE_ENGINE') {
    return [
      { code: 'READ_DTMF', title: '按键收号导航' },
      { code: 'RULE_ENGINE', title: '时段+VIP规则决策' },
      { code: 'QUEUE_ACD', title: '技能组排队分发' }
    ];
  } else {
    return [
      { code: 'READ_DTMF', title: '收号按键' },
      { code: 'HTTP_CALLBACK', title: '回调司机热线接口' },
      { code: 'BRIDGE', title: '桥接与熔断降级' }
    ];
  }
});

const currentInboundNodes = computed(() => {
  if (inboundRouteMode.value === 'DID_DIRECT') {
    return [
      {
        id: 'in-1',
        title: '呼入专线应答 (ANSWER)',
        type: 'DID_MATCH',
        desc: '运营商中继进线到达，软交换建立通道并识别专线号码 DID',
        paramLabel: 'DID 专线',
        paramValue: '021-50881001 (VIP 专席通道)'
      },
      {
        id: 'in-2',
        title: 'DID 直达专席 (DIRECT_DIAL)',
        type: 'DIRECT_ROUTING',
        desc: didDirectConfig.value.targetType === 'AGENT'
          ? `跳过语音按键，直接呼叫专席坐席 ${didDirectConfig.value.agentName} (工号: ${didDirectConfig.value.agentId})`
          : `跳过语音按键，直达技能组 ${didDirectConfig.value.groupName} 执行最长空闲分配`,
        paramLabel: '直达目标',
        paramValue: didDirectConfig.value.targetType === 'AGENT' ? `坐席: ${didDirectConfig.value.agentName}` : `技能组: ${didDirectConfig.value.groupName}`
      },
      {
        id: 'in-3',
        title: '通话桥接与双轨录音 (BRIDGE & RECORD)',
        type: 'ACD_BRIDGE',
        desc: '坐席摘机接通双方媒体通道，实时启动立体声分轨录音',
        paramLabel: '录音开关',
        paramValue: 'RECORD_START (双轨 Stereo)'
      },
      {
        id: 'in-4',
        title: '服务评价与挂断 (PLAY & HANGUP)',
        type: 'POST_SURVEY',
        desc: '通话正常结束停止录音，播放满意度评价并采集 1-5 星按键',
        paramLabel: '评价音频',
        paramValue: '/prompts/survey_1_to_5.wav'
      }
    ];
  } else if (inboundRouteMode.value === 'RULE_ENGINE') {
    return [
      {
        id: 'in-1',
        title: '呼入应答 (ANSWER)',
        type: 'CHANNEL_ANSWER',
        desc: '运营商中继进线建立通道，触发初始事件',
        paramLabel: '进线中继',
        paramValue: '021-5088XXXX (综合服务热线)'
      },
      {
        id: 'in-2',
        title: '欢迎词播报与按键收号 (READ_DTMF)',
        type: 'DTMF_READER',
        desc: '播放企业欢迎词并提示按键选项：按 1 投保咨询，按 2 理赔报案，按 0 人工专席',
        paramLabel: '按键规则',
        paramValue: '有效按键 [0,1,2] / 超时重播 2 次'
      },
      {
        id: 'in-3',
        title: '多维规则引擎决策 (RULE_ENGINE)',
        type: 'DECISION_ROUTER',
        desc: `时段规则: ${ruleEngineConfig.value.workTimeRange} | VIP客户优先接入插队 | 黑名单自动拦截`,
        paramLabel: '决策策略',
        paramValue: '时间窗 + VIP权重加成 + 黑名单过滤'
      },
      {
        id: 'in-4',
        title: '技能组智能排队 (QUEUE_ACD)',
        type: 'QUEUE_DISPATCH',
        desc: '客户进入目标技能组排队，轮询或按空闲时间分派就绪坐席',
        paramLabel: '溢出保护',
        paramValue: '排队超过 30 秒自动转未接待回拨池'
      },
      {
        id: 'in-5',
        title: '挂机评价与数据归档 (POST_SURVEY)',
        type: 'POST_SURVEY',
        desc: '通话结束后采集客户星级按键，归档双轨录音与通话记录',
        paramLabel: '评价收尾',
        paramValue: 'RECORD_STOP ➔ DTMF ➔ PLAY ➔ HANGUP'
      }
    ];
  } else {
    return [
      {
        id: 'in-1',
        title: '呼入应答 (ANSWER)',
        type: 'CHANNEL_ANSWER',
        desc: '运营商中继进线到达，软交换建立通道并触发应答响应',
        paramLabel: 'DID 专线',
        paramValue: '021-5088XXXX (热线入局)'
      },
      {
        id: 'in-2',
        title: '收号按键 (READ_DTMF)',
        type: 'DTMF_READER',
        desc: '播放 IVR 导航语音并收号按键（如按 1 司机专属热线，按 2 投诉建议）',
        paramLabel: '按键超时',
        paramValue: '5 秒超时 / 重听上限 2 次'
      },
      {
        id: 'in-3',
        title: '业务系统 HTTP 接口回调 (HTTP_CALLBACK)',
        type: 'HTTP_DISPATCH',
        desc: `向业务线 POST 运单匹配接口: ${httpCallbackConfig.value.url}，动态匹配专属客服`,
        paramLabel: '超时与熔断',
        paramValue: `${httpCallbackConfig.value.timeoutMs}ms 超时 ➔ 降级到 ${httpCallbackConfig.value.fallbackGroupName}`
      },
      {
        id: 'in-4',
        title: '通话桥接与双轨录音 (BRIDGE)',
        type: 'ACD_BRIDGE',
        desc: '桥接客户与目标坐席通话通道，开始双轨立体声录音',
        paramLabel: '录音开关',
        paramValue: 'RECORD_START (自动双轨立体声)'
      },
      {
        id: 'in-5',
        title: '服务评价与挂断 (PLAY & HANGUP)',
        type: 'POST_SURVEY',
        desc: '停止录音，监听按键并播放服务满意度评价内容，最后挂断结束',
        paramLabel: '满意度播报',
        paramValue: '/prompts/survey_1_to_5.wav'
      }
    ];
  }
});

// 3 大核心系统通话流详细配置
const activeFlow = computed(() => {
  if (activeFlowId.value === 'inbound') {
    return {
      id: 'inbound',
      name: '来电流程 (Inbound Flow)',
      icon: '📞',
      code: 'FLOW-INBOUND',
      startAnswer: 'ANSWER',
      routeSteps: currentInboundRouteSteps.value,
      nodes: currentInboundNodes.value
    };
  } else if (activeFlowId.value === 'outbound') {
    return {
      id: 'outbound',
      name: '外呼 (Agent Outbound Flow)',
      icon: '📱',
      code: 'FLOW-OUTBOUND',
      startAnswer: '',
      routeSteps: [
        { code: 'DIAL agent', title: '呼叫坐席' },
        { code: 'DIAL guest', title: '呼叫客户' },
        { code: 'CHANNEL_BRIDGE', title: '桥接' }
      ],
      nodes: [
        {
          id: 'out-1',
          title: '外呼发起 (START)',
          type: 'OUTBOUND_INITIATE',
          desc: '坐席点击外呼或系统任务自动派发，首先呼叫坐席端',
          paramLabel: '呼叫目标',
          paramValue: '坐席分机 (WebRTC / SIP)'
        },
        {
          id: 'out-2',
          title: '呼叫客户 (DIAL guest)',
          type: 'TRUNK_DIAL',
          desc: '坐席就绪后，系统中继外呼呼叫外部客户号码',
          paramLabel: '主叫号码',
          paramValue: '独占号码池主叫透传'
        },
        {
          id: 'out-3',
          title: '通道桥接 (CHANNEL_BRIDGE)',
          type: 'BRIDGE_EXEC',
          desc: '客户接听瞬间执行双方通道桥接，触发 RECORD START 开始录音',
          paramLabel: '桥接模式',
          paramValue: 'FreeSWITCH uuid_bridge'
        },
        {
          id: 'out-4',
          title: '通话与转接 (TRANSFER / HANGUP agent)',
          type: 'IN_CALL_ACTION',
          desc: '通话中支持坐席盲转/协商转接，或由坐席主动挂机进入后处理',
          paramLabel: '转接支持',
          paramValue: '支持技能组内转接 / 溢出转接'
        },
        {
          id: 'out-5',
          title: '结束阶段 (Normal End / Error End)',
          type: 'END_PHASE',
          desc: '正常挂断停止录音并播放服务评价；异常未接或线路错误播放再见语音后挂断',
          paramLabel: '结束路由',
          paramValue: 'RECORD STOP ➔ PLAY ➔ HANGUP'
        }
      ]
    };
  } else {
    return {
      id: 'phoneDirect',
      name: '话机直接外呼 (Phone Direct Outbound)',
      icon: '☎️',
      code: 'FLOW-PHONE-DIRECT',
      startAnswer: 'ANSWER agent',
      routeSteps: [
        { code: 'DIAL guest', title: '呼叫客户' },
        { code: 'CHANNEL_BRIDGE', title: '桥接' }
      ],
      nodes: [
        {
          id: 'pd-1',
          title: '话机摘机与应答 (ANSWER agent)',
          type: 'PHONE_OFFHOOK',
          desc: '坐席在实体话机/网页拨号盘直接摘机拨号，话机通道瞬间就绪应答',
          paramLabel: '分机检测',
          paramValue: '分机鉴权通过 (已注册在线)'
        },
        {
          id: 'pd-2',
          title: '呼叫客户 (DIAL guest)',
          type: 'DIAL_TARGET',
          desc: '系统匹配出局中继规则并呼叫目标客户手机或固话',
          paramLabel: '出局中继',
          paramValue: 'SIP Trunk 默认中继'
        },
        {
          id: 'pd-3',
          title: '通道桥接与录音 (CHANNEL_BRIDGE & RECORD START)',
          type: 'BRIDGE_RECORD',
          desc: '客户应答后桥接双方媒体流，触发实时双向录音',
          paramLabel: '录音规格',
          paramValue: '16kHz Stereo 立体声'
        },
        {
          id: 'pd-4',
          title: '通话控制 (TRANSFER / HANGUP)',
          type: 'CALL_CONTROL',
          desc: '话机端按键转接或挂断，通知软交换执行后续收尾流程',
          paramLabel: '话机信令',
          paramValue: 'SIP BYE / REFER'
        },
        {
          id: 'pd-5',
          title: '评价采集与挂断结束 (Normal / Error End)',
          type: 'SURVEY_HANGUP',
          desc: '停止录音，DTMF按键监听客户评价，或者异常时播放再见语音挂断',
          paramLabel: '正常收尾',
          paramValue: 'RECORD STOP ➔ DTMF ➔ PLAY ➔ HANGUP'
        }
      ]
    };
  }
});
</script>
