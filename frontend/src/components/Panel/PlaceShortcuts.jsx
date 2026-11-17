import { useState } from 'react';

const favToPlace = (f) => ({ name: f.name, lat: f.latitude, lng: f.longitude, address: f.address || '' });

// 즐겨찾기(로그인 필요)와 최근 검색 장소. 누르면 포커스된 칸(없으면 도착지)에 들어간다.
export default function PlaceShortcuts({
  isLoggedIn, favorites, onRemoveFavorite, history, onRemoveHistory, onClearHistory, onPick,
}) {
  const [tab, setTab] = useState('favorites');

  return (
    <div className="place-shortcuts">
      <div className="place-shortcuts-tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'favorites'} className={`tab-fav${tab === 'favorites' ? ' active' : ''}`} onClick={() => setTab('favorites')}>
          ★ 즐겨찾기{isLoggedIn ? ` (${favorites.length})` : ''}
        </button>
        <button role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>
          최근 검색
        </button>
      </div>

      {tab === 'favorites' && (
        !isLoggedIn ? (
          <p className="place-shortcuts-empty">로그인하면 자주 가는 곳을 저장할 수 있어요.</p>
        ) : favorites.length === 0 ? (
          <p className="place-shortcuts-empty">저장된 즐겨찾기가 없습니다. 장소를 고른 뒤 입력칸의 ★ 로 추가해 보세요.</p>
        ) : (
          <ul className="place-shortcuts-list">
            {favorites.map((fav) => (
              <li key={fav.id}>
                <button type="button" className="place-shortcuts-main" onClick={() => onPick(favToPlace(fav))}>
                  <span className="place-shortcuts-name">★ {fav.name}</span>
                  {fav.address && <span className="place-shortcuts-address">{fav.address}</span>}
                </button>
                <button type="button" className="place-shortcuts-remove" aria-label={`${fav.name} 즐겨찾기 삭제`} onClick={() => onRemoveFavorite(fav.id)}>×</button>
              </li>
            ))}
          </ul>
        )
      )}

      {tab === 'history' && (
        history.length === 0 ? (
          <p className="place-shortcuts-empty">최근 검색한 장소가 여기에 표시됩니다.</p>
        ) : (
          <>
            <ul className="place-shortcuts-list">
              {history.map((h) => (
                <li key={h.id}>
                  <button type="button" className="place-shortcuts-main" onClick={() => onPick(h)}>
                    <span className="place-shortcuts-name">{h.name}</span>
                    {h.address && <span className="place-shortcuts-address">{h.address}</span>}
                  </button>
                  <button type="button" className="place-shortcuts-remove" aria-label={`${h.name} 기록 삭제`} onClick={() => onRemoveHistory(h.id)}>×</button>
                </li>
              ))}
            </ul>
            <button type="button" className="place-shortcuts-clear" onClick={onClearHistory}>기록 전체 삭제</button>
          </>
        )
      )}
    </div>
  );
}
