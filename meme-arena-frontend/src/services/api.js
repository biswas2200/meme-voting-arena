/**
 * Centralized Axios instance for all API calls.
 * 
 * - Base URL from REACT_APP_API_URL env variable
 * - Automatically attaches JWT token from localStorage
 * - Handles 401 responses by clearing token and redirecting to login
 */

import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000, // 30 seconds
});

// Request interceptor — attach JWT token if present
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor — handle 401 (unauthorized)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname;
      const hadToken = !!localStorage.getItem('token');

      // Log to persistent storage so it survives the redirect
      try {
        const existing = JSON.parse(localStorage.getItem('_authLog') || '[]');
        existing.push({
          t: new Date().toISOString(),
          msg: 'API 401 intercepted',
          data: {
            url: error.config?.url,
            method: error.config?.method,
            currentPath,
            hadToken,
            responseData: error.response?.data
          }
        });
        localStorage.setItem('_authLog', JSON.stringify(existing));
      } catch {}

      console.warn('[API] 401 on', error.config?.url, '| hadToken:', hadToken, '| path:', currentPath);

      // Don't redirect if already on auth pages
      if (currentPath.match(/^\/(login|register)/)) {
        return Promise.reject(error);
      }

      // Only clear session and redirect if we actually had a token
      if (hadToken) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.replace('/login');
      }
    }
    return Promise.reject(error);
  }
);

export default api;
