import { useState, useEffect, useRef, useCallback } from 'react';

const COUNTDOWN_SECONDS = 10;

// 헤더의 "긴급 신고" 버튼으로 열린다. 10초 카운트다운 후 112로 자동 연결된다(tel: 링크).
export default function SOSModal({ open, onClose }) {
  const [count, setCount] = useState(COUNTDOWN_SECONDS);
  const timerRef = useRef(null);

  const stopTimer = useCallback(() => {
    clearInterval(timerRef.current);
    timerRef.current = null;
  }, []);

  const callEmergency = useCallback(() => {
    stopTimer();
    window.location.href = 'tel:112';
  }, [stopTimer]);

  const cancel = useCallback(() => {
    stopTimer();
    setCount(COUNTDOWN_SECONDS);
    onClose();
  }, [stopTimer, onClose]);

  // 카운트다운: 1초마다 줄이고, 0 이 되면 렌더 밖(effect)에서 전화를 건다 (state 업데이터 안에서 부수효과를 내지 않는다).
  useEffect(() => {
    if (!open) return undefined;
    setCount(COUNTDOWN_SECONDS);
    timerRef.current = setInterval(() => setCount((prev) => Math.max(0, prev - 1)), 1000);
    return stopTimer;
  }, [open, stopTimer]);

  useEffect(() => {
    if (open && count === 0) callEmergency();
  }, [open, count, callEmergency]);

  // 닫히면 카운트를 되돌려, 다시 열 때 이전의 0 을 보고 곧바로 전화가 걸리는 일이 없게 한다.
  useEffect(() => {
    if (!open) setCount(COUNTDOWN_SECONDS);
  }, [open]);

  if (!open) return null;

  return (
    <div className="sos-overlay">
      <div className="sos-modal" role="alertdialog" aria-labelledby="sos-title">
        <p className="sos-title" id="sos-title">긴급 신고</p>
        <p className="sos-desc">
          <span className="sos-count">{count}</span>초 후 112에 자동 연결됩니다
        </p>

        <div className="sos-progress-ring">
          <svg viewBox="0 0 120 120">
            <circle className="sos-ring-bg" cx="60" cy="60" r="52" />
            <circle
              className="sos-ring-fg"
              cx="60"
              cy="60"
              r="52"
              style={{ strokeDashoffset: 2 * Math.PI * 52 * (1 - count / COUNTDOWN_SECONDS) }}
            />
          </svg>
          <span className="sos-ring-number">{count}</span>
        </div>

        <div className="sos-actions">
          <button className="sos-btn-cancel" onClick={cancel}>
            취소
          </button>
          <button className="sos-btn-now" onClick={callEmergency}>
            즉시 신고
          </button>
        </div>
      </div>
    </div>
  );
}
