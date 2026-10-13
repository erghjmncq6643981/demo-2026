import { useReportData } from './useReportData';
import { currentBusinessDate } from '../model/businessDate';
import { buildSmoothPath, buildAreaPath } from '../model/chartGeometry';
import { ref, computed, onMounted } from "vue";
import { fetchMonitoring } from "../api/monitoringApi";

export type PeriodType = "day" | "week" | "month";

/** Owns the feature state, effects and use cases; the view only composes rendering. */
export function useMonitoringReport() {

  const periodType = ref<PeriodType>("day");
  const currentDate = ref<Date>(currentBusinessDate());
  const selectedType = ref<"all" | "inbound" | "outbound" | "auto" | "internal">("all");
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
  const outboundTotal = computed(() => outboundList.value.reduce((sum, c) => sum + c.total, 0));
  const outboundConnected = computed(
    () =>
      outboundList.value.reduce((sum, c) => sum + c.connected, 0),
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
  const inboundTotal = computed(() => inboundList.value.reduce((sum, c) => sum + c.total, 0));
  const inboundConnected = computed(
    () =>
      inboundList.value.reduce((sum, c) => sum + c.connected, 0),
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
    rawCdrs.value.filter((c) => c.automatic),
  );
  const autoTotal = computed(() => autoList.value.reduce((sum, c) => sum + c.total, 0));
  const autoConnected = computed(
    () =>
      autoList.value.reduce((sum, c) => sum + c.connected, 0),
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
  const internalTotal = computed(() => internalList.value.reduce((sum, c) => sum + c.total, 0));
  const internalConnected = computed(
    () =>
      internalList.value.reduce((sum, c) => sum + c.connected, 0),
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
    const total = trendFilteredCdrs.value.reduce((sum, c) => sum + c.total, 0);
    const connected = trendFilteredCdrs.value.reduce((sum, c) => sum + c.connected, 0);
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
          if (h >= 0 && h < 24) counts[h] += c.total;
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
          if (dayDiff >= 0 && dayDiff < 7) counts[dayDiff] += c.total;
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
          if (d >= 1 && d <= totalDays) counts[d - 1] += c.total;
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
        const total = inHour.reduce((sum, c) => sum + c.total, 0);
        const connected = inHour.reduce((sum, c) => sum + c.connected, 0);
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
        const total = inDay.reduce((sum, c) => sum + c.total, 0);
        const connected = inDay.reduce((sum, c) => sum + c.connected, 0);
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
        const total = inDay.reduce((sum, c) => sum + c.total, 0);
        const connected = inDay.reduce((sum, c) => sum + c.connected, 0);
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
    currentDate.value = currentBusinessDate();
    hoveredIndex.value = null;
    loadData();
  };

  const handleDateChange = () => {
    hoveredIndex.value = null;
    loadData();
  };

  const { rawCdrs, refreshTime, isLoading, hasLoaded, loadError, loadData } = useReportData(() =>
    fetchMonitoring(periodType.value, formatLocalYmd(currentDate.value)));

  const handleRefresh = () => {
    loadData();
  };

  onMounted(() => {
    loadData();
  });

  return { hasLoaded, loadError, periodType, currentDate, selectedType, refreshTime, isLoading, rawCdrs, hoveredIndex, formatLocalYmd, dateRange, formatDuration, outboundList, outboundTotal, outboundConnected, outboundRate, outboundDuration, inboundList, inboundTotal, inboundConnected, inboundRate, inboundDuration, autoList, autoTotal, autoConnected, autoRate, autoDuration, internalList, internalTotal, internalConnected, internalRate, internalDuration, trendFilteredCdrs, currentTrendStats, X_START, X_END, Y_TOP, Y_BOTTOM, scaleCeil, trendBuckets, buildSmoothPath, buildAreaPath, totalLinePath, totalAreaPath, connLinePath, connAreaPath, handleSvgMouseMove, handleSvgMouseLeave, hoveredBucket, setPeriodType, shiftDate, resetToCurrentPeriod, handleDateChange, loadData, handleRefresh };
}
