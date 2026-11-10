import { useState, useRef, useCallback, useEffect } from 'react';
import Header from '../components/Header';
import KakaoMap from '../components/KakaoMap';
import SearchBar from '../components/SearchBar';
import RouteOptions from '../components/Panel/RouteOptions';
import LayerPanel from '../components/Panel/LayerPanel';
import ScoreGuide from '../components/Panel/ScoreGuide';
import RouteResult from '../components/Route/RouteResult';
import RouteOverlay from '../components/Map/RouteOverlay';
import FacilityOverlay from '../components/Map/FacilityOverlay';
import DensityOverlay from '../components/Map/DensityOverlay';
import LocationOverlay from '../components/Map/LocationOverlay';
import PointOverlay from '../components/Map/PointOverlay';
import DensityNotice from '../components/DensityNotice';
import SOSModal from '../components/SOSModal';
import useRoute from '../hooks/useRoute';
import useCurrentLocation from '../hooks/useCurrentLocation';
import useDensityNotice from '../hooks/useDensityNotice';
import { pickRouteByPriority, PRIORITY_OPTIONS } from '../utils/routePriority';

const DEFAULT_LAYERS = { CCTV: true, SECURITY_LIGHT: true, POLICE: true, DENSITY: false };
const FACILITY_KEYS = ['CCTV', 'SECURITY_LIGHT', 'POLICE'];
const BOUNDS_DEBOUNCE_MS = 500;

