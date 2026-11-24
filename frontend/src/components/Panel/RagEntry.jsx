import { useState } from 'react';
import useRagStatus from '../../hooks/useRagStatus';
import { askRag } from '../../api/rag';
import { MAX_QUESTION_CHARS, ragErrorMessage, shouldShowRagEntry, validateQuestion } from '../../utils/rag';

// 질문 입력과 답변 표시(상태 없는 부분). 렌더 테스트를 위해 분리했다.
export function RagAskView({ value, onChange, onSubmit, loading, error, result }) {
  return (
    <div className="rag-body">
      <textarea
        className="rag-input"
        value={value}
        maxLength={MAX_QUESTION_CHARS}
        rows={3}
        placeholder="궁금한 점을 입력하세요"
        aria-label="질문"
        onChange={(e) => onChange(e.target.value)}
      />
      <button type="button" className="rag-submit" onClick={onSubmit} disabled={loading}>
        {loading ? '답변을 찾는 중...' : '질문하기'}
      </button>
      {error && <p className="route-error">{error}</p>}
      {result && (
        <div className="rag-answer">
          <p>{result.answer}</p>
          {result.sources?.length > 0 && (
            <ul className="rag-sources">
              {result.sources.map((s, i) => <li key={`${s}-${i}`}>{s}</li>)}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

// 진입점 틀. status 가 켜짐+연결됨일 때만 그려지고, 아니면 아무것도 렌더하지 않는다.
export function RagEntryView({ status, open, onToggle, children }) {
  if (!shouldShowRagEntry(status)) return null;
  return (
    <div style={{ padding: '0 16px 12px' }}>
      <button className="score-guide-toggle" onClick={onToggle} aria-expanded={open}>
        <span>💬 질문하기 (Q&amp;A)</span>
        <span style={{ fontSize: 10 }}>{open ? '▲' : '▼'}</span>
      </button>
      {open && children}
    </div>
  );
}

// RAG 는 개발 중이다. 서버가 RAG 연동 지점을 꺼 두었거나(기본) 켜 놓았어도 연결된 RAG 서버가 없으면 이 컴포넌트는 아무것도 그리지 않는다.
export default function RagEntry() {
  const status = useRagStatus();
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const submit = async () => {
    const v = validateQuestion(value);
    if (!v.ok) {
      setError(v.message);
      return;
    }
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      setResult(await askRag(v.question));
    } catch (err) {
      setError(ragErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <RagEntryView status={status} open={open} onToggle={() => setOpen((v) => !v)}>
      <RagAskView value={value} onChange={setValue} onSubmit={submit} loading={loading} error={error} result={result} />
    </RagEntryView>
  );
}
