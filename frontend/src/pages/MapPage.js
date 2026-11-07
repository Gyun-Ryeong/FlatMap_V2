import React, { useEffect, useRef, useState, useCallback } from 'react';
import { loadKakaoSDK } from '../utils/kakaoLoader';
import { useAuth } from '../context/AuthContext';
import { getFavorites, addFavorite, removeFavorite } from '../api/favorites';
import { getSearchHistory, addSearchHistory, removeSearchHistory, clearSearchHistory } from '../utils/searchHistory';
import './MapPage.css';

const API_BASE = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

// 위험구간 색상 상수
const RISK_COLORS = {
  SAFE: '#03C75A',
  LOW: '#FF9800',
  MEDIUM: '#FF5722',
  HIGH: '#F44336',
  VERY_HIGH: '#F44336',
};

const RISK_STROKE_WEIGHT = { SAFE: 5, LOW: 7, MEDIUM: 7, HIGH: 8, VERY_HIGH: 8 };

const LAYER_COLORS = {
  accidents: '#F44336', cctv: '#757575',
  protectedZones: '#4CAF50', welfare: '#2196F3', shelters: '#FF9800', senior: '#9C27B0',
};

const TABS = [
  { id: 'route', label: '경로찾기', icon: '🗺️' },
  { id: 'uphill', label: '오르막길', icon: '⛰️' },
  { id: 'safety', label: '안전정보', icon: '⚠️' },
];

const DEFAULT_CENTER = { lat: 37.4201, lng: 127.1265 }; // 성남시청

