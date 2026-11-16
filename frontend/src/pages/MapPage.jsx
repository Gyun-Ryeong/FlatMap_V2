import { useState, useRef, useCallback, useEffect } from 'react';
import Header from '../components/Header';
import AccountButton from '../components/AccountButton';
import KakaoMap from '../components/KakaoMap';
import SearchBar from '../components/SearchBar';
import RouteOptions from '../components/Panel/RouteOptions';
import LayerPanel from '../components/Panel/LayerPanel';
import SteepSlopeList from '../components/Panel/SteepSlopeList';
import PlaceShortcuts from '../components/Panel/PlaceShortcuts';
import ScoreGuide from '../components/Panel/ScoreGuide';
import RagEntry from '../components/Panel/RagEntry';
import RouteResult from '../components/Route/RouteResult';
import RouteDetail from '../components/Route/RouteDetail';
import WeatherMini from '../components/Weather/WeatherMini';
import RouteOverlay from '../components/Map/RouteOverlay';
import FacilityOverlay from '../components/Map/FacilityOverlay';
import DensityOverlay from '../components/Map/DensityOverlay';
import SteepSlopeOverlay from '../components/Map/SteepSlopeOverlay';
import ZoneOverlay from '../components/Map/ZoneOverlay';
import FavoriteOverlay from '../components/Map/FavoriteOverlay';
import LocationOverlay from '../components/Map/LocationOverlay';
import PointOverlay from '../components/Map/PointOverlay';
import DensityNotice from '../components/DensityNotice';
import SOSModal from '../components/SOSModal';
import useRoute from '../hooks/useRoute';
import useCurrentLocation from '../hooks/useCurrentLocation';
import useDensityNotice from '../hooks/useDensityNotice';
import useFavorites from '../hooks/useFavorites';
import useDetour from '../hooks/useDetour';
import { fetchWeather } from '../api/mapData';
import {
  getSearchHistory, addSearchHistory, removeSearchHistory, clearSearchHistory,
} from '../utils/searchHistory';
import { pickRouteByPriority, PRIORITY_OPTIONS } from '../utils/routePriority';
import { loadWalkMode, saveWalkMode, walkModeLabel } from '../utils/walkMode';
import { ZONE_KEYS } from '../utils/zoneLayers';

const DEFAULT_LAYERS = {
  CCTV: true, SECURITY_LIGHT: true, POLICE: true, DENSITY: false, STEEP: false,
  ACCIDENT: false, PROTECTED: false, WELFARE: false, SHELTER: false, SENIOR: false,
};
const DEFAULT_CENTER = { lat: 37.4201, lng: 127.1265 }; // 성남시청 (위치를 못 얻을 때의 날씨 기준점)
const FACILITY_KEYS = ['CCTV', 'SECURITY_LIGHT', 'POLICE'];
const BOUNDS_DEBOUNCE_MS = 500;

