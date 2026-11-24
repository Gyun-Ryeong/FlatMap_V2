import { useAuth } from '../context/AuthContext';

// 카카오 로그인/로그아웃. 로그인하면 즐겨찾기를 쓸 수 있다.
export default function AccountButton() {
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
      {user.profileImageUrl && <img src={user.profileImageUrl} alt="" className="kakao-user-avatar" />}
      <span className="kakao-user-nickname">{user.nickname || '사용자'}</span>
      <button className="kakao-logout-btn" onClick={logout}>로그아웃</button>
    </div>
  );
}
