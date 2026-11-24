import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import axiosInstance from '../api/axiosInstance';
import { buildLoginUrl } from '../utils/authUrl';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    try {
      const res = await axiosInstance.get('/auth/me');
      setUser(res.data);
    } catch (e) {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const login = () => {
    // 전체 페이지 이동은 axios 를 타지 않는다. 기본은 같은 출처(/api, 운영 nginx·개발 프록시), 필요할 때만 VITE_BACKEND_URL.
    window.location.href = buildLoginUrl(import.meta.env.VITE_BACKEND_URL);
  };

  const logout = async () => {
    try {
      await axiosInstance.post('/auth/logout');
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, isLoggedIn: !!user, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
