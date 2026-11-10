import { useState } from 'react';
import './styles/global.css';
import './styles/app.css';
import HomeScreen from './components/Home/HomeScreen';
import MapPage from './pages/MapPage';
import { AuthProvider } from './context/AuthContext';

// 카카오 로그인은 백엔드를 거쳐 "/" 로 되돌아오므로, 지도에 들어온 상태를 이 탭의 sessionStorage 에 기억해
// 로그인 직후 다시 홈 화면이 보이지 않게 한다. (저장소를 못 쓰는 환경에서는 홈부터 시작한다.)
const ENTERED_KEY = 'app_entered';

function readEntered() {
  try {
    return sessionStorage.getItem(ENTERED_KEY) === '1';
  } catch {
    return false;
  }
}

function writeEntered(value) {
  try {
    if (value) sessionStorage.setItem(ENTERED_KEY, '1');
    else sessionStorage.removeItem(ENTERED_KEY);
  } catch {
    /* 저장 실패는 무시 */
  }
}

export default function App() {
  const [hasEntered, setHasEntered] = useState(readEntered);

  const enter = () => { writeEntered(true); setHasEntered(true); };
  const goHome = () => { writeEntered(false); setHasEntered(false); };

  return (
    <AuthProvider>
      {hasEntered ? <MapPage onHome={goHome} /> : <HomeScreen onStart={enter} />}
    </AuthProvider>
  );
}
