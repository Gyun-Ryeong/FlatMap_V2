import { useState, useRef, useEffect, useCallback } from 'react';
import { searchPlaces } from '../utils/kakaoPlaces';

const INPUT_STYLE = {
  width: '100%',
  minHeight: 52,
  padding: '12px 12px 12px 34px',
  background: 'white',
  border: '2px solid #d9dde5',
  borderRadius: 10,
  color: '#20232b',
  fontSize: 17,
  fontFamily: 'inherit',
  fontWeight: 600,
  outline: 'none',
  boxSizing: 'border-box',
};

function pickButtonStyle(active, activeColor) {
  return {
    flex: 1,
    padding: '8px 6px',
    borderRadius: 9,
    border: active ? `2px solid ${activeColor}` : '1.5px solid #d9dde4',
    background: active ? `${activeColor}14` : 'white',
    color: active ? activeColor : '#525965',
    fontSize: 13,
    fontWeight: 800,
    cursor: 'pointer',
    fontFamily: 'inherit',
  };
}

const SEARCH_DEBOUNCE_MS = 300;

export default function SearchBar({
  onSelectOrigin,
  onSelectDestination,
  originValue,
  destValue,
  clickMode,
  onChangeClickMode,
  onFieldFocus,
  fieldExtras,
}) {
  const [originText, setOriginText] = useState('');
  const [destText, setDestText] = useState('');
  const [originResults, setOriginResults] = useState([]);
  const [destResults, setDestResults] = useState([]);
  const [activeField, setActiveField] = useState(null);
  const [locationError, setLocationError] = useState('');
  const searchBarRef = useRef(null);
  const debounceRef = useRef({});

  useEffect(() => {
    if (originValue !== undefined) setOriginText(originValue);
  }, [originValue]);

  useEffect(() => {
    if (destValue !== undefined) setDestText(destValue);
  }, [destValue]);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClick = (e) => {
      if (searchBarRef.current && !searchBarRef.current.contains(e.target)) {
        setActiveField(null);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  useEffect(() => {
    const timers = debounceRef.current;
    return () => Object.values(timers).forEach(clearTimeout);
  }, []);

  const search = useCallback((key, keyword, setResults) => {
    clearTimeout(debounceRef.current[key]);
    if (keyword.trim().length <= 1) {
      setResults([]);
      return;
    }
    debounceRef.current[key] = setTimeout(async () => {
      setResults(await searchPlaces(keyword));
    }, SEARCH_DEBOUNCE_MS);
  }, []);

  const focusField = (key) => {
    setActiveField(key);
    if (onFieldFocus) onFieldFocus(key);
  };

  const handleOriginChange = (e) => {
    const val = e.target.value;
    setOriginText(val);
    focusField('origin');
    search('origin', val, setOriginResults);
  };

  const handleDestChange = (e) => {
    const val = e.target.value;
    setDestText(val);
    focusField('dest');
    search('dest', val, setDestResults);
  };

  const selectOrigin = (place) => {
    setOriginText(place.name);
    setOriginResults([]);
    setActiveField(null);
    onSelectOrigin(place.lat, place.lng, place.name, place);
  };

  const selectDest = (place) => {
    setDestText(place.name);
    setDestResults([]);
    setActiveField(null);
    onSelectDestination(place.lat, place.lng, place.name, place);
  };

  const handleCurrentLocation = () => {
    setLocationError('');
    if (!navigator.geolocation) {
      setLocationError('이 브라우저는 위치 서비스를 지원하지 않습니다.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude, longitude } = pos.coords;
        setOriginText('현재 위치');
        setOriginResults([]);
        setActiveField(null);
        onSelectOrigin(latitude, longitude, '현재 위치');
      },
      () => setLocationError('위치 정보를 가져올 수 없습니다. 브라우저의 위치 권한을 확인해 주세요.')
    );
  };

  const fields = [
    {
      key: 'origin',
      placeholder: '출발지 검색',
      value: originText,
      onChange: handleOriginChange,
      results: originResults,
      onPick: selectOrigin,
      dot: { color: '#6b8fcf', round: true },
    },
    {
      key: 'dest',
      placeholder: '목적지 검색',
      value: destText,
      onChange: handleDestChange,
      results: destResults,
      onPick: selectDest,
      dot: { color: '#b84c4c', round: false },
    },
  ];

  return (
    <div ref={searchBarRef} style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
      {fields.map((field) => (
        <div key={field.key} className="search-field" style={{ position: 'relative' }}>
          <div
            style={{
              position: 'absolute',
              left: 13,
              top: '50%',
              transform: 'translateY(-50%)',
              width: 10,
              height: 10,
              borderRadius: field.dot.round ? '50%' : 2,
              background: field.dot.color,
            }}
          />
          <input
            className="location-input"
            type="text"
            placeholder={field.placeholder}
            value={field.value}
            onChange={field.onChange}
            onFocus={() => focusField(field.key)}
            style={{ ...INPUT_STYLE, paddingRight: fieldExtras ? 44 : 12 }}
          />
          {fieldExtras && fieldExtras(field.key)}
          {activeField === field.key && field.results.length > 0 && (
            <ul className="search-dropdown">
              {field.results.map((place, idx) => (
                <li key={`${place.name}-${idx}`} onClick={() => field.onPick(place)}>
                  <span className="place-name">{place.name}</span>
                  <span className="place-address">{place.address}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}

      <div style={{ display: 'flex', gap: 6 }}>
        <button onClick={handleCurrentLocation} style={pickButtonStyle(false, '#5a7ab8')}>
          ◎ 현재 위치
        </button>
        <button
          onClick={() => onChangeClickMode(clickMode === 'origin' ? null : 'origin')}
          style={pickButtonStyle(clickMode === 'origin', '#5a7ab8')}
        >
          {clickMode === 'origin' ? '지도를 누르세요' : '출발 지도선택'}
        </button>
        <button
          onClick={() => onChangeClickMode(clickMode === 'destination' ? null : 'destination')}
          style={pickButtonStyle(clickMode === 'destination', '#b84c4c')}
        >
          {clickMode === 'destination' ? '지도를 누르세요' : '도착 지도선택'}
        </button>
      </div>
      {locationError && <div className="route-error">{locationError}</div>}
    </div>
  );
}