export default function MapPage({ onHome }) {
  const [map, setMap] = useState(null);
  const [mapFailed, setMapFailed] = useState(false);
  const [origin, setOrigin] = useState(null);
  const [destination, setDestination] = useState(null);
  const [originLabel, setOriginLabel] = useState(undefined);
  const [destLabel, setDestLabel] = useState(undefined);
  const [clickMode, setClickMode] = useState(null);
  const [selectedRouteId, setSelectedRouteId] = useState(null);
  const [resultsMode, setResultsMode] = useState(false);
  const [priority, setPriority] = useState('safety');
  const [layers, setLayers] = useState(DEFAULT_LAYERS);
  const [facilityTruncated, setFacilityTruncated] = useState(false);
  const [bounds, setBounds] = useState(null);
  const [zoom, setZoom] = useState(null);
  const [sheetExpanded, setSheetExpanded] = useState(true);
  const [sosOpen, setSosOpen] = useState(false);

  const boundsTimerRef = useRef(null);
  const initialCenteredRef = useRef(false);
  const priorityRef = useRef(priority);
  priorityRef.current = priority;

  const { routes, scoreModel, loading, error, fetchRoutes, clearRoutes } = useRoute();
  const { location: currentLocation, error: locationError } = useCurrentLocation();
  const { notice: densityNotice, dismiss: dismissDensityNotice } = useDensityNotice(currentLocation);

  // 경로가 도착하면 선택한 기준(안전 우선/균형/빠른 길)에 맞는 경로를 고르고 결과 뷰로 전환
  useEffect(() => {
    if (!routes || routes.length === 0) return;
    const picked = pickRouteByPriority(routes, priorityRef.current);
    if (picked) setSelectedRouteId(picked.routeId);
    setResultsMode(true);
    setSheetExpanded(true);
  }, [routes]);

  // 지도 뷰포트 변경 시 bounds/zoom 업데이트 (디바운스)
  useEffect(() => {
    if (!map) return undefined;
    const kakao = window.kakao;

    const update = () => {
      clearTimeout(boundsTimerRef.current);
      boundsTimerRef.current = setTimeout(() => {
        const b = map.getBounds();
        const sw = b.getSouthWest();
        const ne = b.getNorthEast();
        setBounds({ lat1: sw.getLat(), lng1: sw.getLng(), lat2: ne.getLat(), lng2: ne.getLng() });
        setZoom(map.getLevel());
      }, BOUNDS_DEBOUNCE_MS);
    };

    update();
    kakao.maps.event.addListener(map, 'bounds_changed', update);
    kakao.maps.event.addListener(map, 'zoom_changed', update);

    return () => {
      clearTimeout(boundsTimerRef.current);
      kakao.maps.event.removeListener(map, 'bounds_changed', update);
      kakao.maps.event.removeListener(map, 'zoom_changed', update);
    };
  }, [map]);

  // 첫 위치 수신 시 지도 중심 이동
  useEffect(() => {
    if (!map || !currentLocation || initialCenteredRef.current) return;
    map.setCenter(new window.kakao.maps.LatLng(currentLocation.lat, currentLocation.lng));
    initialCenteredRef.current = true;
  }, [map, currentLocation]);

  const handleMapReady = useCallback((mapInstance) => {
    setMap(mapInstance);
  }, []);

  const moveCenter = useCallback((lat, lng) => {
    if (!map) return;
    map.setCenter(new window.kakao.maps.LatLng(lat, lng));
  }, [map]);

  const handleSelectOrigin = useCallback((lat, lng, label) => {
    setOrigin({ lat, lng });
    if (label) setOriginLabel(label);
    moveCenter(lat, lng);
  }, [moveCenter]);

  const handleSelectDestination = useCallback((lat, lng, label) => {
    setDestination({ lat, lng });
    if (label) setDestLabel(label);
    moveCenter(lat, lng);
  }, [moveCenter]);

  const handleMapClick = useCallback((lat, lng) => {
    const label = `선택한 위치 (${lat.toFixed(5)}, ${lng.toFixed(5)})`;
    if (clickMode === 'origin') {
      handleSelectOrigin(lat, lng, label);
    } else if (clickMode === 'destination') {
      handleSelectDestination(lat, lng, label);
    }
    setClickMode(null);
  }, [clickMode, handleSelectOrigin, handleSelectDestination]);

  const handleSearch = () => {
    if (!origin || !destination) return;
    setSelectedRouteId(null);
    setResultsMode(false);
    setSheetExpanded(true);
    fetchRoutes(origin, destination);
  };

  // 결과 화면에서 조건 입력 화면으로 돌아간다 (출발/도착 지점은 유지)
  const handleEditConditions = () => {
    setResultsMode(false);
    setSelectedRouteId(null);
    clearRoutes();
    setSheetExpanded(true);
  };

  const handleSelectRoute = (routeId) => {
    setSelectedRouteId(routeId);
    setSheetExpanded(false);
  };

  const handleCenterOnMe = () => {
    if (!map || !currentLocation) return;
    map.setCenter(new window.kakao.maps.LatLng(currentLocation.lat, currentLocation.lng));
    map.setLevel(3);
  };

  const handleToggleLayer = (key) => {
    setLayers((current) => ({ ...current, [key]: !current[key] }));
  };

  const handleToggleAllFacilities = () => {
    setLayers((current) => {
      const allOn = FACILITY_KEYS.every((k) => current[k]);
      return { ...current, ...Object.fromEntries(FACILITY_KEYS.map((k) => [k, !allOn])) };
    });
  };

  const canSearch = origin && destination && !loading;
  const priorityLabel = PRIORITY_OPTIONS.find((o) => o.value === priority)?.label;

  return (
    <div className="map-page">
      <Header mapReady={!!map} mapFailed={mapFailed} onHome={onHome} onSOS={() => setSosOpen(true)} />

      <div className="map-stage">
        <KakaoMap
          onMapReady={handleMapReady}
          onMapClick={handleMapClick}
          onError={() => setMapFailed(true)}
          clickMode={clickMode}
        />

        {!map && (
          <div className="map-loading">
            <div style={{ textAlign: 'center', color: '#8a8e9a' }}>
              <div style={{ fontSize: 28, marginBottom: 8 }}>🗺️</div>
              <div style={{ fontSize: 13 }}>
                {mapFailed
                  ? '지도를 불러오지 못했습니다. 카카오 JS 키(VITE_KAKAO_API_KEY)와 허용 도메인 설정을 확인해 주세요.'
                  : '카카오 지도를 불러오는 중...'}
              </div>
            </div>
          </div>
        )}

        {/* 현재 위치로 이동 버튼 */}
        <button
          className={`btn-my-location${!currentLocation ? ' btn-my-location--inactive' : ''}`}
          onClick={handleCenterOnMe}
          title={locationError ? '위치 권한이 필요합니다' : currentLocation ? '내 위치로' : '위치 확인 중...'}
          aria-label="내 위치로 이동"
        >
          {locationError ? '🚫' : currentLocation ? '📍' : '⏳'}
        </button>

        {/* 시설이 적은 구역 안내 */}
        {densityNotice && <DensityNotice cell={densityNotice} onDismiss={dismissDensityNotice} />}

        {/* 좌측 패널 (모바일에서는 하단 시트) */}
        <div className={`map-panel ${resultsMode ? 'map-panel-results' : ''} ${sheetExpanded ? '' : 'map-panel-collapsed'}`}>
          <button
            className="mobile-sheet-toggle"
            onClick={() => setSheetExpanded((value) => !value)}
            aria-expanded={sheetExpanded}
          >
            <span className="mobile-sheet-handle" />
            <span>{sheetExpanded ? '지도를 크게 보기' : resultsMode ? '경로 정보 다시 보기' : '길찾기 조건 다시 보기'}</span>
            <span className="mobile-sheet-chevron" aria-hidden="true">{sheetExpanded ? '⌄' : '⌃'}</span>
          </button>

          {/* 검색 */}
          <div className="panel-search">
            {resultsMode ? (
              <div className="panel-search-summary">
                <div className="panel-search-route">
                  <span style={{ color: '#6b8fcf', fontWeight: 600 }}>{originLabel || '출발지'}</span>
                  <span style={{ margin: '0 5px', color: '#c4c8d4' }}>→</span>
                  <span style={{ color: '#b84c4c', fontWeight: 600 }}>{destLabel || '목적지'}</span>
                </div>
                <button className="edit-conditions-button" onClick={handleEditConditions}>
                  조건 수정
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                <SearchBar
                  onSelectOrigin={handleSelectOrigin}
                  onSelectDestination={handleSelectDestination}
                  originValue={originLabel}
                  destValue={destLabel}
                  clickMode={clickMode}
                  onChangeClickMode={setClickMode}
                />

                <RouteOptions priority={priority} onChangePriority={setPriority} />

                <button className="search-submit" onClick={handleSearch} disabled={!canSearch}>
                  {loading ? '경로를 찾는 중...' : '경로 찾기'}
                </button>

                {error && <div className="route-error">{error}</div>}
              </div>
            )}
          </div>

          {resultsMode && (
            <>
              <div className="applied-conditions">
                <div className="applied-conditions-title">적용된 탐색 조건</div>
                <span className="applied-conditions-chip">{priorityLabel}</span>
              </div>

              <RouteResult
                routes={routes ?? []}
                selectedRouteId={selectedRouteId}
                onSelectRoute={handleSelectRoute}
              />
            </>
          )}

          <LayerPanel
            layers={layers}
            onToggle={handleToggleLayer}
            onToggleAllFacilities={handleToggleAllFacilities}
            zoom={zoom}
            facilityTruncated={facilityTruncated}
          />

          <ScoreGuide scoreModel={scoreModel} />
        </div>
      </div>

      <RouteOverlay map={map} routes={routes ?? []} selectedRouteId={selectedRouteId} />
      <FacilityOverlay map={map} layers={layers} bounds={bounds} zoom={zoom} onTruncated={setFacilityTruncated} />
      <DensityOverlay map={map} enabled={layers.DENSITY} bounds={bounds} zoom={zoom} />
      <LocationOverlay map={map} location={currentLocation} />
      <PointOverlay map={map} point={origin} type="origin" />
      <PointOverlay map={map} point={destination} type="dest" />
      <SOSModal open={sosOpen} onClose={() => setSosOpen(false)} />
    </div>
  );
}
