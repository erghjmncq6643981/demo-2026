/** Pure SVG geometry; report queries and statistical semantics remain in the feature use case. */
export const buildSmoothPath = (pts: { x: number; y: number }[]): string => {
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
export const buildAreaPath = (pts: { x: number; y: number }[], baseY: number): string => {
  if (pts.length === 0) return "";
  const linePath = buildSmoothPath(pts);
  const first = pts[0];
  const last = pts[pts.length - 1];
  return `${linePath} L ${last.x},${baseY} L ${first.x},${baseY} Z`;
};
