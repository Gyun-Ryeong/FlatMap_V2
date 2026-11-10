// 붕괴위험 지정지(급경사지) 경고 구간을 경로 좌표 인덱스 범위로 바꾼다.
// 경로 정점 기준 nearestRouteIdx 앞뒤 SPREAD 개 좌표를 경고 구간으로 보고, 겹치거나 맞닿으면 합친다.
export const WARNING_SPREAD = 8;

export function buildWarningRanges(riskSections, coordCount, spread = WARNING_SPREAD) {
  if (!riskSections || !riskSections.length || coordCount < 2) return [];
  const ranges = riskSections
    .filter((rs) => Number.isInteger(rs.nearestRouteIdx))
    .map((rs) => ({
      start: Math.max(0, rs.nearestRouteIdx - spread),
      end: Math.min(coordCount - 1, rs.nearestRouteIdx + spread),
    }))
    .sort((a, b) => a.start - b.start);

  const merged = [];
  for (const r of ranges) {
    const last = merged[merged.length - 1];
    if (last && r.start <= last.end + 1) {
      last.end = Math.max(last.end, r.end);
    } else {
      merged.push({ ...r });
    }
  }
  return merged;
}

// 경로 좌표를 [{warning:false|true, path:[...]}, ...] 조각으로 나눈다(조각끼리 끝점 하나를 공유해 선이 이어진다).
export function splitPathByWarnings(path, ranges) {
  if (!path.length) return [];
  if (!ranges.length) return [{ warning: false, path }];
  const pieces = [];
  let cursor = 0;
  for (const range of ranges) {
    if (cursor < range.start) pieces.push({ warning: false, path: path.slice(cursor, range.start + 1) });
    pieces.push({ warning: true, path: path.slice(range.start, range.end + 1) });
    cursor = range.end;
  }
  if (cursor < path.length - 1) pieces.push({ warning: false, path: path.slice(cursor) });
  return pieces.filter((p) => p.path.length >= 2);
}
