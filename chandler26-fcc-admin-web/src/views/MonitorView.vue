<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { cdrApi, type CallCdrVO } from "../api/cdrApi";
import {
  ChevronLeft,
  ChevronRight,
  RefreshCw,
} from "lucide-vue-next";

type PeriodType = "day" | "week" | "month";

const periodType = ref<PeriodType>("day");
const currentDate = ref<Date>(new Date());
const selectedType = ref<"all" | "inbound" | "outbound" | "auto" | "internal">("all");
const refreshTime = ref("刚刚");
const isLoading = ref(false);
const rawCdrs = ref<CallCdrVO[]>([]);
const hoveredIndex = ref<number | null>(null);

const formatLocalYmd = (d: Date): string => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
};

const dateRange = computed(() => {
  const d = currentDate.value instanceof Date ? currentDate.value : new Date(currentDate.value);
  if (periodType.value === "day") {
    const ymd = formatLocalYmd(d);
    return {
      startTime: `${ymd}T00:00:00`,
      endTime: `${ymd}T23:59:59`,
      label: ymd,
      monday: null as Date | null,
      year: d.getFullYear(),
      month: d.getMonth(),
      totalDays: 1,
    };
  } else if (periodType.value === "week") {
    const dayOffset = (d.getDay() + 6) % 7;
    const mon = new Date(d.getFullYear(), d.getMonth(), d.getDate() - dayOffset);
    const sun = new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + 6);
    const startYmd = formatLocalYmd(mon);
    const endYmd = formatLocalYmd(sun);
    return {
      startTime: `${startYmd}T00:00:00`,
      endTime: `${endYmd}T23:59:59`,
      label: `${startYmd} ~ ${endYmd}`,
      monday: mon,
      year: d.getFullYear(),
      month: d.getMonth(),
      totalDays: 7,
    };
  } else {
    const y = d.getFullYear();
    const m = d.getMonth();
    const firstDay = new Date(y, m, 1);
    const lastDay = new Date(y, m + 1, 0);
    const startYmd = formatLocalYmd(firstDay);
    const endYmd = formatLocalYmd(lastDay);
    return {
      startTime: `${startYmd}T00:00:00`,
      endTime: `${endYmd}T23:59:59`,
      label: `${startYmd} ~ ${endYmd}`,
      monday: null as Date | null,
      year: y,
      month: m,
      totalDays: lastDay.getDate(),
    };
  }
});

