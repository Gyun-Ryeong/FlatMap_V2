import { useEffect, useState } from 'react';

const DURATION = 10000;

// 내 위치가 "시설 밀도 낮음" 격자에 들어왔을 때의 안내. 위험하다는 뜻이 아니라 수집된 시설 데이터가 적다는 안내다.
export default function DensityNotice({ cell, onDismiss }) {
  const [progress, setProgress] = useState(100);

  useEffect(() => {
    const start = Date.now();
    const timer = setTimeout(onDismiss, DURATION);

    const interval = setInterval(() => {
      const elapsed = Date.now() - start;
      setProgress(Math.max(0, 100 - (elapsed / DURATION) * 100));
    }, 50);

    return () => {
      clearTimeout(timer);
      clearInterval(interval);
    };
  }, [onDismiss]);

  return (
    <div className="density-notice" role="status">
      <span className="density-notice-icon" aria-hidden="true">ⓘ</span>
      <div className="density-notice-text">
        <span className="density-notice-title">시설이 적은 구역 안내</span>
        <span className="density-notice-desc">
          이 주변은 수집된 안전시설 데이터가 적은 구역입니다(시설 {cell.facilityCount}개).
          위험하다는 뜻은 아니며, 주변을 살피며 이동하세요.
        </span>
        <div className="density-notice-gauge-bg">
          <div className="density-notice-gauge-fill" style={{ width: `${progress}%` }} />
        </div>
      </div>
      <button className="density-notice-close" onClick={onDismiss} aria-label="안내 닫기">✕</button>
    </div>
  );
}