export default function MapPage({ onHome }) {
  const [map, setMap] = useState(null);
  const [mapFailed, setMapFailed] = useState(false);
  const [origin, setOrigin] = useState(null);
  const [destination, setDestination] = useState(null);
  const [originPlace, setOriginPlace] = useState(null);
  const [destPlace, setDestPlace] = useState(null);
  const [originLabel, setOriginLabel] = useState(undefined);
  const [destLabel, setDestLabel] = useState(undefined);
  const [clickMode, setClickMode] = useState(null);
  const [selectedRouteId, setSelectedRouteId] = useState(null);
  const [resultsMode, setResultsMode] = useState(false);
  const [priority, setPriority] = useState('safety');
  const [walkMode, setWalkMode] = useState(loadWalkMode);
  const [layers, setLayers] = useState(DEFAULT_LAYERS);
  const [facilityTruncated, setFacilityTruncated] = useState(false);
  const [bounds, setBounds] = useState(null);
  const [zoom, setZoom] = useState(null);
  const [sheetExpanded, setSheetExpanded] = useState(true);
  const [sosOpen, setSosOpen] = useState(false);
  const [steepCount, setSteepCount] = useState(null);
  const [zoneCounts, setZoneCounts] = useState({});
  const [history, setHistory] = useState(getSearchHistory);
  const [mapWeather, setMapWeather] = useState(null);

  const boundsTimerRef = useRef(null);
  const initialCenteredRef = useRef(false);
  const activeFieldRef = useRef(null);
  const weatherFetchedRef = useRef(false);
  const priorityRef = useRef(priority);
  priorityRef.current = priority;

  const { routes, scoreModel, loading, error, fetchRoutes, clearRoutes } = useRoute();
  const { location: currentLocation, error: locationError } = useCurrentLocation();
  const { notice: densityNotice, dismiss: dismissDensityNotice } = useDensityNotice(currentLocation);
  const { isLoggedIn, favorites, isFavorited, add: addFavorite, remove: removeFavorite } = useFavorites();
  const selectedRoute = (routes ?? []).find((r) => r.routeId === selectedRouteId) ?? null;
  // 화면의 경로 점수는 검색 시점의 보행 유형으로 계산된 것이다 — 우회 비교도 서버가 알려 준 그 유형을 쓴다.
  const resultWalkMode = scoreModel?.walkMode ?? walkMode;
  const { detour, requestDetour } = useDetour(selectedRoute, origin, destination, resultWalkMode);

  // 지도 위 날씨 칩: 첫 위치(없으면 기본 위치) 기준으로 한 번만 조회한다. 경로별 날씨는 경로 응답에 들어 있다.
  useEffect(() => {
    if (weatherFetchedRef.current) return undefined;
    const fallbackTimer = setTimeout(() => {
      if (weatherFetchedRef.current) return;
      weatherFetchedRef.current = true;
      fetchWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng).then(setMapWeather);
    }, 4000);
    if (currentLocation) {
      weatherFetchedRef.current = true;
      clearTimeout(fallbackTimer);
      fetchWeather(currentLocation.lat, currentLocation.lng).then(setMapWeather);
    }
    return () => clearTimeout(fallbackTimer);
  }, [currentLocation]);

  // 경로가 도착하면 선택한 기준(안전 우선/균형/빠른 길/오르막 최소)에 맞는 경로를 고르고 결과 뷰로 전환
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

  // place 는 장소 검색 결과(이름·주소 포함)일 때만 넘어온다. 검색한 장소는 최근 검색에 남긴다.
  const handleSelectOrigin = useCallback((lat, lng, label, place) => {
    setOrigin({ lat, lng });
    setOriginPlace({ name: label ?? '', lat, lng, address: place?.address ?? '' });
    if (label) setOriginLabel(label);
    if (place) setHistory(addSearchHistory(place));
    moveCenter(lat, lng);
  }, [moveCenter]);

  const handleSelectDestination = useCallback((lat, lng, label, place) => {
    setDestination({ lat, lng });
    setDestPlace({ name: label ?? '', lat, lng, address: place?.address ?? '' });
    if (label) setDestLabel(label);
    if (place) setHistory(addSearchHistory(place));
    moveCenter(lat, lng);
  }, [moveCenter]);

  // 즐겨찾기·최근 검색을 누르면 포커스된 칸(출발지 칸이면 출발지, 아니면 도착지)에 넣는다.
  const handlePickShortcut = (place) => {
    if (activeFieldRef.current === 'origin') {
      handleSelectOrigin(place.lat, place.lng, place.name, place);
    } else {
      handleSelectDestination(place.lat, place.lng, place.name, place);
    }
  };

  const renderFavoriteStar = (fieldKey) => {
    if (!isLoggedIn) return null;
    const place = fieldKey === 'origin' ? originPlace : destPlace;
    const favorited = isFavorited(place);
    return (
      <button
        type="button"
        className={`favorite-star-btn${favorited ? ' active' : ''}`}
        disabled={!place || favorited}
        onClick={() => addFavorite(place)}
        title={favorited ? '이미 즐겨찾기에 있음' : '즐겨찾기 추가'}
        aria-label={favorited ? '이미 즐겨찾기에 있음' : '즐겨찾기에 추가'}
      >
        ★
      </button>
    );
  };

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
    fetchRoutes(origin, destination, walkMode);
  };

  const handleChangeWalkMode = (mode) => {
    setWalkMode(mode);
    saveWalkMode(mode);
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

  const handleToggleAllZones = () => {
    setLayers((current) => {
      const allOn = ZONE_KEYS.every((k) => current[k]);
      return { ...current, ...Object.fromEntries(ZONE_KEYS.map((k) => [k, !allOn])) };
    });
  };

  const handleZoneLoaded = useCallback((key, count) => {
    setZoneCounts((current) => ({ ...current, [key]: count }));
  }, []);

  // 오르막길 구간 목록에서 항목을 누르면 그 위치로 이동하고 급경사지 경고 레이어를 켠다.
  const handlePickSteep = (item) => {
    if (!map || item.latitude == null || item.longitude == null) return;
    map.setCenter(new window.kakao.maps.LatLng(item.latitude, item.longitude));
    map.setLevel(3);
    setLayers((current) => ({ ...current, STEEP: true }));
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
      <Header
        mapReady={!!map}
        mapFailed={mapFailed}
        onHome={onHome}
        onSOS={() => setSosOpen(true)}
        accountSlot={<AccountButton />}
      />

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

        {/* 현재 위치 날씨 */}
        <WeatherMini weather={mapWeather} />

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
                  onFieldFocus={(key) => { activeFieldRef.current = key; }}
                  fieldExtras={renderFavoriteStar}
                />

                <PlaceShortcuts
                  isLoggedIn={isLoggedIn}
                  favorites={favorites}
                  onRemoveFavorite={removeFavorite}
                  history={history}
                  onRemoveHistory={(id) => setHistory(removeSearchHistory(id))}
                  onClearHistory={() => setHistory(clearSearchHistory())}
                  onPick={handlePickShortcut}
                />

                <RouteOptions priority={priority} onChangePriority={setPriority} walkMode={walkMode} onChangeWalkMode={handleChangeWalkMode} />

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
                <span className="applied-conditions-chip">보행 유형: {walkModeLabel(resultWalkMode)}</span>
              </div>

              <RouteResult
                routes={routes ?? []}
                selectedRouteId={selectedRouteId}
                onSelectRoute={handleSelectRoute}
                renderDetail={(route) => (
                  <RouteDetail route={route} detour={detour} onRequestDetour={requestDetour} />
                )}
              />
            </>
          )}

          <LayerPanel
            layers={layers}
            onToggle={handleToggleLayer}
            onToggleAllFacilities={handleToggleAllFacilities}
            onToggleAllZones={handleToggleAllZones}
            zoom={zoom}
            facilityTruncated={facilityTruncated}
            steepCount={steepCount}
            zoneCounts={zoneCounts}
          />

          <SteepSlopeList onPick={handlePickSteep} />

          <ScoreGuide scoreModel={scoreModel} />

          {/* RAG 는 개발 중: 서버가 켜짐+연결됨일 때만 보인다(기본은 아무것도 렌더하지 않음) */}
          <RagEntry />
        </div>
      </div>

      <RouteOverlay
        map={map}
        routes={routes ?? []}
        selectedRouteId={selectedRouteId}
        detour={detour.status === 'done' ? detour.data : null}
      />
      <FacilityOverlay map={map} layers={layers} bounds={bounds} zoom={zoom} onTruncated={setFacilityTruncated} />
      <DensityOverlay map={map} enabled={layers.DENSITY} bounds={bounds} zoom={zoom} />
      <SteepSlopeOverlay map={map} enabled={layers.STEEP} onLoaded={setSteepCount} />
      <ZoneOverlay map={map} layers={layers} bounds={bounds} zoom={zoom} onLoaded={handleZoneLoaded} />
      <FavoriteOverlay map={map} favorites={favorites} />
      <LocationOverlay map={map} location={currentLocation} />
      <PointOverlay map={map} point={origin} type="origin" />
      <PointOverlay map={map} point={destination} type="dest" />
      <SOSModal open={sosOpen} onClose={() => setSosOpen(false)} />
    </div>
  );
}
