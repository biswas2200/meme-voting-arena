import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};

export const AuthProvider = ({ children }) => {
  // Initialize user synchronously from localStorage so route guards
  // never see user=null on first render when a session exists.
  const [user, setUser] = useState(() => {
    try {
      const savedUser = localStorage.getItem('user');
      const savedToken = localStorage.getItem('token');
      if (savedToken && savedUser) return JSON.parse(savedUser);
    } catch {}
    return null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [loading, setLoading] = useState(false); // no longer needed to block render

  /* ── Background token validation on mount ── */
  useEffect(() => {
    const validateToken = async () => {
      const savedToken = localStorage.getItem('token');
      if (!savedToken) return; // no session, nothing to validate

      try {
        const res = await api.get('/api/auth/profile', { timeout: 5000 });
        const fresh = {
          id:       res.data.id,
          username: res.data.username,
          email:    res.data.email,
          role:     res.data.role
        };
        setUser(fresh);
        localStorage.setItem('user', JSON.stringify(fresh));
      } catch (err) {
        // Only clear session on explicit 401 (token expired/invalid)
        // Network errors, timeouts, 500s — keep the cached user
        if (err.response?.status === 401) {
          _clearSession();
        }
      }
    };
    validateToken();
  }, []);

  const _clearSession = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  };

  /* ── Login ── */
  const login = async (credentials) => {
    try {
      const res = await api.post('/api/auth/signin', credentials);
      const { token: newToken, user: userData } = res.data;
      setToken(newToken);
      setUser(userData);
      localStorage.setItem('token', newToken);
      localStorage.setItem('user', JSON.stringify(userData));
      return { success: true, user: userData };
    } catch (err) {
      return {
        success: false,
        message: err.response?.data?.message || 'Login failed. Please try again.'
      };
    }
  };

  /* ── Register ── */
  const register = async (userData) => {
    try {
      const res = await api.post('/api/auth/signup', userData);
      const { token: newToken, user: newUser } = res.data;
      setToken(newToken);
      setUser(newUser);
      localStorage.setItem('token', newToken);
      localStorage.setItem('user', JSON.stringify(newUser));
      return { success: true, user: newUser };
    } catch (err) {
      return {
        success: false,
        message: err.response?.data?.message || 'Registration failed. Please try again.'
      };
    }
  };

  /* ── Logout ── */
  const logout = () => _clearSession();

  /* ── Refresh profile from server ── */
  const refreshUserData = async () => {
    const res = await api.get('/api/auth/profile');
    const fresh = {
      id:       res.data.id,
      username: res.data.username,
      email:    res.data.email,
      role:     res.data.role
    };
    setUser(fresh);
    localStorage.setItem('user', JSON.stringify(fresh));
    return fresh;
  };

  return (
    <AuthContext.Provider value={{
      user, token, loading,
      login, register, logout, refreshUserData,
      isAuthenticated: !!user
    }}>
      {children}
    </AuthContext.Provider>
  );
};