const formatDuration = (ms: number): string => {
  if (!ms || ms <= 0) return "0秒";
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}时${minutes}分${seconds}秒`;
  if (minutes > 0) return `${minutes}分${seconds}秒`;
  return `${seconds}秒`;
};

// 呼出统计
const outboundList = computed(() =>
  rawCdrs.value.filter((c) => c.direction === "OUTBOUND"),
);
const outboundTotal = computed(() => outboundList.value.length);
const outboundConnected = computed(
  () =>
    outboundList.value.filter(
      (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
    ).length,
);
const outboundRate = computed(() =>
  outboundTotal.value
    ? ((outboundConnected.value / outboundTotal.value) * 100).toFixed(2) + "%"
    : "0.00%",
);
const outboundDuration = computed(() =>
  formatDuration(
    outboundList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0),
  ),
);

// 呼入统计
const inboundList = computed(() =>
  rawCdrs.value.filter((c) => c.direction === "INBOUND"),
);
const inboundTotal = computed(() => inboundList.value.length);
const inboundConnected = computed(
  () =>
    inboundList.value.filter(
      (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
    ).length,
);
const inboundRate = computed(() =>
  inboundTotal.value
    ? ((inboundConnected.value / inboundTotal.value) * 100).toFixed(2) + "%"
    : "0.00%",
);
const inboundDuration = computed(() =>
  formatDuration(
    inboundList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0),
  ),
);

// 智能外呼统计
const autoList = computed(() =>
  rawCdrs.value.filter((c) => c.modelType === "CAMPAIGN"),
);
const autoTotal = computed(() => autoList.value.length);
const autoConnected = computed(
  () =>
    autoList.value.filter(
      (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
    ).length,
);
const autoRate = computed(() =>
  autoTotal.value
    ? ((autoConnected.value / autoTotal.value) * 100).toFixed(2) + "%"
    : "0.00%",
);
const autoDuration = computed(() =>
  formatDuration(
    autoList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0),
  ),
);

// 内线呼叫统计
const internalList = computed(() =>
  rawCdrs.value.filter((c) => c.direction === "INTERNAL"),
);
const internalTotal = computed(() => internalList.value.length);
const internalConnected = computed(
  () =>
    internalList.value.filter(
      (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
    ).length,
);
const internalRate = computed(() =>
  internalTotal.value
    ? ((internalConnected.value / internalTotal.value) * 100).toFixed(2) + "%"
    : "0.00%",
);
const internalDuration = computed(() =>
  formatDuration(
    internalList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0),
  ),
);

// 趋势图根据选择类型过滤的数据集
const trendFilteredCdrs = computed(() => {
  if (selectedType.value === "all") return rawCdrs.value;
  if (selectedType.value === "inbound") return inboundList.value;
  if (selectedType.value === "outbound") return outboundList.value;
  if (selectedType.value === "auto") return autoList.value;
  if (selectedType.value === "internal") return internalList.value;
  return rawCdrs.value;
});

// 当前趋势过滤条件下的汇总指标
const currentTrendStats = computed(() => {
  const total = trendFilteredCdrs.value.length;
  const connected = trendFilteredCdrs.value.filter(
    (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
  ).length;
  const rate = total > 0 ? ((connected / total) * 100).toFixed(2) + "%" : "0.00%";
  return {
    total: `${total}通`,
    connected: `${connected}通`,
    count: total,
    connCount: connected,
    rate,
  };
});

// 构建趋势时间桶 (按日: 24小时; 按周: 7天; 按月: 当月各天)
interface TrendBucket {
  key: string;
  title: string;
  axisLabel: string;
  showTick: boolean;
  total: number;
  connected: number;
  rate: string;
  x: number;
  totalY: number;
  connY: number;
}

const X_START = 50;
const X_END = 770;
const Y_TOP = 25;
const Y_BOTTOM = 160;

const scaleCeil = computed(() => {
  let max = 0;
  if (periodType.value === "day") {
    const counts = new Array(24).fill(0);
    trendFilteredCdrs.value.forEach((c) => {
      const raw = c.initiatedAt || "";
      const t = new Date(raw.replace(" ", "T"));
      if (!isNaN(t.getTime())) {
        const h = t.getHours();
        if (h >= 0 && h < 24) counts[h]++;
      }
    });
    max = Math.max(0, ...counts);
  } else if (periodType.value === "week") {
    const counts = new Array(7).fill(0);
    const mon = dateRange.value.monday || new Date();
    trendFilteredCdrs.value.forEach((c) => {
      const raw = c.initiatedAt || "";
      const t = new Date(raw.replace(" ", "T"));
      if (!isNaN(t.getTime())) {
        const dayDiff = Math.floor((t.getTime() - mon.getTime()) / (24 * 3600 * 1000));
        if (dayDiff >= 0 && dayDiff < 7) counts[dayDiff]++;
      }
    });
    max = Math.max(0, ...counts);
  } else {
    const totalDays = dateRange.value.totalDays || 30;
    const counts = new Array(totalDays).fill(0);
    const y = dateRange.value.year;
    const m = dateRange.value.month;
    trendFilteredCdrs.value.forEach((c) => {
      const raw = c.initiatedAt || "";
      const t = new Date(raw.replace(" ", "T"));
      if (!isNaN(t.getTime()) && t.getFullYear() === y && t.getMonth() === m) {
        const d = t.getDate();
        if (d >= 1 && d <= totalDays) counts[d - 1]++;
      }
    });
    max = Math.max(0, ...counts);
  }

  if (max <= 5) return 5;
  if (max <= 10) return 10;
  if (max <= 20) return 20;
  if (max <= 50) return 50;
  if (max <= 100) return 100;
  return Math.ceil(max / 50) * 50;
});

const trendBuckets = computed<TrendBucket[]>(() => {
  const ceil = scaleCeil.value;
  const list = trendFilteredCdrs.value;

  if (periodType.value === "day") {
    const buckets: TrendBucket[] = [];
    const n = 24;
    for (let h = 0; h < n; h++) {
      const x = X_START + (h / (n - 1)) * (X_END - X_START);
      const hourStr = String(h).padStart(2, "0");
      const nextHourStr = String((h + 1) % 24).padStart(2, "0");

      const inHour = list.filter((c) => {
        const raw = c.initiatedAt || "";
        const t = new Date(raw.replace(" ", "T"));
        return !isNaN(t.getTime()) && t.getHours() === h;
      });
      const total = inHour.length;
      const connected = inHour.filter(
        (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
      ).length;
      const rate = total > 0 ? ((connected / total) * 100).toFixed(1) + "%" : "0%";

      const totalY = Y_BOTTOM - (total / ceil) * (Y_BOTTOM - Y_TOP);
      const connY = Y_BOTTOM - (connected / ceil) * (Y_BOTTOM - Y_TOP);

      buckets.push({
        key: `hour-${h}`,
        title: `${dateRange.value.label} ${hourStr}:00 ~ ${nextHourStr}:00`,
        axisLabel: `${hourStr}:00`,
        showTick: h === 0 || h === 4 || h === 8 || h === 12 || h === 16 || h === 20 || h === 23,
        total,
        connected,
        rate,
        x,
        totalY,
        connY,
      });
    }
    return buckets;
  } else if (periodType.value === "week") {
    const buckets: TrendBucket[] = [];
    const mon = dateRange.value.monday || new Date();
    const weekNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
    const n = 7;
    for (let i = 0; i < n; i++) {
      const bDate = new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + i);
      const ymd = formatLocalYmd(bDate);
      const x = X_START + (i / (n - 1)) * (X_END - X_START);

      const inDay = list.filter((c) => {
        const raw = c.initiatedAt || "";
        const t = new Date(raw.replace(" ", "T"));
        return !isNaN(t.getTime()) && formatLocalYmd(t) === ymd;
      });
      const total = inDay.length;
      const connected = inDay.filter(
        (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
      ).length;
      const rate = total > 0 ? ((connected / total) * 100).toFixed(1) + "%" : "0%";

      const totalY = Y_BOTTOM - (total / ceil) * (Y_BOTTOM - Y_TOP);
      const connY = Y_BOTTOM - (connected / ceil) * (Y_BOTTOM - Y_TOP);

      buckets.push({
        key: `day-${i}`,
        title: `${weekNames[i]} (${ymd})`,
        axisLabel: `${weekNames[i]} ${bDate.getMonth() + 1}/${bDate.getDate()}`,
        showTick: true,
        total,
        connected,
        rate,
        x,
        totalY,
        connY,
      });
    }
    return buckets;
  } else {
    const buckets: TrendBucket[] = [];
    const totalDays = dateRange.value.totalDays || 30;
    const y = dateRange.value.year;
    const m = dateRange.value.month;
    const n = totalDays;

    for (let d = 1; d <= totalDays; d++) {
      const x = X_START + ((d - 1) / (n - 1)) * (X_END - X_START);
      const dateStr = `${y}-${String(m + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

      const inDay = list.filter((c) => {
        const raw = c.initiatedAt || "";
        const t = new Date(raw.replace(" ", "T"));
        return (
          !isNaN(t.getTime()) &&
          t.getFullYear() === y &&
          t.getMonth() === m &&
          t.getDate() === d
        );
      });
      const total = inDay.length;
      const connected = inDay.filter(
        (c) => c.status === "ANSWERED" || c.status === "NORMAL_END",
      ).length;
      const rate = total > 0 ? ((connected / total) * 100).toFixed(1) + "%" : "0%";

      const totalY = Y_BOTTOM - (total / ceil) * (Y_BOTTOM - Y_TOP);
      const connY = Y_BOTTOM - (connected / ceil) * (Y_BOTTOM - Y_TOP);

      buckets.push({
        key: `month-day-${d}`,
        title: `${dateStr}`,
        axisLabel: `${d}日`,
        showTick: d === 1 || d === 5 || d === 10 || d === 15 || d === 20 || d === 25 || d === totalDays,
        total,
        connected,
        rate,
        x,
        totalY,
        connY,
      });
    }
    return buckets;
  }
});

