import { describe, expect, it } from 'vitest';
import {
  getLevel, getLevelLabel, getLevelStyle, SCORE_GUIDE_BANDS, SCORE_HIGH_MIN, SCORE_MID_MIN,
} from '../scoreLevel';

describe('점수 등급 표기', () => {
  it('구간 경계: 71 이상 높은 편, 41 이상 보통, 그 아래 낮은 편', () => {
    expect(getLevel(100)).toBe('high');
    expect(getLevel(SCORE_HIGH_MIN)).toBe('high');
    expect(getLevel(SCORE_HIGH_MIN - 0.1)).toBe('mid');
    expect(getLevel(SCORE_MID_MIN)).toBe('mid');
    expect(getLevel(SCORE_MID_MIN - 0.1)).toBe('low');
    expect(getLevel(0)).toBe('low');
  });

  it('점수가 없으면 unknown, 라벨은 "-"', () => {
    expect(getLevel(null)).toBe('unknown');
    expect(getLevel(undefined)).toBe('unknown');
    expect(getLevel(NaN)).toBe('unknown');
    expect(getLevelLabel(null)).toBe('-');
  });

  it('라벨에 "안전"·"위험" 같은 단정 표현을 쓰지 않는다 (점수는 설계 파라미터 기반 상대 비교 값)', () => {
    [0, 20, 41, 60, 71, 100].forEach((s) => {
      expect(getLevelLabel(s)).not.toMatch(/안전|위험/);
    });
    expect(getLevelLabel(80)).toBe('높은 편');
    expect(getLevelLabel(50)).toBe('보통');
    expect(getLevelLabel(10)).toBe('낮은 편');
  });

  it('등급마다 색상 세트가 있다', () => {
    [90, 50, 10, null].forEach((score) => {
      expect(getLevelStyle(score)).toMatchObject({
        color: expect.any(String), bg: expect.any(String), border: expect.any(String),
      });
    });
  });

  it('점수 가이드 구간은 표시 기준과 같은 상수에서 만들어진다', () => {
    expect(SCORE_GUIDE_BANDS.map((b) => b.range)).toEqual(['0–40', '41–70', '71–100']);
  });
});