function MapPage() {
  const { user } = useAuth();
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const polylinesRef = useRef([]);
  const markersRef = useRef([]);
  const myLocationOverlayRef = useRef(null);
  const myLocationRef = useRef(null);
  const [activeTab, setActiveTab] = useState('route');
  const [panelOpen, setPanelOpen] = useState(true);
  const [kakaoReady, setKakaoReady] = useState(false);
  const [locationMsg, setLocationMsg] = useState('');
  const [defaultOrigin, setDefaultOrigin] = useState(null);
  const [mapWeather, setMapWeather] = useState(null);
  const detourPolylinesRef = useRef([]);
  const detourMarkersRef = useRef([]);
  const layerMarkersRef = useRef({ accidents: [], cctv: [], protectedZones: [], welfare: [], shelters: [], senior: [] });
  const activeLayersRef = useRef({ accidents: false, cctv: false, protectedZones: false, welfare: false, shelters: false, senior: false });
  const layerInfoOverlayRef = useRef(null);
  const dbMarkersRef = useRef([]);
  const [dbMarkersOn, setDbMarkersOn] = useState(false);
  const favMarkersRef = useRef([]);
  const [mapFavorites, setMapFavorites] = useState([]);

  const cctvClustererRef = useRef(null);

  const getMaxMarkersByZoom = useCallback(() => {
    if (!mapInstanceRef.current) return 200;
    const level = mapInstanceRef.current.getLevel();
    // 카카오맵 레벨: 1(가장 확대) ~ 14(가장 축소)
    if (level <= 3) return Infinity;
    if (level <= 5) return 500;
    if (level <= 6) return 300;
    if (level <= 7) return 100;
    return 30;
  }, []);

  const updateLayerMarkersForZoom = useCallback(() => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const maxMarkers = getMaxMarkersByZoom();

    Object.keys(activeLayersRef.current).forEach((type) => {
      if (!activeLayersRef.current[type]) return;
      if (type === 'cctv') return; // clusterer가 줌 변경 자동 처리
      const markers = layerMarkersRef.current[type] || [];
      markers.forEach((m, i) => {
        if (i < maxMarkers) {
          m.setMap(mapInstanceRef.current);
        } else {
          m.setMap(null);
        }
      });
    });
  }, [getMaxMarkersByZoom]);

  const clearLayerMarkers = useCallback((type) => {
    if (type === 'cctv' && cctvClustererRef.current) {
      cctvClustererRef.current.clear();
      cctvClustererRef.current = null;
    }
    if (!layerMarkersRef.current[type]) return;
    layerMarkersRef.current[type].forEach((m) => m.setMap(null));
    layerMarkersRef.current[type] = [];
    activeLayersRef.current[type] = false;
  }, []);

  // 즐겨찾기 장소를 지도에 금색 ★ 마커 + 이름 라벨로 표시
  const drawFavoriteMarkers = useCallback((favs) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    favMarkersRef.current.forEach((m) => m.setMap(null));
    favMarkersRef.current = [];

    favs.forEach((f) => {
      if (f.latitude == null || f.longitude == null) return;
      const wrap = document.createElement('div');
      wrap.className = 'fav-map-marker';
      const pin = document.createElement('div');
      pin.className = 'fav-map-pin';
      pin.textContent = '★';
      const label = document.createElement('div');
      label.className = 'fav-map-label';
      label.textContent = f.name || '';
      wrap.appendChild(pin);
      wrap.appendChild(label);

      const overlay = new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(f.latitude, f.longitude),
        content: wrap,
        map: mapInstanceRef.current,
        yAnchor: 1,
        zIndex: 90,
      });
      favMarkersRef.current.push(overlay);
    });
  }, []);

  useEffect(() => {
    if (!kakaoReady) return;
    drawFavoriteMarkers(mapFavorites);
  }, [mapFavorites, kakaoReady, drawFavoriteMarkers]);

  const showLayerMarkers = useCallback((type, items, emoji) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;
    clearLayerMarkers(type);

    activeLayersRef.current[type] = true;

    // CCTV — MarkerClusterer 적용
    if (type === 'cctv') {
      // 개별 마커: 기본 핀 대신 작은 원형 아이콘 사용 (다른 레이어와 톤 통일 + 크기 축소)
      const cctvIcon = 'data:image/svg+xml;utf8,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20"><circle cx="10" cy="10" r="8" fill="#757575" stroke="#fff" stroke-width="2"/></svg>'
      );
      const cctvMarkerImage = new kakao.maps.MarkerImage(
        cctvIcon,
        new kakao.maps.Size(20, 20),
        { offset: new kakao.maps.Point(10, 10) }
      );

      const kakaoMarkers = items
        .filter((item) => item.latitude && item.longitude)
        .map((item) => {
          const marker = new kakao.maps.Marker({
            position: new kakao.maps.LatLng(item.latitude, item.longitude),
            image: cctvMarkerImage,
          });
          kakao.maps.event.addListener(marker, 'click', () => {
            if (layerInfoOverlayRef.current) {
              layerInfoOverlayRef.current.setMap(null);
              layerInfoOverlayRef.current = null;
            }
            const infoEl = document.createElement('div');
            infoEl.className = 'layer-info-window';
            infoEl.innerHTML = `
              <div class="layer-info-header">
                <span>📹 ${item.name || 'CCTV'}</span>
                <button class="layer-info-close">&times;</button>
              </div>
              <div class="layer-info-body">
                ${item.address ? `<p>${item.address}</p>` : ''}
              </div>`;
            const infoOverlay = new kakao.maps.CustomOverlay({
              position: new kakao.maps.LatLng(item.latitude, item.longitude),
              content: infoEl,
              map: mapInstanceRef.current,
              yAnchor: 1.4,
              zIndex: 300,
            });
            layerInfoOverlayRef.current = infoOverlay;
            infoEl.querySelector('.layer-info-close').addEventListener('click', (ev) => {
              ev.stopPropagation();
              infoOverlay.setMap(null);
              layerInfoOverlayRef.current = null;
            });
          });
          return marker;
        });

      const clusterer = new kakao.maps.MarkerClusterer({
        map: mapInstanceRef.current,
        averageCenter: true,
        minLevel: 5,
        disableClickZoom: false,
        styles: [{
          width: '32px', height: '32px',
          background: 'rgba(117,117,117,0.85)',
          borderRadius: '50%',
          color: '#fff',
          textAlign: 'center',
          lineHeight: '32px',
          fontSize: '11px',
          fontWeight: 'bold',
        }],
      });
      clusterer.addMarkers(kakaoMarkers);
      cctvClustererRef.current = clusterer;
      layerMarkersRef.current[type] = kakaoMarkers;
      return;
    }

    // 일반 레이어 — CustomOverlay
    const bgColor = LAYER_COLORS[type] || '#666';
    const maxMarkers = getMaxMarkersByZoom();

    const newMarkers = items.map((item) => {
      if (!item.latitude || !item.longitude) return null;

      const wrap = document.createElement('div');
      wrap.className = 'layer-marker-wrap';

      const circle = document.createElement('div');
      circle.className = 'layer-marker-circle';
      circle.style.background = bgColor;
      circle.textContent = emoji;
      wrap.appendChild(circle);

      wrap.addEventListener('click', (e) => {
        e.stopPropagation();
        if (layerInfoOverlayRef.current) {
          layerInfoOverlayRef.current.setMap(null);
          layerInfoOverlayRef.current = null;
        }
        const infoEl = document.createElement('div');
        infoEl.className = 'layer-info-window';
        infoEl.innerHTML = `
          <div class="layer-info-header">
            <span>${emoji} ${item.name || '정보 없음'}</span>
            <button class="layer-info-close">&times;</button>
          </div>
          <div class="layer-info-body">
            ${item.address ? `<p>${item.address}</p>` : ''}
            ${item.phone ? `<p>📞 ${item.phone}</p>` : ''}
            ${item.type ? `<p>유형: ${item.type}</p>` : ''}
          </div>`;
        const infoOverlay = new kakao.maps.CustomOverlay({
          position: new kakao.maps.LatLng(item.latitude, item.longitude),
          content: infoEl,
          map: mapInstanceRef.current,
          yAnchor: 1.4,
          zIndex: 300,
        });
        layerInfoOverlayRef.current = infoOverlay;
        infoEl.querySelector('.layer-info-close').addEventListener('click', (ev) => {
          ev.stopPropagation();
          infoOverlay.setMap(null);
          layerInfoOverlayRef.current = null;
        });
      });

      const overlay = new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(item.latitude, item.longitude),
        content: wrap,
        yAnchor: 0.5,
        zIndex: 80,
      });
      return overlay;
    }).filter(Boolean);


    newMarkers.forEach((m, i) => {
      if (i < maxMarkers) m.setMap(mapInstanceRef.current);
    });

    layerMarkersRef.current[type] = newMarkers;
  }, [clearLayerMarkers, getMaxMarkersByZoom]);

  const showMyLocationMarker = useCallback((kakao, lat, lng) => {
    if (myLocationOverlayRef.current) {
      myLocationOverlayRef.current.setMap(null);
    }

    const content = '<div class="my-location-marker"><div class="my-location-dot"></div><div class="my-location-pulse"></div></div>';
    myLocationOverlayRef.current = new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(lat, lng),
      content,
      map: mapInstanceRef.current,
      zIndex: 150,
    });
  }, []);

  const reverseGeocode = useCallback((kakao, lat, lng) => {
    const geocoder = new kakao.maps.services.Geocoder();
    geocoder.coord2Address(lng, lat, (result, status) => {
      if (status === kakao.maps.services.Status.OK && result[0]) {
        const addr = result[0].road_address
          ? result[0].road_address.address_name
          : result[0].address.address_name;
        setDefaultOrigin({ name: addr, lat, lng });
      } else {
        setDefaultOrigin({ name: '현재 위치', lat, lng });
      }
    });
  }, []);

  const fetchMapWeather = useCallback((lat, lng) => {
    fetch(`${API_BASE}/weather?lat=${lat}&lng=${lng}`)
      .then((res) => res.ok ? res.json() : null)
      .then((data) => { if (data) setMapWeather(data); })
      .catch(() => {});
  }, []);

  const requestCurrentLocation = useCallback((kakao) => {
    if (!navigator.geolocation) {
      setLocationMsg('브라우저가 위치 서비스를 지원하지 않습니다.');
      setTimeout(() => setLocationMsg(''), 3000);
      fetchMapWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        myLocationRef.current = { lat, lng };

        mapInstanceRef.current.setCenter(new kakao.maps.LatLng(lat, lng));
        mapInstanceRef.current.setLevel(4);
        showMyLocationMarker(kakao, lat, lng);
        reverseGeocode(kakao, lat, lng);
        fetchMapWeather(lat, lng);
      },
      (err) => {
        console.warn('위치 권한 거부:', err.message);
        myLocationRef.current = DEFAULT_CENTER;
        setLocationMsg('위치 권한이 거부되어 기본 위치로 설정됩니다.');
        setTimeout(() => setLocationMsg(''), 3000);
        reverseGeocode(kakao, DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
        fetchMapWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
      },
      { enableHighAccuracy: true, timeout: 5000 }
    );
  }, [showMyLocationMarker, reverseGeocode, fetchMapWeather]);

  useEffect(() => {
    let cancelled = false;

    loadKakaoSDK().then((kakao) => {
      if (cancelled || mapInstanceRef.current) return;
      mapInstanceRef.current = new kakao.maps.Map(mapRef.current, {
        center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
        level: 5,
      });
      setKakaoReady(true);

      // 컨테이너 크기 확정 후 지도 재배치 (가로 찌그러짐 방지)
      requestAnimationFrame(() => {
        const center = mapInstanceRef.current.getCenter();
        mapInstanceRef.current.relayout();
        mapInstanceRef.current.setCenter(center);
      });

      // 줌 변경 시 레이어 마커 밀도 갱신
      kakao.maps.event.addListener(mapInstanceRef.current, 'zoom_changed', () => {
        updateLayerMarkersForZoom();
      });

      // 현재 위치 가져오기
      requestCurrentLocation(kakao);
    });

    return () => { cancelled = true; };
  }, [updateLayerMarkersForZoom, requestCurrentLocation]);

  // 브라우저 창 크기 변경 시 지도 재배치
  useEffect(() => {
    const onResize = () => {
      if (!mapInstanceRef.current) return;
      const center = mapInstanceRef.current.getCenter();
      mapInstanceRef.current.relayout();
      mapInstanceRef.current.setCenter(center);
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const handleMyLocation = useCallback(() => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        myLocationRef.current = { lat, lng };
        mapInstanceRef.current.setCenter(new kakao.maps.LatLng(lat, lng));
        mapInstanceRef.current.setLevel(4);
        showMyLocationMarker(kakao, lat, lng);
      },
      () => {
        if (myLocationRef.current) {
          const { lat, lng } = myLocationRef.current;
          mapInstanceRef.current.setCenter(new kakao.maps.LatLng(lat, lng));
          mapInstanceRef.current.setLevel(4);
        }
        setLocationMsg('위치를 가져올 수 없습니다.');
        setTimeout(() => setLocationMsg(''), 3000);
      },
      { enableHighAccuracy: true, timeout: 5000 }
    );
  }, [showMyLocationMarker]);

  const clearDetour = useCallback(() => {
    detourPolylinesRef.current.forEach((p) => p.setMap(null));
    detourPolylinesRef.current = [];
    detourMarkersRef.current.forEach((m) => m.setMap(null));
    detourMarkersRef.current = [];
  }, []);


  const clearRoute = useCallback(() => {
    polylinesRef.current.forEach((p) => p.setMap(null));
    polylinesRef.current = [];
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];
    clearDetour();
  }, [clearDetour]);

  const drawRoute = useCallback((coords, riskSections = []) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    clearRoute();

    const path = coords.map((c) => new kakao.maps.LatLng(c.lat, c.lng));
    if (path.length === 0) return;

    // 위험구간이 없으면 기존처럼 초록색 한 줄
    if (!riskSections || riskSections.length === 0) {
      const line = new kakao.maps.Polyline({
        map: mapInstanceRef.current,
        path,
        strokeWeight: 5,
        strokeColor: RISK_COLORS.SAFE,
        strokeOpacity: 0.8,
        strokeStyle: 'solid',
      });
      polylinesRef.current.push(line);
    } else {
      // 위험구간 인덱스 범위 계산 (nearestRouteIdx 기준 앞뒤 SPREAD 좌표)
      const SPREAD = 8;
      const riskRanges = riskSections
        .map((rs) => ({
          start: Math.max(0, rs.nearestRouteIdx - SPREAD),
          end: Math.min(coords.length - 1, rs.nearestRouteIdx + SPREAD),
          riskLevel: rs.riskLevel,
        }))
        .sort((a, b) => a.start - b.start);

      // 겹치는 구간 병합
      const merged = [];
      for (const r of riskRanges) {
        if (merged.length > 0 && r.start <= merged[merged.length - 1].end + 1) {
          const last = merged[merged.length - 1];
          last.end = Math.max(last.end, r.end);
          // 더 높은 위험도 유지
          const priority = ['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'];
          if (priority.indexOf(r.riskLevel) > priority.indexOf(last.riskLevel)) {
            last.riskLevel = r.riskLevel;
          }
        } else {
          merged.push({ ...r });
        }
      }

      // 구간별 폴리라인 그리기
      let cursor = 0;
      for (const range of merged) {
        // 안전 구간 (cursor ~ range.start)
        if (cursor < range.start) {
          const safePath = path.slice(cursor, range.start + 1);
          if (safePath.length >= 2) {
            const safeLine = new kakao.maps.Polyline({
              map: mapInstanceRef.current,
              path: safePath,
              strokeWeight: 5,
              strokeColor: RISK_COLORS.SAFE,
              strokeOpacity: 0.8,
              strokeStyle: 'solid',
            });
            polylinesRef.current.push(safeLine);
          }
        }
        // 위험 구간
        const riskPath = path.slice(range.start, range.end + 1);
        if (riskPath.length >= 2) {
          const riskLine = new kakao.maps.Polyline({
            map: mapInstanceRef.current,
            path: riskPath,
            strokeWeight: RISK_STROKE_WEIGHT[range.riskLevel] || 7,
            strokeColor: RISK_COLORS[range.riskLevel] || RISK_COLORS.MEDIUM,
            strokeOpacity: 0.9,
            strokeStyle: 'solid',
          });
          polylinesRef.current.push(riskLine);
        }
        cursor = range.end;
      }
      // 마지막 안전 구간
      if (cursor < path.length - 1) {
        const safePath = path.slice(cursor, path.length);
        if (safePath.length >= 2) {
          const safeLine = new kakao.maps.Polyline({
            map: mapInstanceRef.current,
            path: safePath,
            strokeWeight: 5,
            strokeColor: RISK_COLORS.SAFE,
            strokeOpacity: 0.8,
            strokeStyle: 'solid',
          });
          polylinesRef.current.push(safeLine);
        }
      }

    }

    // 출발/도착 마커
    if (path.length >= 2) {
      const startMarker = new kakao.maps.Marker({
        map: mapInstanceRef.current,
        position: path[0],
        title: '출발',
      });
      const endMarker = new kakao.maps.Marker({
        map: mapInstanceRef.current,
        position: path[path.length - 1],
        title: '도착',
      });
      markersRef.current.push(startMarker, endMarker);
    }

    // 경로에 맞게 지도 범위 조정
    const bounds = new kakao.maps.LatLngBounds();
    path.forEach((p) => bounds.extend(p));
    mapInstanceRef.current.setBounds(bounds);

  }, [clearRoute]);

  const setRouteOpacity = useCallback((opacity) => {
    polylinesRef.current.forEach((p) => p.setOptions({ strokeOpacity: opacity }));
  }, []);

  const drawDetourRoute = useCallback((coords, riskSections = []) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;
    clearDetour();

    const path = coords.map((c) => new kakao.maps.LatLng(c.lat, c.lng));
    if (path.length === 0) return;

    // 원래 경로 투명도 낮춤
    setRouteOpacity(0.4);

    if (!riskSections || riskSections.length === 0) {
      // 안전한 우회 경로: 파란 점선
      const line = new kakao.maps.Polyline({
        map: mapInstanceRef.current,
        path,
        strokeWeight: 6,
        strokeColor: '#2196F3',
        strokeOpacity: 0.9,
        strokeStyle: 'shortdash',
      });
      detourPolylinesRef.current.push(line);
    } else {
      // 우회 경로에도 위험구간이 있는 경우
      const SPREAD = 8;
      const riskRanges = riskSections
        .map((rs) => ({
          start: Math.max(0, rs.nearestRouteIdx - SPREAD),
          end: Math.min(coords.length - 1, rs.nearestRouteIdx + SPREAD),
          riskLevel: rs.riskLevel,
        }))
        .sort((a, b) => a.start - b.start);

      const merged = [];
      for (const r of riskRanges) {
        if (merged.length > 0 && r.start <= merged[merged.length - 1].end + 1) {
          const last = merged[merged.length - 1];
          last.end = Math.max(last.end, r.end);
        } else {
          merged.push({ ...r });
        }
      }

      let cursor = 0;
      for (const range of merged) {
        if (cursor < range.start) {
          const safePath = path.slice(cursor, range.start + 1);
          if (safePath.length >= 2) {
            detourPolylinesRef.current.push(new kakao.maps.Polyline({
              map: mapInstanceRef.current, path: safePath,
              strokeWeight: 6, strokeColor: '#2196F3', strokeOpacity: 0.9, strokeStyle: 'shortdash',
            }));
          }
        }
        const riskPath = path.slice(range.start, range.end + 1);
        if (riskPath.length >= 2) {
          detourPolylinesRef.current.push(new kakao.maps.Polyline({
            map: mapInstanceRef.current, path: riskPath,
            strokeWeight: 7, strokeColor: '#FF5722', strokeOpacity: 0.9, strokeStyle: 'shortdash',
          }));
        }
        cursor = range.end;
      }
      if (cursor < path.length - 1) {
        const safePath = path.slice(cursor);
        if (safePath.length >= 2) {
          detourPolylinesRef.current.push(new kakao.maps.Polyline({
            map: mapInstanceRef.current, path: safePath,
            strokeWeight: 6, strokeColor: '#2196F3', strokeOpacity: 0.9, strokeStyle: 'shortdash',
          }));
        }
      }
    }

    // 우회 경로 출발/도착 마커
    if (path.length >= 2) {
      const bounds = new kakao.maps.LatLngBounds();
      path.forEach((p) => bounds.extend(p));
      polylinesRef.current.forEach((pl) => {
        const plPath = pl.getPath();
        plPath.forEach((p) => bounds.extend(p));
      });
      mapInstanceRef.current.setBounds(bounds);
    }

  }, [clearDetour, setRouteOpacity]);

  const selectRoute = useCallback((type) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (type === 'original') {
      polylinesRef.current.forEach((p) => { p.setMap(map); p.setOptions({ strokeOpacity: 0.8 }); });
      markersRef.current.forEach((m) => m.setMap(map));
      detourPolylinesRef.current.forEach((p) => p.setMap(null));
      detourMarkersRef.current.forEach((m) => m.setMap(null));
    } else {
      polylinesRef.current.forEach((p) => p.setMap(null));
      detourPolylinesRef.current.forEach((p) => { p.setMap(map); p.setOptions({ strokeOpacity: 0.9 }); });
      detourMarkersRef.current.forEach((m) => m.setMap(map));
      markersRef.current.forEach((m) => m.setMap(map));
    }
  }, []);

  const moveToLocation = useCallback((lat, lng, name) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;
    const pos = new kakao.maps.LatLng(lat, lng);
    mapInstanceRef.current.setCenter(pos);
    mapInstanceRef.current.setLevel(3);

    // 기존 UphillPanel 마커 제거 (태그로 구분)
    markersRef.current = markersRef.current.filter((m) => {
      if (m._uphillMarker) { m.setMap(null); return false; }
      return true;
    });

    const marker = new kakao.maps.Marker({ map: mapInstanceRef.current, position: pos, title: name || '' });
    marker._uphillMarker = true;
    markersRef.current.push(marker);

    if (name) {
      const infoWindow = new kakao.maps.InfoWindow({ content: `<div style="padding:5px;font-size:12px;">${name}</div>` });
      infoWindow.open(mapInstanceRef.current, marker);
    }
  }, []);

  const toggleDbMarkers = useCallback(async () => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    if (dbMarkersOn) {
      dbMarkersRef.current.forEach((m) => m.setMap(null));
      dbMarkersRef.current = [];
      setDbMarkersOn(false);
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/steep-slope`);
      const items = await res.json();

      const riskColor = { VERY_HIGH: '#E53935', HIGH: '#E53935', MEDIUM: '#E53935', LOW: '#E53935' };
      const riskLabel = { VERY_HIGH: '위험', HIGH: '위험', MEDIUM: '주의', LOW: '양호' };
      const addressOf = (it) => [it.sigungu, it.eupmyeon, it.jibun].filter(Boolean).join(' ');

      const newMarkers = items.flatMap((item) => {
        if (!item.latitude || !item.longitude) return [];
        // 위험도 미측정은 회색으로 구분 — 안전하다는 인상을 주지 않는다.
        const color = riskColor[item.riskLevel] || '#9E9E9E';
        const pos = new kakao.maps.LatLng(item.latitude, item.longitude);

        const showInfo = () => {
          if (layerInfoOverlayRef.current) {
            layerInfoOverlayRef.current.setMap(null);
            layerInfoOverlayRef.current = null;
          }
          const infoEl = document.createElement('div');
          infoEl.className = 'layer-info-window';
          infoEl.innerHTML = `
            <div class="layer-info-header">
              <span>${item.isMountain ? '🏔️' : '⛰️'} ${item.name}</span>
              <button class="layer-info-close">&times;</button>
            </div>
            <div class="layer-info-body">
              ${item.isMountain ? '<p class="terrain-line">산지형 (산 지번·임야)</p>' : ''}
              <p>경사도: ${item.grade != null ? item.grade.toFixed(1) + '°' : '미측정'}</p>
              <p>위험도: ${riskLabel[item.riskLevel] || '미측정'}</p>
              ${addressOf(item) ? `<p>주소: ${addressOf(item)}</p>` : ''}
              <p style="font-size:11px;color:#888;">DB 데이터 (${item.source || ''})</p>
            </div>`;
          const infoOverlay = new kakao.maps.CustomOverlay({
            position: pos, content: infoEl,
            map: mapInstanceRef.current, yAnchor: 1.4, zIndex: 300,
          });
          layerInfoOverlayRef.current = infoOverlay;
          infoEl.querySelector('.layer-info-close').addEventListener('click', (ev) => {
            ev.stopPropagation();
            infoOverlay.setMap(null);
            layerInfoOverlayRef.current = null;
          });
        };

        const circle = new kakao.maps.Circle({
          center: pos, radius: 50,
          strokeWeight: 0, strokeOpacity: 0,
          fillColor: color, fillOpacity: 0.25,
          map: mapInstanceRef.current, zIndex: 190,
        });
        kakao.maps.event.addListener(circle, 'click', showInfo);

        const wrap = document.createElement('div');
        wrap.style.cssText = 'cursor:pointer;';
        const dot = document.createElement('div');
        dot.style.cssText = [
          'width:18px;height:18px;border-radius:50%;',
          `background:${color};border:3px solid #fff;`,
          'box-shadow:0 2px 8px rgba(0,0,0,0.35);',
        ].join('');
        wrap.appendChild(dot);
        wrap.addEventListener('click', (e) => { e.stopPropagation(); showInfo(); });

        const pinOverlay = new kakao.maps.CustomOverlay({
          position: pos, content: wrap,
          map: mapInstanceRef.current, yAnchor: 0.5, zIndex: 191,
        });

        return [circle, pinOverlay];
      });

      dbMarkersRef.current = newMarkers;
      setDbMarkersOn(true);
    } catch (err) {
      console.error('DB 마커 로드 실패:', err);
    }
  }, [dbMarkersOn]);

  const handleTabClick = (tabId) => {
    if (activeTab === tabId && panelOpen) {
      setPanelOpen(false);
    } else {
      setActiveTab(tabId);
      setPanelOpen(true);
    }
  };

  return (
    <div className={`map-layout ${panelOpen ? '' : 'sheet-collapsed'}`}>
      <div ref={mapRef} className="map-container" />

      {/* 모바일 전용 상단 검색바 */}
      <div className="mobile-search-bar">
        <button
          type="button"
          className="mobile-search-field"
          onClick={() => { setActiveTab('route'); setPanelOpen(true); }}
        >
          <span className="mobile-search-icon">🔍</span>
          <span className="mobile-search-placeholder">장소, 주소 검색</span>
        </button>
        <button
          type="button"
          className="mobile-profile-btn"
          onClick={() => { setActiveTab('profile'); setPanelOpen(true); }}
          title="프로필"
        >
          {user?.profileImageUrl
            ? <img src={user.profileImageUrl} alt="" className="mobile-profile-img" />
            : <span className="mobile-profile-fallback">👤</span>}
        </button>
      </div>

      {/* 내 위치 버튼 */}
      <button className="my-location-btn" onClick={handleMyLocation} title="내 위치">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="4" />
          <line x1="12" y1="2" x2="12" y2="6" />
          <line x1="12" y1="18" x2="12" y2="22" />
          <line x1="2" y1="12" x2="6" y2="12" />
          <line x1="18" y1="12" x2="22" y2="12" />
        </svg>
      </button>

      {/* 날씨 미니 오버레이 */}
      {mapWeather && <WeatherMiniOverlay weather={mapWeather} />}

      {/* 위치 안내 메시지 */}
      {locationMsg && <div className="location-toast">{locationMsg}</div>}

      {/* 왼쪽 패널 */}
      <div className={`side-panel ${panelOpen ? 'open' : 'closed'}`}>
        {/* 헤더 */}
        <div className="panel-header">
          <div className="panel-logo">
            <span className="logo-icon">⛰️</span>
            <span className="logo-text">경기 안전 내비</span>
          </div>
          <AuthPanel />
          <button className="panel-toggle" onClick={() => setPanelOpen(!panelOpen)}>
            {panelOpen ? '◀' : '▶'}
          </button>
        </div>

        {/* 탭 메뉴 */}
        <div className="tab-menu">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              className={`tab-btn ${activeTab === tab.id && panelOpen ? 'active' : ''}`}
              onClick={() => handleTabClick(tab.id)}
            >
              <span className="tab-icon">{tab.icon}</span>
              <span className="tab-label">{tab.label}</span>
            </button>
          ))}
        </div>

        {/* 탭 콘텐츠 — display로 전환 (unmount 방지, state 유지) */}
        <div className="tab-content">
          <div style={{ display: activeTab === 'route' ? '' : 'none' }}>
            <RoutePanel kakaoReady={kakaoReady} onRouteFound={drawRoute} onDetourFound={drawDetourRoute} onSelectRoute={selectRoute} defaultOrigin={defaultOrigin} onFavoritesChange={setMapFavorites} />
          </div>
          <div style={{ display: activeTab === 'uphill' ? '' : 'none' }}>
            <UphillPanel onItemClick={moveToLocation} onToggleDb={toggleDbMarkers} dbMarkersOn={dbMarkersOn} />
          </div>
          <div style={{ display: activeTab === 'safety' ? '' : 'none' }}>
            <SafetyPanel onToggleLayer={showLayerMarkers} onClearLayer={clearLayerMarkers} />
          </div>
          <div style={{ display: activeTab === 'profile' ? '' : 'none' }}>
            <MobileProfilePanel />
          </div>
        </div>
      </div>
    </div>
  );
}

