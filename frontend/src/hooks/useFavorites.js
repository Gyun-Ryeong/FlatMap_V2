import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { getFavorites, addFavorite, removeFavorite } from '../api/favorites';

// 로그인한 사용자의 즐겨찾기. 로그아웃하면 비운다. 실패는 조용히 무시한다(검색·경로 기능에 영향 없음).
export default function useFavorites() {
  const { isLoggedIn } = useAuth();
  const [favorites, setFavorites] = useState([]);

  useEffect(() => {
    if (!isLoggedIn) {
      setFavorites([]);
      return;
    }
    getFavorites().then(setFavorites).catch(() => {});
  }, [isLoggedIn]);

  const isFavorited = useCallback(
    (place) => !!place && favorites.some((f) => f.latitude === place.lat && f.longitude === place.lng),
    [favorites]
  );

  const add = useCallback(async (place) => {
    if (!place || isFavorited(place)) return;
    try {
      const saved = await addFavorite(place);
      // 백엔드가 중복 시 기존 항목을 돌려주므로 id 기준으로 중복 추가를 막는다
      setFavorites((prev) => (prev.some((f) => f.id === saved.id) ? prev : [saved, ...prev]));
    } catch {
      /* 저장 실패는 무시 */
    }
  }, [isFavorited]);

  const remove = useCallback(async (id) => {
    try {
      await removeFavorite(id);
      setFavorites((prev) => prev.filter((f) => f.id !== id));
    } catch {
      /* 삭제 실패는 무시 */
    }
  }, []);

  return { isLoggedIn, favorites, isFavorited, add, remove };
}
