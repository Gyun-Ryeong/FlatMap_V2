import { useState, useCallback } from 'react';
import { fetchSafeRoutes, routeErrorMessage } from '../api/routes';

export default function useRoute() {
  const [routes, setRoutes] = useState([]);
  const [scoreModel, setScoreModel] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchRoutes = useCallback(async (origin, destination) => {
    setLoading(true);
    setError(null);
    setRoutes([]);
    try {
      const data = await fetchSafeRoutes(origin, destination);
      if (!data.routes || data.routes.length === 0) {
        setError('경로를 찾을 수 없습니다.');
      } else {
        setRoutes(data.routes);
        setScoreModel(data.scoreModel ?? null);
      }
    } catch (err) {
      setError(routeErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  const clearRoutes = useCallback(() => {
    setRoutes([]);
    setError(null);
  }, []);

  return { routes, scoreModel, loading, error, fetchRoutes, clearRoutes };
}