function AuthPanel() {
  const { user, loading, isLoggedIn, login, logout } = useAuth();

  if (loading) return null;

  if (!isLoggedIn) {
    return (
      <button className="kakao-login-btn" onClick={login}>
        카카오 로그인
      </button>
    );
  }

  return (
    <div className="kakao-user-info">
      <span className="kakao-user-nickname">{user.nickname || '사용자'}</span>
      <button className="kakao-logout-btn" onClick={logout}>로그아웃</button>
    </div>
  );
}

function MobileProfilePanel() {
  const { user, loading, isLoggedIn, login, logout } = useAuth();

  if (loading) return null;

  if (!isLoggedIn) {
    return (
      <div className="mobile-profile-panel">
        <span className="mobile-profile-avatar-fallback">👤</span>
        <p className="mobile-profile-desc">로그인하고 즐겨찾기 등<br />더 많은 기능을 이용해보세요</p>
        <button className="kakao-login-btn large" onClick={login}>카카오 로그인</button>
      </div>
    );
  }

  return (
    <div className="mobile-profile-panel">
      {user.profileImageUrl
        ? <img src={user.profileImageUrl} alt="" className="mobile-profile-avatar" />
        : <span className="mobile-profile-avatar-fallback">👤</span>}
      <p className="mobile-profile-name">{user.nickname || '사용자'}</p>
      <button className="kakao-logout-btn large" onClick={logout}>로그아웃</button>
    </div>
  );
}

