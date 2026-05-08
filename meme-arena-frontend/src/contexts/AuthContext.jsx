import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext();

// ── Persistent debug logger — survives page redirects ──────────────────────
// Read logs any time by running this in the browser console:
//   JSON.parse(localStorage.getItem('_authLog') || '[]')
const authLog = (msg, data) => {
  const entry = { t: new Date().toISOString(), msg, data };
  console.log('[AUTH]', msg, data ?? '');
  try {
    const existing = JSON.parse(localStorage.getItem('_authLog') || '[]');
    existing.push(entry);
    // Keep last 50 entries
    if (existing.length > 50) existing.splice(0, existing.length - 50);
    localStorage.setItem('_authLog', JSON.stringify(existing));
  } catch {}
};

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
      authLog('INIT: reading localStorage', {
        hasToken: !!savedToken,
        hasUser: !!savedUser,
        tokenPreview: savedToken ? savedToken.substring(0, 20) + '...' : null
      });
      if (savedToken && savedUser) {
        const parsed = JSON.parse(savedUser);
        authLog('INIT: restored user from localStorage', { username: parsed.username, role: parsed.role });
        return parsed;
      }
      authLog('INIT: no saved session found');
    } catch (e) {
      authLog('INIT: error parsing saved user', { error: e.message });
    }
    return null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [loading, setLoading] = useState(false);

  /* ── Background token validation on mount ── */
  /* DISABLED — causes session clearing race condition.
     Token validity is checked on every API call via the 401 interceptor.
  useEffect(() => { ... }, []); */

  const _clearSession = () => {
    authLog('CLEAR SESSION called', { stack: new Error().stack?.split('\n')[2]?.trim() });
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  };

  /* ── Login ── */
  const login = async (credentials) => {
    try {
      authLog('LOGIN: attempting', { username: credentials.username });
      const res = await api.post('/api/auth/signin', credentials);
      authLog('LOGIN: response received', {
        status: res.status,
        hasToken: !!res.data?.token,
        hasUser: !!res.data?.user,
        dataKeys: Object.keys(res.data || {})
      });
      const { token: newToken, user: userData } = res.data;
      if (!newToken) {
        authLog('LOGIN: ERROR - no token in response', { data: res.data });
        return { success: false, message: 'Server did not return a token.' };
      }
      setToken(newToken);
      setUser(userData);
      localStorage.setItem('token', newToken);
      localStorage.setItem('user', JSON.stringify(userData));
      authLog('LOGIN: saved to localStorage', {
        username: userData?.username,
        tokenPreview: newToken.substring(0, 20) + '...'
      });
      return { success: true, user: userData };
    } catch (err) {
      authLog('LOGIN: error', {
        status: err.response?.status,
        message: err.response?.data?.message || err.message
      });
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
