/**
 * 统一时间格式化工具：格式化为 yyyy-MM-dd HH:mm:ss
 *
 * 自动识别并兼容：
 * 1. 后端存储的 UTC ISO 格式串（如 "2026-09-29T06:38:57.178" 或 带 Z 的 "2026-09-29T06:38:57.178Z"），转为本地时区东八区显示；
 * 2. 毫秒时间戳（number 或 string 形式的时间戳）；
 * 3. Date 实例；
 * 4. 已有 "yyyy-MM-dd HH:mm:ss" 串保持原样或解析校验。
 */
export function formatDateTime(value?: string | number | Date | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }

  let date: Date;
  if (value instanceof Date) {
    date = value;
  } else if (typeof value === 'number') {
    date = new Date(value);
  } else {
    let str = String(value).trim();
    if (!str || str === '-') {
      return '-';
    }

    // 如果是纯数字时间戳字符串（如 "1790662613038"）
    if (/^\d{10,13}$/.test(str)) {
      date = new Date(Number(str));
    } else {
      // 如果包含 'T' 且没有时区偏移（+08:00 或 Z 等），说明是后端直接返回的无时区 UTC 串，统一追加 'Z' 作为 UTC 解析
      if (str.includes('T') && !str.endsWith('Z') && !/[+-]\d{2}(:\d{2})?$/.test(str)) {
        str += 'Z';
      }
      date = new Date(str);
    }
  }

  if (isNaN(date.getTime())) {
    // 降级截取处理
    return String(value).replace('T', ' ').slice(0, 19);
  }

  const Y = date.getFullYear();
  const M = String(date.getMonth() + 1).padStart(2, '0');
  const D = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const m = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  return `${Y}-${M}-${D} ${h}:${m}:${s}`;
}