function FavoritesModal({ favorites, onSelect, onRemove, onClose }) {
  return (
    <div className="favorites-modal-backdrop" onClick={onClose}>
      <div className="favorites-modal" onClick={(e) => e.stopPropagation()}>
        <div className="favorites-modal-header">
          <h3>즐겨찾기 ({favorites.length})</h3>
          <button type="button" className="favorites-modal-close" onClick={onClose}>×</button>
        </div>
        <div className="favorites-modal-list">
          {favorites.length === 0 ? (
            <p className="favorites-modal-empty">저장된 즐겨찾기가 없습니다.</p>
          ) : (
            favorites.map((fav) => (
              <div key={fav.id} className="favorites-modal-item">
                <button type="button" className="favorites-modal-item-main" onClick={() => onSelect(fav)}>
                  <span className="favorites-modal-item-name">★ {fav.name}</span>
                  {fav.address && <span className="favorites-modal-item-address">{fav.address}</span>}
                </button>
                <button type="button" className="favorites-modal-item-remove" onClick={() => onRemove(fav.id)}>삭제</button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function PlaceSearchInput({ placeholder, value, onChange, onClear, onSelect, dotClass, kakaoReady, trailing, field, onFieldFocus, onFieldBlur }) {
  const [results, setResults] = useState([]);
  const [showResults, setShowResults] = useState(false);
  const debounceRef = useRef(null);
  const justSelectedRef = useRef(false);

  const searchPlace = useCallback((keyword) => {
    if (!keyword.trim() || !kakaoReady || !window.kakao?.maps?.services) {
      setResults([]);
      setShowResults(false);
      return;
    }

    const ps = new window.kakao.maps.services.Places();
    ps.keywordSearch(keyword, (data, status) => {
      if (status === window.kakao.maps.services.Status.OK) {
        setResults(data.slice(0, 5));
        setShowResults(true);
      } else if (status === window.kakao.maps.services.Status.ZERO_RESULT) {
        setResults([]);
        setShowResults(true);
      } else {
        console.error('장소 검색 실패:', status);
        setResults([]);
        setShowResults(false);
      }
    });
  }, [kakaoReady]);

  const handleChange = (e) => {
    const val = e.target.value;
    if (justSelectedRef.current) {
      justSelectedRef.current = false;
      return;
    }
    onChange(val);
    onClear();
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => searchPlace(val), 300);
  };

  const handleSelect = (place) => {
    justSelectedRef.current = true;
    onSelect({
      name: place.place_name,
      lng: parseFloat(place.x),
      lat: parseFloat(place.y),
      address: place.address_name,
    });
    onChange(place.place_name);
    setShowResults(false);
  };

  return (
    <div className="place-search-wrapper">
      <div className="route-input-row">
        <span className={`route-dot ${dotClass}`} />
        <input
          type="text"
          placeholder={placeholder}
          className="route-input"
          value={value}
          onChange={handleChange}
          onFocus={() => { onFieldFocus?.(field); if (results.length > 0) setShowResults(true); }}
          onBlur={() => { onFieldBlur?.(field); setTimeout(() => setShowResults(false), 200); }}
        />
        {trailing}
      </div>
      {showResults && (
        <ul className="place-results">
          {results.length > 0 ? (
            results.map((place) => (
              <li key={place.id} className="place-result-item" onMouseDown={() => handleSelect(place)}>
                <span className="place-name">{place.place_name}</span>
                <span className="place-address">{place.address_name}</span>
              </li>
            ))
          ) : (
            <li className="place-result-empty">검색 결과가 없습니다</li>
          )}
        </ul>
      )}
    </div>
  );
}

const toKorError = (err) => {
  if (!err?.message || err.message === 'Failed to fetch' || err instanceof TypeError) {
    return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.';
  }
  return err.message;
};

function RoutePanel({ kakaoReady, onRouteFound, onDetourFound, onSelectRoute, defaultOrigin, onFavoritesChange }) {
  const { isLoggedIn } = useAuth();
  const [originText, setOriginText] = useState('');
  const [destText, setDestText] = useState('');
  const [originPlace, setOriginPlace] = useState(null);
  const [destPlace, setDestPlace] = useState(null);
  const [routeOption, setRouteOption] = useState('SAFE');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [routeInfo, setRouteInfo] = useState(null);
  const [detourInfo, setDetourInfo] = useState(null);
  const [detourLoading, setDetourLoading] = useState(false);
  const [detourError, setDetourError] = useState('');
  const [selectedRoute, setSelectedRoute] = useState(null); // 'original' | 'detour' | null
  const [favorites, setFavorites] = useState([]);
  const [showFavoritesModal, setShowFavoritesModal] = useState(false);
  const [modalField, setModalField] = useState(null); // 모달 열 때의 대상 칸 스냅샷
  const [history, setHistory] = useState(() => getSearchHistory());
  const routeDataRef = useRef(null); // 원래 경로 전체 데이터 저장
  const defaultAppliedRef = useRef(false);
  const activeFieldRef = useRef(null); // 마지막으로 포커스된 입력칸 ('origin' | 'dest')

  // 장소 선택 시 최근 검색 기록에 저장 (출발/도착 공통)
  const recordSetOrigin = (place) => {
    setOriginPlace(place);
    setHistory(addSearchHistory(place));
  };
  const recordSetDest = (place) => {
    setDestPlace(place);
    setHistory(addSearchHistory(place));
    // 출발지가 비어있으면 현재 위치를 자동 삽입 (도착지만 검색해도 바로 경로 검색 가능)
    if (!originPlace && defaultOrigin) {
      setOriginText(defaultOrigin.name);
      setOriginPlace({ name: defaultOrigin.name, lat: defaultOrigin.lat, lng: defaultOrigin.lng });
    }
  };
  const handleRemoveHistory = (id) => setHistory(removeSearchHistory(id));
  const handleClearHistory = () => setHistory(clearSearchHistory());

  const favToPlace = (f) => ({ name: f.name, lat: f.latitude, lng: f.longitude, address: f.address });

  // 입력칸 포커스 추적 (즐겨찾기/최근검색 클릭 시 어느 칸에 넣을지 판단용)
  const handleFieldFocus = (f) => { activeFieldRef.current = f; };
  const handleFieldBlur = (f) => {
    // 클릭(포커스 이탈 직후) 처리를 위해 약간의 지연 후 해제
    setTimeout(() => { if (activeFieldRef.current === f) activeFieldRef.current = null; }, 200);
  };

  // 즐겨찾기/최근검색 클릭: 포커스 중인 칸에 삽입.
  // 아무 칸도 포커스 안 했으면 도착지로 넣고 출발지는 현재위치를 채움.
  const applyPlace = (place, forcedField) => {
    const field = forcedField || activeFieldRef.current;
    if (field === 'origin') {
      setOriginText(place.name);
      setOriginPlace({ name: place.name, lat: place.lat, lng: place.lng, address: place.address });
    } else {
      if (!originPlace && defaultOrigin) {
        setOriginText(defaultOrigin.name);
        setOriginPlace({ name: defaultOrigin.name, lat: defaultOrigin.lat, lng: defaultOrigin.lng });
      }
      setDestText(place.name);
      setDestPlace({ name: place.name, lat: place.lat, lng: place.lng, address: place.address });
    }
    setShowFavoritesModal(false);
  };

  const openFavoritesModal = () => {
    setModalField(activeFieldRef.current);
    setShowFavoritesModal(true);
  };

  const clearOrigin = () => { setOriginText(''); setOriginPlace(null); };
  const clearDest = () => { setDestText(''); setDestPlace(null); };

  // 출발 ↔ 도착 맞바꾸기
  const handleSwap = () => {
    setOriginText(destText);
    setDestText(originText);
    setOriginPlace(destPlace);
    setDestPlace(originPlace);
    setRouteInfo(null);
    setDetourInfo(null);
    setSelectedRoute(null);
    setError('');
  };

  // 전체 초기화 (출발지는 현재위치로 복귀, 도착지·결과 비움)
  const handleReset = () => {
    if (defaultOrigin) {
      setOriginText(defaultOrigin.name);
      setOriginPlace({ name: defaultOrigin.name, lat: defaultOrigin.lat, lng: defaultOrigin.lng });
    } else {
      setOriginText('');
      setOriginPlace(null);
    }
    setDestText('');
    setDestPlace(null);
    setRouteInfo(null);
    setDetourInfo(null);
    setDetourError('');
    setSelectedRoute(null);
    setError('');
  };

  useEffect(() => {
    if (defaultOrigin && !defaultAppliedRef.current && !originPlace) {
      defaultAppliedRef.current = true;
      setOriginText(defaultOrigin.name);
      setOriginPlace({ name: defaultOrigin.name, lat: defaultOrigin.lat, lng: defaultOrigin.lng });
    }
  }, [defaultOrigin, originPlace]);

  useEffect(() => {
    if (!isLoggedIn) { setFavorites([]); return; }
    getFavorites().then(setFavorites).catch(() => {});
  }, [isLoggedIn]);

  // 즐겨찾기 목록이 바뀔 때마다 지도(부모)에 전달해 마커 갱신
  useEffect(() => {
    onFavoritesChange?.(favorites);
  }, [favorites, onFavoritesChange]);

  const isFavorited = (place) =>
    !!place && favorites.some((f) => f.latitude === place.lat && f.longitude === place.lng);

  const handleAddFavorite = async (place) => {
    if (!place || isFavorited(place)) return;
    try {
      const saved = await addFavorite(place);
      // 백엔드가 중복 시 기존 항목을 반환하므로 id 기준으로 중복 추가 방지
      setFavorites((prev) => (prev.some((f) => f.id === saved.id) ? prev : [saved, ...prev]));
    } catch (e) {
      console.error('즐겨찾기 추가 실패:', e);
    }
  };

  const handleRemoveFavorite = async (id) => {
    try {
      await removeFavorite(id);
      setFavorites((prev) => prev.filter((f) => f.id !== id));
    } catch (e) {
      console.error('즐겨찾기 삭제 실패:', e);
    }
  };

  const ROUTE_OPTIONS = [
    { id: 'SAFE', label: '안전 우선' },
    { id: 'SHORT', label: '최단 거리' },
    { id: 'FLAT', label: '오르막 최소' },
  ];

  const handleSearch = async () => {
    if (!originPlace) { setError('출발지를 검색하여 선택해주세요.'); return; }
    if (!destPlace) { setError('도착지를 검색하여 선택해주세요.'); return; }

    setError('');
    setLoading(true);
    setRouteInfo(null);
    setDetourInfo(null);
    setDetourError('');
    setSelectedRoute(null);

    try {
      const res = await fetch(`${API_BASE}/route/walk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLng: originPlace.lng, originLat: originPlace.lat,
          destLng: destPlace.lng, destLat: destPlace.lat,
          option: routeOption,
        }),
      });

      if (!res.ok) throw new Error('경로를 찾을 수 없습니다.');
      const data = await res.json();

      routeDataRef.current = data;
      setRouteInfo({
        distance: data.totalDistance,
        duration: data.totalDuration,
        riskSections: data.riskSections || [],
        overallRisk: data.overallRisk || 'SAFE',
        weather: data.weather || null,
      });


      if (data.coords && data.coords.length > 0) {
        onRouteFound(data.coords, data.riskSections || []);
      }

      // 안전 우선 모드 + 위험구간 있으면 자동으로 우회 경로도 검색
      if (routeOption === 'SAFE' && data.riskSections && data.riskSections.length > 0) {
        searchDetour(data);
      }
    } catch (err) {
      setError(toKorError(err));
    } finally {
      setLoading(false);
    }
  };

  const searchDetour = async (originalData) => {
    const data = originalData || routeDataRef.current;
    if (!data || !data.riskSections || data.riskSections.length === 0) return;

    setDetourLoading(true);
    setDetourError('');
    setDetourInfo(null);

    try {
      const res = await fetch(`${API_BASE}/route/walk/detour`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLng: originPlace.lng, originLat: originPlace.lat,
          destLng: destPlace.lng, destLat: destPlace.lat,
          option: routeOption,
          avoidPoints: data.riskSections.map((rs) => ({
            lat: rs.lat, lng: rs.lng,
            riskLevel: rs.riskLevel,
            nearestRouteIdx: rs.nearestRouteIdx,
          })),
        }),
      });

      if (!res.ok) throw new Error('우회 경로를 찾을 수 없습니다.');
      const detour = await res.json();

      if (detour.detourApplied === false) {
        setDetourError(detour.detourMessage || '더 안전한 우회 경로를 찾지 못했습니다.');
        return;
      }

      setDetourInfo({
        distance: detour.totalDistance,
        duration: detour.totalDuration,
        riskSections: detour.riskSections || [],
        overallRisk: detour.overallRisk || 'SAFE',
        coords: detour.coords,
      });

      if (detour.coords && detour.coords.length > 0) {
        onDetourFound(detour.coords, detour.riskSections || []);
      }
    } catch (err) {
      console.error('우회 경로 검색 실패:', err);
      setDetourError(toKorError(err));
    } finally {
      setDetourLoading(false);
    }
  };

  const handleSelectRoute = (type) => {
    setSelectedRoute(type);
    onSelectRoute(type);
  };

  const riskBadge = (risk) => {
    const cls = risk === 'DANGER' ? 'danger' : risk === 'CAUTION' ? 'caution' : 'safe';
    const lbl = risk === 'DANGER' ? '위험' : risk === 'CAUTION' ? '주의' : '안전';
    return <span className={`overall-risk-badge ${cls}`}>{lbl}</span>;
  };

  return (
    <div className="content-section">
      <h3 className="section-title">경로 찾기</h3>
      <div className="route-inputs">
        <PlaceSearchInput
          placeholder="출발지 입력" value={originText}
          onChange={(v) => setOriginText(v)} onClear={() => setOriginPlace(null)}
          onSelect={recordSetOrigin} dotClass="start" kakaoReady={kakaoReady}
          field="origin" onFieldFocus={handleFieldFocus} onFieldBlur={handleFieldBlur}
          trailing={
            <>
              {originText && (
                <button type="button" className="input-clear-btn" onMouseDown={(e) => e.preventDefault()} onClick={clearOrigin} title="지우기">×</button>
              )}
              {isLoggedIn && (
                <button
                  type="button"
                  className={`favorite-star-btn ${isFavorited(originPlace) ? 'active' : ''}`}
                  disabled={!originPlace || isFavorited(originPlace)}
                  onClick={() => handleAddFavorite(originPlace)}
                  title={isFavorited(originPlace) ? '이미 즐겨찾기에 있음' : '즐겨찾기 추가'}
                >★</button>
              )}
            </>
          }
        />
        <div className="route-input-divider" />
        <PlaceSearchInput
          placeholder="도착지 입력" value={destText}
          onChange={(v) => setDestText(v)} onClear={() => setDestPlace(null)}
          onSelect={recordSetDest} dotClass="end" kakaoReady={kakaoReady}
          field="dest" onFieldFocus={handleFieldFocus} onFieldBlur={handleFieldBlur}
          trailing={
            <>
              {destText && (
                <button type="button" className="input-clear-btn" onMouseDown={(e) => e.preventDefault()} onClick={clearDest} title="지우기">×</button>
              )}
              {isLoggedIn && (
                <button
                  type="button"
                  className={`favorite-star-btn ${isFavorited(destPlace) ? 'active' : ''}`}
                  disabled={!destPlace || isFavorited(destPlace)}
                  onClick={() => handleAddFavorite(destPlace)}
                  title={isFavorited(destPlace) ? '이미 즐겨찾기에 있음' : '즐겨찾기 추가'}
                >★</button>
              )}
            </>
          }
        />
      </div>

      <div className="route-actions">
        <button type="button" className="route-action-btn" onClick={handleSwap} title="출발↔도착 바꾸기">
          ⇅ 출발·도착 바꾸기
        </button>
        <button type="button" className="route-action-btn" onClick={handleReset} title="출발·도착 초기화">
          ↺ 초기화
        </button>
      </div>

      <div className="favorite-section">
        <div className="favorite-section-header">
          <span className="favorite-section-title">⭐ 즐겨찾기</span>
          {isLoggedIn && (
            <button type="button" className="favorite-viewall-btn" onClick={openFavoritesModal}>
              전체보기 ({favorites.length})
            </button>
          )}
        </div>
        {!isLoggedIn ? (
          <p className="favorite-empty">로그인하면 자주 가는 곳을 저장할 수 있어요.</p>
        ) : favorites.length > 0 ? (
          <div className="favorite-chip-list">
            {favorites.slice(0, 4).map((fav) => (
              <span key={fav.id} className="favorite-chip">
                <button type="button" className="favorite-chip-label" onClick={() => applyPlace(favToPlace(fav))}>
                  ★ {fav.name}
                </button>
                <button type="button" className="favorite-chip-remove" onClick={() => handleRemoveFavorite(fav.id)}>×</button>
              </span>
            ))}
          </div>
        ) : (
          <p className="favorite-empty">저장된 즐겨찾기가 없습니다. 검색 후 ★ 버튼으로 추가해보세요.</p>
        )}
      </div>

      {showFavoritesModal && (
        <FavoritesModal
          favorites={favorites}
          onSelect={(fav) => applyPlace(favToPlace(fav), modalField)}
          onRemove={handleRemoveFavorite}
          onClose={() => setShowFavoritesModal(false)}
        />
      )}

      <div className="route-option-group">
        {ROUTE_OPTIONS.map((opt) => (
          <button
            key={opt.id}
            className={`route-option-btn ${routeOption === opt.id ? 'active' : ''}`}
            onClick={() => setRouteOption(opt.id)}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {error && <p className="route-error">{error}</p>}

      <button className="primary-btn" onClick={handleSearch} disabled={loading}>
        {loading ? <span className="btn-loading"><span className="btn-spinner" />검색 중...</span> : '경로 검색'}
      </button>

      {!routeInfo && (
        <div className="recent-search-section">
          <div className="recent-search-header">
            <span className="recent-search-title">🕘 최근 검색</span>
            {history.length > 0 && (
              <button type="button" className="recent-search-clear" onClick={handleClearHistory}>전체삭제</button>
            )}
          </div>
          {history.length === 0 ? (
            <p className="favorite-empty">최근 검색한 장소가 여기에 표시됩니다.</p>
          ) : (
          <ul className="recent-search-list">
            {history.map((h) => (
              <li key={h.id} className="recent-search-item">
                <button type="button" className="recent-search-main" onClick={() => applyPlace(h)}>
                  <span className="recent-search-name">{h.name}</span>
                  {h.address && <span className="recent-search-address">{h.address}</span>}
                </button>
                <button type="button" className="recent-search-remove" onClick={() => handleRemoveHistory(h.id)}>×</button>
              </li>
            ))}
          </ul>
          )}
        </div>
      )}

      {routeInfo && (
        <div className="route-result-section">
          {/* 날씨 정보 */}
          {routeInfo.weather && <WeatherCard weather={routeInfo.weather} />}

          {/* 경로 비교 패널 (우회 경로가 있을 때) */}
          {detourInfo ? (
            <div className="route-compare">
              <div className={`route-compare-card original${selectedRoute === 'original' ? ' selected' : ''}`}>
                <div className="route-compare-header">
                  <span className="route-compare-label">원래 경로</span>
                  {riskBadge(routeInfo.overallRisk)}
                </div>
                <div className="route-compare-stats">
                  <span>{(routeInfo.distance / 1000).toFixed(1)}km</span>
                  <span>{Math.round(routeInfo.duration / 60)}분</span>
                </div>
                <div className="route-compare-risk">
                  위험구간 {routeInfo.riskSections.length}개
                </div>
                <button className="route-select-btn" onClick={() => handleSelectRoute('original')}>
                  {selectedRoute === 'original' ? '선택됨' : '이 경로 선택'}
                </button>
              </div>
              <div className={`route-compare-card detour${selectedRoute === 'detour' ? ' selected' : ''}`}>
                <div className="route-compare-header">
                  <span className="route-compare-label">우회 경로</span>
                  {riskBadge(detourInfo.overallRisk)}
                </div>
                <div className="route-compare-stats">
                  <span>{(detourInfo.distance / 1000).toFixed(1)}km</span>
                  <span>{Math.round(detourInfo.duration / 60)}분</span>
                </div>
                <div className="route-compare-risk">
                  {detourInfo.riskSections.length > 0
                    ? `위험구간 ${detourInfo.riskSections.length}개 (완전한 우회 어려움)`
                    : '위험구간 없음'
                  }
                </div>
                <button className="route-select-btn detour" onClick={() => handleSelectRoute('detour')}>
                  {selectedRoute === 'detour' ? '선택됨' : '이 경로 선택'}
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* 단일 경로 정보 */}
              <div className="route-result-info">
                <span>거리: {(routeInfo.distance / 1000).toFixed(1)}km</span>
                <span>예상 시간: {Math.round(routeInfo.duration / 60)}분</span>
              </div>
              <div className="risk-summary">
                <div className="risk-summary-header">
                  {riskBadge(routeInfo.overallRisk)}
                  {routeInfo.riskSections.length > 0
                    ? <span className="risk-summary-text">위험구간 {routeInfo.riskSections.length}개 발견</span>
                    : <span className="risk-summary-text safe-text">안전한 경로입니다</span>
                  }
                </div>
                {routeInfo.riskSections.length > 0 && (
                  <div className="risk-section-list">
                    {routeInfo.riskSections.map((rs, i) => {
                      const levelClass = rs.riskLevel === 'VERY_HIGH' || rs.riskLevel === 'HIGH' ? 'high'
                        : rs.riskLevel === 'MEDIUM' ? 'medium' : 'low';
                      const levelLabel = rs.riskLevel === 'VERY_HIGH' || rs.riskLevel === 'HIGH' ? '위험'
                        : rs.riskLevel === 'MEDIUM' ? '주의' : '양호';
                      return (
                        <div key={i} className="risk-section-item">
                          <div className="risk-section-info">
                            <span className="risk-section-name">{rs.name || `위험구간 ${i + 1}`}</span>
                            <span className="risk-section-detail">경사 {rs.grade ? rs.grade.toFixed(1) + '°' : '-'} · 경로에서 {rs.distanceFromRoute ? rs.distanceFromRoute.toFixed(0) + 'm' : '-'}</span>
                          </div>
                          <span className={`risk-badge ${levelClass}`}>{levelLabel}</span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          )}

          {/* 우회 경로 버튼 */}
          {routeInfo.riskSections.length > 0 && !detourInfo && routeOption !== 'SHORT' && (
            <button
              className="detour-btn"
              onClick={() => searchDetour(null)}
              disabled={detourLoading}
            >
              {detourLoading ? '우회 경로 검색 중...' : '\uD83D\uDD04 우회 경로 추천'}
            </button>
          )}
          {detourError && <p className="route-error">{detourError}</p>}
          {detourInfo && detourInfo.riskSections.length > 0 && (
            <p className="detour-warning">완전한 우회가 어렵습니다. 주의하여 이동하세요.</p>
          )}
        </div>
      )}

    </div>
  );
}

const REGION_OPTIONS = [
  { label: '전체 지역', value: '' },
  { label: '성남시', value: '411' },
  { label: '수정구', value: '41131' },
  { label: '중원구', value: '41133' },
  { label: '분당구', value: '41135' },
];

const RISK_FILTER_OPTIONS = [
  { label: '위험도 전체', value: '' },
  { label: '위험 (HIGH)', value: 'HIGH' },
  { label: '주의 (MEDIUM)', value: 'MEDIUM' },
  { label: '양호 (LOW)', value: 'LOW' },
  // 위험도가 비어있는 레코드는 서버 필터로 조회할 수 없어 클라이언트에서 거른다.
  { label: '미측정', value: 'UNSET' },
];

const FALLBACK_DATA = [
  { name: '광교산 오르막', regionCode: '', grade: 12, riskLevel: 'HIGH', latitude: 37.28, longitude: 127.05 },
  { name: '청계산 진입로', regionCode: '', grade: 9, riskLevel: 'MEDIUM', latitude: 37.41, longitude: 127.05 },
  { name: '북한산 순환로', regionCode: '', grade: 7, riskLevel: 'LOW', latitude: 37.66, longitude: 126.98 },
];

function UphillPanel({ onItemClick, onToggleDb, dbMarkersOn }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [regionCode, setRegionCode] = useState('411');
  const [riskLevel, setRiskLevel] = useState('');

  const fetchData = useCallback(async (region, risk) => {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams();
      if (region) params.append('regionCode', region);
      if (risk && risk !== 'UNSET') params.append('riskLevel', risk);
      const url = `${API_BASE}/steep-slope${params.toString() ? '?' + params.toString() : ''}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error('데이터를 불러올 수 없습니다.');
      const data = await res.json();
      setItems(risk === 'UNSET' ? data.filter((d) => !d.riskLevel) : data);
    } catch (err) {
      console.error('급경사지 데이터 로드 실패:', err);
      setError(toKorError(err));
      setItems(FALLBACK_DATA);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData(regionCode, riskLevel);
  }, [fetchData, regionCode, riskLevel]);

  // 위험도가 없는 레코드(경사도 미측정)를 '양호'로 표시하면 안 된다 — 미측정으로 구분한다.
  const getRiskClass = (level) => {
    if (level === 'VERY_HIGH' || level === 'HIGH') return 'high';
    if (level === 'MEDIUM') return 'medium';
    if (level === 'LOW') return 'low';
    return 'unknown';
  };

  const getRiskLabel = (level) => {
    if (level === 'VERY_HIGH' || level === 'HIGH') return '위험';
    if (level === 'MEDIUM') return '주의';
    if (level === 'LOW') return '양호';
    return '미측정';
  };

  const formatAddress = (item) => {
    const parts = [item.sigungu, item.eupmyeon, item.jibun].filter(Boolean);
    return parts.length ? parts.join(' ') : (item.regionCode ? `지역코드 ${item.regionCode}` : '');
  };

  const unmeasured = items.filter((it) => it.grade == null).length;
  const mountainCount = items.filter((it) => it.isMountain).length;

  return (
    <div className="content-section">
      <h3 className="section-title">오르막길 구간</h3>
      <p className="section-desc">
        성남시 급경사지 안전 정보입니다. ({items.length}건
        {mountainCount > 0 && <span className="desc-terrain"> · 산지형 {mountainCount}건</span>}
        {unmeasured > 0 && <span className="desc-warn"> · 경사도 미측정 {unmeasured}건</span>})
      </p>

      <button
        onClick={onToggleDb}
        style={{
          width: '100%', marginBottom: '8px', padding: '9px 14px',
          background: dbMarkersOn ? '#1A237E' : '#E8EAF6',
          color: dbMarkersOn ? '#fff' : '#1A237E',
          border: 'none', borderRadius: '10px',
          fontSize: '13px', fontWeight: 600, cursor: 'pointer', letterSpacing: '0.3px',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '7px',
          boxShadow: dbMarkersOn ? '0 2px 8px rgba(26,35,126,0.25)' : '0 1px 3px rgba(0,0,0,0.08)',
          transition: 'all 0.18s ease',
        }}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <circle cx="12" cy="12" r="10" fillOpacity="0.25"/>
          <circle cx="12" cy="12" r="5"/>
        </svg>
        {dbMarkersOn ? '급경사지 반경 닫기' : '급경사지 반경 표시'}
      </button>

      <div className="filter-row">
        <select className="filter-select" value={regionCode} onChange={(e) => setRegionCode(e.target.value)}>
          {REGION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select className="filter-select" value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}>
          {RISK_FILTER_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {loading && <p className="loading-text">데이터 로딩 중...</p>}
      {error && <p className="route-error">{error} (기본 데이터 표시)</p>}

      <div className="segment-list">
        {items.map((item, i) => (
          <div
            key={item.id || i}
            className="segment-item"
            onClick={() => item.latitude && item.longitude && onItemClick?.(item.latitude, item.longitude, item.name)}
          >
            <div className="segment-info">
              <span className="segment-name">
                {item.isMountain && <span className="terrain-tag" title="산 지번(임야) — 산지형 급경사지">산지형</span>}
                {item.name || '이름 없음'}
              </span>
              <span className="segment-city">{formatAddress(item)}</span>
            </div>
            <div className="segment-meta">
              <span className={`grade-badge ${item.grade == null ? 'unknown' : ''}`}>
                {item.grade != null ? `경사 ${item.grade.toFixed(1)}°` : '경사 미측정'}
              </span>
              <span className={`risk-badge ${getRiskClass(item.riskLevel)}`}>
                {getRiskLabel(item.riskLevel)}
              </span>
            </div>
          </div>
        ))}
        {!loading && items.length === 0 && (
          <p className="empty-text">조건에 맞는 데이터가 없습니다.</p>
        )}
      </div>
    </div>
  );
}

const WEATHER_ICONS = {
  clear: '\u2600\uFE0F',
  cloudy: '\u26C5',
  overcast: '\u2601\uFE0F',
  rain: '\uD83C\uDF27\uFE0F',
  snow: '\u2744\uFE0F',
  sleet: '\uD83C\uDF28\uFE0F',
};

function WeatherMiniOverlay({ weather }) {
  if (!weather) return null;
  const icon = WEATHER_ICONS[weather.icon] || '\u2600\uFE0F';
  const hasWarning = !!weather.warning;

  return (
    <div className={`weather-mini-overlay${hasWarning ? ' warning' : ''}`} title={hasWarning ? weather.warning : undefined}>
      <span className="weather-mini-icon">{icon}</span>
      <span className="weather-mini-temp">{weather.temperature != null ? Math.round(weather.temperature) : '--'}°C</span>
    </div>
  );
}

function WeatherCard({ weather }) {
  if (!weather) return null;
  const icon = WEATHER_ICONS[weather.icon] || '\u2600\uFE0F';

  return (
    <div className="weather-card">
      <div className="weather-main">
        <span className="weather-icon-large">{icon}</span>
        <div className="weather-info">
          <span className="weather-temp">{weather.temperature != null ? Math.round(weather.temperature) : '--'}°C</span>
          <span className="weather-desc">{weather.description || '--'}</span>
        </div>
        <div className="weather-detail">
          <span>습도 {weather.humidity ?? '--'}%</span>
          <span>풍속 {weather.windSpeed != null ? weather.windSpeed.toFixed(1) : '--'}m/s</span>
        </div>
      </div>
      {weather.warning && (
        <div className="weather-warning">
          <span className="warning-icon">⚠️</span>
          <span>{weather.warning}</span>
        </div>
      )}
    </div>
  );
}

function SafetyPanel({ onToggleLayer, onClearLayer }) {
  const [weather, setWeather] = useState(null);
  const [loading, setLoading] = useState(true);
  const [riskStats, setRiskStats] = useState({ high: 0, medium: 0, low: 0 });
  const [layers, setLayers] = useState({
    accidents: false, cctv: false,
    protectedZones: false, welfare: false, shelters: false, senior: false,
  });
  const [layerData, setLayerData] = useState({
    accidents: [], cctv: [],
    protectedZones: [], welfare: [], shelters: [], senior: [],
  });

  // 날씨 + 통계 로드
  useEffect(() => {
    const fetchWeather = async (lat, lng) => {
      try {
        const res = await fetch(`${API_BASE}/weather?lat=${lat}&lng=${lng}`);
        if (res.ok) setWeather(await res.json());
      } catch (err) {
        console.error('날씨 조회 실패:', err);
      } finally {
        setLoading(false);
      }
    };

    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => fetchWeather(pos.coords.latitude, pos.coords.longitude),
        () => fetchWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
        { timeout: 3000 }
      );
    } else {
      fetchWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
    }

    // 위험도별 통계
    fetch(`${API_BASE}/steep-slope/risk-stats`)
      .then((r) => r.ok ? r.json() : null)
      .then((d) => { if (d) setRiskStats(d); })
      .catch(() => {});
  }, []);

  // 레이어 설정
  const LAYER_CONFIG = [
    { key: 'accidents', label: '사고다발지', emoji: '\u26A0\uFE0F', endpoint: '/safety/accident-zones' },
    { key: 'cctv', label: 'CCTV', emoji: '\uD83D\uDCF9', endpoint: '/safety/cctv' },
    { key: 'protectedZones', label: '교통약자 보호구역', emoji: '\uD83D\uDEB8', endpoint: '/safety/protected-zones' },
    { key: 'welfare', label: '장애인복지시설', emoji: '\u267F', endpoint: '/safety/welfare-facilities' },
    { key: 'shelters', label: '그늘막', emoji: '\u26F1\uFE0F', endpoint: '/safety/shade-shelters' },
    { key: 'senior', label: '노인복지관', emoji: '\uD83C\uDFE0', endpoint: '/safety/senior-centers' },
  ];

  // 레이어 토글 핸들러
  const handleToggle = async (type) => {
    const newState = !layers[type];
    setLayers((prev) => ({ ...prev, [type]: newState }));

    if (!newState) {
      onClearLayer(type);
      return;
    }

    // 데이터가 이미 있으면 재사용
    if (layerData[type] && layerData[type].length > 0) {
      const config = LAYER_CONFIG.find((c) => c.key === type);
      onToggleLayer(type, layerData[type], config ? config.emoji : '');
      return;
    }

    // 데이터 fetch
    const config = LAYER_CONFIG.find((c) => c.key === type);
    if (!config) return;

    try {
      const res = await fetch(`${API_BASE}${config.endpoint}`);
      if (res.ok) {
        const data = await res.json();
        setLayerData((prev) => ({ ...prev, [type]: data }));
        onToggleLayer(type, data, config.emoji);
      } else {
        console.error(`[${type}] API 오류: HTTP ${res.status}`);
      }
    } catch (err) {
      console.error(`[${type}] 데이터 로드 실패:`, err);
    }
  };

  const handleAllOn = () => {
    LAYER_CONFIG.forEach(({ key }) => {
      if (!layers[key]) handleToggle(key);
    });
  };

  const handleAllOff = () => {
    LAYER_CONFIG.forEach(({ key }) => {
      if (layers[key]) onClearLayer(key);
    });
    setLayers({ accidents: false, cctv: false, protectedZones: false, welfare: false, shelters: false, senior: false });
  };

  const getWeatherAlerts = () => {
    if (!weather) return [];
    const alerts = [];

    if (weather.warning) {
      alerts.push({ msg: weather.warning, time: '방금 전', level: 'high' });
    }
    if (weather.temperature != null && weather.temperature <= 3 && weather.temperature > 0) {
      alerts.push({ msg: '낮은 기온, 결빙 가능성 있음', time: '방금 전', level: 'medium' });
    }
    if (weather.humidity != null && weather.humidity >= 90) {
      alerts.push({ msg: '높은 습도, 노면 습기 주의', time: '방금 전', level: 'medium' });
    }
    if (weather.windSpeed != null && weather.windSpeed >= 8 && !(weather.windSpeed >= 10)) {
      alerts.push({ msg: '다소 강한 바람, 노출 구간 주의', time: '방금 전', level: 'medium' });
    }
    if (alerts.length === 0) {
      alerts.push({ msg: '현재 특이 기상 없음, 안전한 보행 가능', time: '방금 전', level: 'low' });
    }
    return alerts;
  };

  return (
    <div className="content-section">
      <h3 className="section-title">안전 정보</h3>
      <p className="section-desc">실시간 도로 안전 현황입니다.</p>

      {/* 날씨 카드 */}
      {loading ? (
        <p className="loading-text">날씨 정보 로딩 중...</p>
      ) : (
        <WeatherCard weather={weather} />
      )}

      {/* 위험도 통계 (실제 DB 데이터) */}
      <div className="safety-stats">
        <div className="stat-card red">
          <span className="stat-number">{riskStats.high}</span>
          <span className="stat-label">위험 구간</span>
        </div>
        <div className="stat-card yellow">
          <span className="stat-number">{riskStats.medium}</span>
          <span className="stat-label">주의 구간</span>
        </div>
        <div className="stat-card green">
          <span className="stat-number">{riskStats.low}</span>
          <span className="stat-label">안전 구간</span>
        </div>
      </div>

      {/* 기상 알림 (실제 날씨 기반) */}
      <div className="notice-list">
        <p className="option-label">기상 알림</p>
        {getWeatherAlerts().map((item, i) => (
          <div key={i} className="notice-item">
            <span className={`notice-dot ${item.level}`} />
            <div>
              <p className="notice-msg">{item.msg}</p>
              <p className="notice-time">{item.time}</p>
            </div>
          </div>
        ))}
      </div>

      {/* 지도 레이어 토글 */}
      <div className="layer-toggles">
        <div className="layer-toggles-header">
          <p className="option-label">지도 레이어</p>
          <div className="layer-all-btns">
            <button className="layer-all-btn" onClick={handleAllOn}>전체 켜기</button>
            <button className="layer-all-btn off" onClick={handleAllOff}>전체 끄기</button>
          </div>
        </div>
        <div className="layer-toggle-list">
          {LAYER_CONFIG.map(({ key, label, emoji }) => (
            <button
              key={key}
              className={`layer-toggle-btn${layers[key] ? ' active' : ''}`}
              onClick={() => handleToggle(key)}
            >
              <span className="layer-toggle-icon">{emoji}</span>
              <span>{label}</span>
              <span className={`layer-toggle-badge${layers[key] ? ' on' : ''}`}>{layers[key] ? 'ON' : 'OFF'}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default MapPage;