// 平滑贝塞尔曲线生成
const buildSmoothPath = (pts: { x: number; y: number }[]): string => {
  if (pts.length === 0) return "";
  if (pts.length === 1) return `M ${pts[0].x},${pts[0].y}`;
  let d = `M ${pts[0].x},${pts[0].y}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i];
    const p1 = pts[i + 1];
    const dx = (p1.x - p0.x) / 2;
    const cp1x = p0.x + dx * 0.6;
    const cp1y = p0.y;
    const cp2x = p1.x - dx * 0.6;
    const cp2y = p1.y;
    d += ` C ${cp1x.toFixed(1)},${cp1y.toFixed(1)} ${cp2x.toFixed(1)},${cp2y.toFixed(1)} ${p1.x.toFixed(1)},${p1.y.toFixed(1)}`;
  }
  return d;
};

// 渐变面积闭合路径生成
const buildAreaPath = (pts: { x: number; y: number }[], baseY: number): string => {
  if (pts.length === 0) return "";
  const linePath = buildSmoothPath(pts);
  const first = pts[0];
  const last = pts[pts.length - 1];
  return `${linePath} L ${last.x},${baseY} L ${first.x},${baseY} Z`;
};

// 总量走势路径与面积
const totalLinePath = computed(() => {
  const pts = trendBuckets.value.map((b) => ({ x: b.x, y: b.totalY }));
  return buildSmoothPath(pts);
});

const totalAreaPath = computed(() => {
  const pts = trendBuckets.value.map((b) => ({ x: b.x, y: b.totalY }));
  return buildAreaPath(pts, Y_BOTTOM);
});

// 接通量走势路径与面积
const connLinePath = computed(() => {
  const pts = trendBuckets.value.map((b) => ({ x: b.x, y: b.connY }));
  return buildSmoothPath(pts);
});

const connAreaPath = computed(() => {
  const pts = trendBuckets.value.map((b) => ({ x: b.x, y: b.connY }));
  return buildAreaPath(pts, Y_BOTTOM);
});

// 鼠标悬停交互计算
const handleSvgMouseMove = (e: MouseEvent) => {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const offsetX = e.clientX - rect.left;
  const ratio = offsetX / rect.width;
  const svgX = ratio * 800;

  const buckets = trendBuckets.value;
  if (!buckets || buckets.length < 2) return;

  const n = buckets.length;
  const idx = Math.min(
    Math.max(0, Math.round(((svgX - X_START) / (X_END - X_START)) * (n - 1))),
    n - 1,
  );
  hoveredIndex.value = idx;
};

const handleSvgMouseLeave = () => {
  hoveredIndex.value = null;
};

const hoveredBucket = computed<TrendBucket | null>(() => {
  if (hoveredIndex.value === null) return null;
  return trendBuckets.value[hoveredIndex.value] || null;
});

// 周期与时间切换
const setPeriodType = (type: PeriodType) => {
  if (periodType.value === type) return;
  periodType.value = type;
  hoveredIndex.value = null;
  loadData();
};

const shiftDate = (step: number) => {
  const d = new Date(currentDate.value);
  if (periodType.value === "day") {
    d.setDate(d.getDate() + step);
  } else if (periodType.value === "week") {
    d.setDate(d.getDate() + step * 7);
  } else if (periodType.value === "month") {
    d.setMonth(d.getMonth() + step);
  }
  currentDate.value = d;
  hoveredIndex.value = null;
  loadData();
};

const resetToCurrentPeriod = () => {
  currentDate.value = new Date();
  hoveredIndex.value = null;
  loadData();
};

const handleDateChange = () => {
  hoveredIndex.value = null;
  loadData();
};

const loadData = async () => {
  isLoading.value = true;
  try {
    const res = await cdrApi.list({
      pageNum: 1,
      pageSize: 2000,
      startTime: dateRange.value.startTime,
      endTime: dateRange.value.endTime,
    });
    rawCdrs.value = res?.list || [];
    refreshTime.value = new Date().toLocaleTimeString();
  } catch (err) {
    console.error("加载监控大盘真实数据失败:", err);
  } finally {
    isLoading.value = false;
  }
};

const handleRefresh = () => {
  loadData();
};

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="flex-1 flex flex-col gap-6 overflow-y-auto pr-1">
    <!-- 全局通话信息卡片 -->
    <div class="bg-white rounded-3xl border border-slate-100 shadow-card p-6">
      <div
        class="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-100 mb-4"
      >
        <!-- 左侧：标题 + 日/周/月切换 + 饱满清晰的时间导航胶囊 -->
        <div class="flex flex-wrap items-center gap-3.5">
          <h3 class="font-black text-lg text-slate-900 tracking-tight shrink-0">
            全局通话信息总览
          </h3>

          <div class="h-5 w-px bg-slate-200 shrink-0 mx-1"></div>

          <!-- 日 / 周 / 月 维度切换胶囊 -->
          <div
            class="flex items-center bg-slate-100 p-1 rounded-xl border border-slate-200/80 shadow-2xs h-10 shrink-0"
          >
            <button
              v-for="item in [
                { key: 'day', label: '日' },
                { key: 'week', label: '周' },
                { key: 'month', label: '月' },
              ]"
              :key="item.key"
              @click="setPeriodType(item.key as PeriodType)"
              class="px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer"
              :class="
                periodType === item.key
                  ? 'bg-white text-brand-600 shadow-xs font-black'
                  : 'text-slate-500 hover:text-slate-900'
              "
            >
              {{ item.label }}
            </button>
          </div>

          <!-- 一体化时间选择器胶囊 (大字体、紧凑贴合、消除内部过宽留白) -->
          <div
            class="flex items-center bg-white border border-slate-200 hover:border-slate-300 rounded-xl px-2 h-10 shadow-2xs transition-all gap-1 shrink-0"
          >
            <!-- 快捷上一周期 -->
            <button
              @click="shiftDate(-1)"
              class="w-7 h-7 flex items-center justify-center rounded-lg text-slate-400 hover:text-brand-600 hover:bg-slate-100 transition cursor-pointer"
              title="上一周期"
            >
              <ChevronLeft class="w-4 h-4" />
            </button>

            <!-- 嵌入式日历展示 (17px 醒目等宽大字号，紧密居中) -->
            <div class="flex items-center">
              <!-- 按日选择 -->
              <el-date-picker
                v-if="periodType === 'day'"
                v-model="currentDate"
                type="date"
                :clearable="false"
                size="default"
                class="seamless-date-picker !w-[150px]"
                @change="handleDateChange"
              />

              <!-- 按周选择 -->
              <el-date-picker
                v-else-if="periodType === 'week'"
                v-model="currentDate"
                type="week"
                format="YYYY 第 ww 周"
                :clearable="false"
                size="default"
                class="seamless-date-picker !w-[172px]"
                @change="handleDateChange"
              />

              <!-- 按月选择 -->
              <el-date-picker
                v-else-if="periodType === 'month'"
                v-model="currentDate"
                type="month"
                format="YYYY-MM"
                :clearable="false"
                size="default"
                class="seamless-date-picker !w-[124px]"
                @change="handleDateChange"
              />
            </div>

            <!-- 快捷下一周期 -->
            <button
              @click="shiftDate(1)"
              class="w-7 h-7 flex items-center justify-center rounded-lg text-slate-400 hover:text-brand-600 hover:bg-slate-100 transition cursor-pointer"
              title="下一周期"
            >
              <ChevronRight class="w-4 h-4" />
            </button>

            <!-- 竖线分割 -->
            <div class="h-4 w-px bg-slate-200 mx-1"></div>

            <!-- 快捷今天/本周/本月跳转 -->
            <button
              @click="resetToCurrentPeriod"
              class="px-2.5 py-1 rounded-lg text-xs font-bold text-slate-600 hover:text-brand-600 hover:bg-brand-50 transition cursor-pointer"
            >
              {{
                periodType === "day"
                  ? "今天"
                  : periodType === "week"
                    ? "本周"
                    : "本月"
              }}
            </button>
          </div>
        </div>

        <!-- 右侧：刷新大盘 -->
        <div class="flex items-center gap-2">
          <button
            @click="handleRefresh"
            :disabled="isLoading"
            class="px-4 h-10 bg-brand-500 hover:bg-brand-600 active:scale-95 text-white font-bold rounded-xl shadow-xs transition cursor-pointer text-xs flex items-center gap-2"
          >
            <RefreshCw
              class="w-4 h-4"
              :class="isLoading ? 'animate-spin' : ''"
            />
            <span>{{ isLoading ? "刷新中..." : "刷新大盘" }}</span>
          </button>
        </div>
      </div>

      <!-- 数据明细矩阵 (真实数据库统计) -->
      <div class="overflow-x-auto">
        <table
          class="w-full text-sm text-left border border-slate-100 rounded-2xl overflow-hidden shadow-2xs"
        >
          <tbody class="divide-y divide-slate-100">
            <tr class="bg-slate-50/60">
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">
                呼出总数
              </td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">
                {{ outboundTotal }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">
                呼出接通数量
              </td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ outboundConnected }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">
                呼出接通率
              </td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">
                {{ outboundRate }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">
                呼出总时长
              </td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ outboundDuration }}
              </td>
            </tr>
            <tr>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">
                {{ inboundTotal }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入接通数量</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ inboundConnected }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">
                {{ inboundRate }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ inboundDuration }}
              </td>
            </tr>
            <tr class="bg-slate-50/60">
              <td class="py-3.5 px-4 font-bold text-slate-700">智能外呼总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">
                {{ autoTotal }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">
                智能外呼接通数
              </td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ autoConnected }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">外呼接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">
                {{ autoRate }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">外呼总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ autoDuration }}
              </td>
            </tr>
            <tr>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线呼叫总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">
                {{ internalTotal }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线接通数量</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ internalConnected }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">
                {{ internalRate }}
              </td>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">
                {{ internalDuration }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 通话量趋势 -->
    <div
      class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 flex-1 flex flex-col justify-between min-h-[360px]"
    >
      <!-- 趋势头部控制栏 -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
        <div class="flex items-center gap-4">
          <h3 class="font-black text-base text-slate-900 tracking-tight">
            通话量趋势
          </h3>

          <!-- 通话类型选择 -->
          <div class="flex items-center gap-2 text-xs">
            <span class="text-slate-400 font-medium">通话类型:</span>
            <select
              v-model="selectedType"
              class="bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 text-slate-800 font-bold focus:outline-none focus:border-brand-500 cursor-pointer text-xs shadow-2xs transition"
            >
              <option value="all">全部通话</option>
              <option value="inbound">呼入通话</option>
              <option value="outbound">呼出通话</option>
              <option value="auto">智能外呼</option>
              <option value="internal">内线呼叫</option>
            </select>
          </div>
        </div>

        <!-- 右侧图例与时段汇总 -->
        <div class="flex items-center gap-4 text-xs font-mono">
          <span class="flex items-center gap-1.5">
            <span class="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
            <span
              >发起总量:
              <strong class="text-slate-800">{{
                currentTrendStats.total
              }}</strong></span
            >
          </span>
          <span class="flex items-center gap-1.5">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
            <span
              >成功接通:
              <strong class="text-slate-800">{{
                currentTrendStats.connected
              }}</strong></span
            >
          </span>
          <span class="flex items-center gap-1.5">
            <span class="text-slate-400">接通率:</span>
            <strong class="text-emerald-600 font-bold">{{
              currentTrendStats.rate
            }}</strong>
          </span>
        </div>
      </div>

      <!-- 趋势展示区 (动态平滑曲线与面积图) -->
      <div
        class="h-64 bg-slate-50/50 rounded-2xl border border-slate-100 p-4 relative flex flex-col justify-between overflow-hidden"
      >
        <!-- 空状态遮罩 -->
        <div
          v-if="currentTrendStats.count === 0"
          class="absolute inset-0 flex flex-col items-center justify-center text-center p-4 z-20 bg-slate-50/70 backdrop-blur-[1px]"
        >
          <div
            class="w-12 h-12 rounded-2xl bg-white border border-slate-200 shadow-xs flex items-center justify-center text-xl mb-2"
          >
            📈
          </div>
          <p class="text-xs font-bold text-slate-700 mb-0.5">
            当前时段暂无
            {{
              selectedType === "inbound"
                ? "呼入"
                : selectedType === "outbound"
                  ? "呼出"
                  : selectedType === "auto"
                    ? "智能外呼"
                    : selectedType === "internal"
                      ? "内线"
                      : ""
            }}
            通话流水
          </p>
          <p class="text-[11px] text-slate-400">
            产生通话或切换周期后将在此呈现走势图
          </p>
        </div>

        <!-- 响应式 SVG 走势图 -->
        <svg
          class="w-full h-full absolute inset-0 cursor-crosshair"
          viewBox="0 0 800 200"
          preserveAspectRatio="none"
          @mousemove="handleSvgMouseMove"
          @mouseleave="handleSvgMouseLeave"
        >
          <defs>
            <!-- 蓝色渐变 (发起总量) -->
            <linearGradient id="totalGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#3B82F6" stop-opacity="0.25" />
              <stop offset="100%" stop-color="#3B82F6" stop-opacity="0.01" />
            </linearGradient>
            <!-- 绿色渐变 (成功接通) -->
            <linearGradient id="connGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#10B981" stop-opacity="0.25" />
              <stop offset="100%" stop-color="#10B981" stop-opacity="0.01" />
            </linearGradient>
          </defs>

          <!-- Y 轴水平网格基准线及刻度数值 -->
          <!-- 100% 刻度线 -->
          <line
            x1="50"
            y1="25"
            x2="770"
            y2="25"
            stroke="#E2E8F0"
            stroke-dasharray="4 4"
            stroke-width="1"
          />
          <text
            x="42"
            y="28"
            text-anchor="end"
            class="text-[10px] fill-slate-400 font-mono"
          >
            {{ scaleCeil }}
          </text>

          <!-- 66% 刻度线 -->
          <line
            x1="50"
            y1="70"
            x2="770"
            y2="70"
            stroke="#E2E8F0"
            stroke-dasharray="4 4"
            stroke-width="1"
          />
          <text
            x="42"
            y="73"
            text-anchor="end"
            class="text-[10px] fill-slate-400 font-mono"
          >
            {{ Math.round(scaleCeil * 0.66) }}
          </text>

          <!-- 33% 刻度线 -->
          <line
            x1="50"
            y1="115"
            x2="770"
            y2="115"
            stroke="#E2E8F0"
            stroke-dasharray="4 4"
            stroke-width="1"
          />
          <text
            x="42"
            y="118"
            text-anchor="end"
            class="text-[10px] fill-slate-400 font-mono"
          >
            {{ Math.round(scaleCeil * 0.33) }}
          </text>

          <!-- 0 底线 -->
          <line
            x1="50"
            y1="160"
            x2="770"
            y2="160"
            stroke="#CBD5E1"
            stroke-width="1.5"
          />
          <text
            x="42"
            y="163"
            text-anchor="end"
            class="text-[10px] fill-slate-400 font-mono"
          >
            0
          </text>

          <!-- 面积填充与趋势折线 (有数据时渲染) -->
          <g v-if="currentTrendStats.count > 0">
            <!-- 发起总量面积图 -->
            <path :d="totalAreaPath" fill="url(#totalGradient)" />
            <!-- 成功接通面积图 -->
            <path :d="connAreaPath" fill="url(#connGradient)" />

            <!-- 发起总量平滑曲线 -->
            <path
              :d="totalLinePath"
              fill="none"
              stroke="#3B82F6"
              stroke-width="2.5"
              stroke-linecap="round"
            />
            <!-- 成功接通平滑曲线 -->
            <path
              :d="connLinePath"
              fill="none"
              stroke="#10B981"
              stroke-width="2.5"
              stroke-linecap="round"
            />

            <!-- 数据点节点标记 (有数值时显示小圆点) -->
            <template v-for="b in trendBuckets" :key="b.key">
              <circle
                v-if="b.total > 0"
                :cx="b.x"
                :cy="b.totalY"
                r="3.5"
                fill="#3B82F6"
                stroke="#FFFFFF"
                stroke-width="1.5"
              />
              <circle
                v-if="b.connected > 0"
                :cx="b.x"
                :cy="b.connY"
                r="3.5"
                fill="#10B981"
                stroke="#FFFFFF"
                stroke-width="1.5"
              />
            </template>
          </g>

          <!-- 鼠标悬停时的纵向指示参考线与高亮圆点 -->
          <g v-if="hoveredBucket">
            <line
              :x1="hoveredBucket.x"
              y1="20"
              :x2="hoveredBucket.x"
              y2="160"
              stroke="#94A3B8"
              stroke-dasharray="3 3"
              stroke-width="1.5"
            />
            <!-- 高亮圆圈 -->
            <circle
              :cx="hoveredBucket.x"
              :cy="hoveredBucket.totalY"
              r="5"
              fill="#3B82F6"
              stroke="#FFFFFF"
              stroke-width="2"
            />
            <circle
              :cx="hoveredBucket.x"
              :cy="hoveredBucket.connY"
              r="5"
              fill="#10B981"
              stroke="#FFFFFF"
              stroke-width="2"
            />
          </g>

          <!-- X 轴刻度文字 -->
          <template v-for="b in trendBuckets" :key="`tick-${b.key}`">
            <text
              v-if="b.showTick"
              :x="b.x"
              y="180"
              text-anchor="middle"
              class="text-[11px] fill-slate-400 font-mono"
            >
              {{ b.axisLabel }}
            </text>
          </template>
        </svg>

        <!-- 悬停数据详情 Tooltip -->
        <div
          v-if="hoveredBucket"
          class="absolute z-30 pointer-events-none bg-slate-900/90 text-white rounded-xl px-3 py-2 text-xs shadow-xl backdrop-blur-xs transition-all"
          :style="{
            left: `${Math.min(Math.max(10, (hoveredBucket.x / 800) * 100), 85)}%`,
            top: '16px',
            transform: 'translateX(-50%)',
          }"
        >
          <div class="font-bold text-slate-200 border-b border-slate-700 pb-1 mb-1.5 flex items-center gap-1.5">
            <span>⏱️</span>
            <span>{{ hoveredBucket.title }}</span>
          </div>
          <div class="flex flex-col gap-1 font-mono">
            <div class="flex items-center justify-between gap-4">
              <span class="flex items-center gap-1 text-slate-300">
                <span class="w-2 h-2 rounded-full bg-blue-400"></span>
                <span>发起总量:</span>
              </span>
              <strong class="text-white font-bold">{{ hoveredBucket.total }} 通</strong>
            </div>
            <div class="flex items-center justify-between gap-4">
              <span class="flex items-center gap-1 text-slate-300">
                <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
                <span>成功接通:</span>
              </span>
              <strong class="text-emerald-400 font-bold">{{ hoveredBucket.connected }} 通</strong>
            </div>
            <div class="flex items-center justify-between gap-4">
              <span class="text-slate-400">接通率:</span>
              <strong class="text-emerald-300">{{ hoveredBucket.rate }}</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.seamless-date-picker.el-date-editor) {
  --el-date-editor-width: auto;
}
:deep(.seamless-date-picker .el-input__wrapper) {
  background: transparent !important;
  box-shadow: none !important;
  padding: 0 4px !important;
  height: 36px !important;
  cursor: pointer !important;
  transition: all 0.15s ease-in-out;
}
:deep(.seamless-date-picker .el-input__wrapper:hover),
:deep(.seamless-date-picker .el-input__wrapper.is-focus) {
  box-shadow: none !important;
}
:deep(.seamless-date-picker .el-input__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace !important;
  font-size: 17px !important;
  font-weight: 800 !important;
  color: #0f172a !important;
  text-align: center !important;
  cursor: pointer !important;
  padding: 0 !important;
  letter-spacing: 0.02em !important;
}
:deep(.seamless-date-picker .el-input__prefix) {
  color: #4f46e5 !important;
  margin-right: 4px !important;
}
:deep(.seamless-date-picker .el-input__prefix .el-icon) {
  font-size: 18px !important;
}
</style>
