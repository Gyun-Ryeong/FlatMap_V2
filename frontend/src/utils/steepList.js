// "오르막길 구간" 목록(FlatMap 오르막길 탭 복원)용 순수 함수.
// 대상은 붕괴위험 지정 급경사지 표본이다. 등급(위험·주의·양호)은 없고 경사도(각도 °)만 있으며 측정되지 않았을 수 있다.
// 보행난이도 임계값을 적용하지 않는다 — 보행난이도는 경로의 DEM 경사로 따로 계산한다.

export const REGION_OPTIONS = [
  { label: '전체 지역', value: '' },
  { label: '성남시', value: '411' },
  { label: '수정구', value: '41131' },
  { label: '중원구', value: '41133' },
  { label: '분당구', value: '41135' },
];

export const SORT_OPTIONS = [
  { label: '경사 큰 순', value: 'grade' },
  { label: '이름 순', value: 'name' },
];

export function formatSteepAddress(item) {
  const parts = [item.sigungu, item.eupmyeon, item.jibun].filter(Boolean);
  if (parts.length) return parts.join(' ');
  return item.regionCode ? `지역코드 ${item.regionCode}` : '';
}

// 경사도가 없는 항목을 "0°"나 양호로 보이지 않게 "미측정"으로 쓴다.
export function gradeLabel(grade) {
  return grade != null ? `경사 ${grade.toFixed(1)}°` : '경사 미측정';
}

export function summarizeSteep(items) {
  return {
    total: items.length,
    mountain: items.filter((i) => i.isMountain).length,
    unmeasured: items.filter((i) => i.grade == null).length,
  };
}

// 경사 큰 순: 측정값이 있는 항목을 먼저(큰 것부터), 미측정은 뒤로. 같으면 이름 순. 원본 배열은 바꾸지 않는다.
export function sortSteepSlopes(items, sort) {
  const byName = (a, b) => (a.name ?? '').localeCompare(b.name ?? '', 'ko');
  const copy = [...items];
  if (sort === 'name') return copy.sort(byName);
  return copy.sort((a, b) => {
    if (a.grade == null && b.grade == null) return byName(a, b);
    if (a.grade == null) return 1;
    if (b.grade == null) return -1;
    return b.grade - a.grade || byName(a, b);
  });
}
