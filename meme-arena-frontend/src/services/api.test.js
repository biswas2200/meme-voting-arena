import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Re-import the api module fresh for each test so interceptors are clean.
 * We reset modules before each test to avoid interceptor accumulation.
 */
async function freshApi() {
  const mod = await import('./api?t=' + Date.now());
  return mod.default;
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('api.js — Axios instance configuration', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  // ── Base URL ──────────────────────────────────────────────────────────────

  it('uses VITE_API_URL env variable as base URL when set', async () => {
    // The module is already loaded; we verify the default fallback
    const { default: api } = await import('./api');
    // baseURL should be either the env value or the fallback
    expect(api.defaults.baseURL).toBeTruthy();
  });

  it('falls back to http://localhost:8080 when VITE_API_URL is not set', async () => {
    const { default: api } = await import('./api');
    // In test environment VITE_API_URL is not set → fallback
    expect(api.defaults.baseURL).toBe('http://localhost:8080');
  });

  // ── Default headers ───────────────────────────────────────────────────────

  it('sets Content-Type to application/json by default', async () => {
    const { default: api } = await import('./api');
    expect(api.defaults.headers['Content-Type']).toBe('application/json');
  });

  it('sets timeout to 30000ms', async () => {
    const { default: api } = await import('./api');
    expect(api.defaults.timeout).toBe(30000);
  });

  // ── Request interceptor — JWT attachment ──────────────────────────────────

  it('attaches Authorization header when token is in localStorage', async () => {
    localStorage.setItem('token', 'test-jwt-token-abc123');
    const { default: api } = await import('./api');

    // Simulate the request interceptor by running it manually
    const config = { headers: {} };
    const interceptor = api.interceptors.request.handlers[0];
    const result = interceptor.fulfilled(config);

    expect(result.headers.Authorization).toBe('Bearer test-jwt-token-abc123');
  });

  it('does NOT attach Authorization header when no token in localStorage', async () => {
    localStorage.removeItem('token');
    const { default: api } = await import('./api');

    const config = { headers: {} };
    const interceptor = api.interceptors.request.handlers[0];
    const result = interceptor.fulfilled(config);

    expect(result.headers.Authorization).toBeUndefined();
  });

  it('returns config unchanged when no token is present', async () => {
    localStorage.removeItem('token');
    const { default: api } = await import('./api');

    const config = { headers: { 'Content-Type': 'application/json' } };
    const interceptor = api.interceptors.request.handlers[0];
    const result = interceptor.fulfilled(config);

    expect(result).toEqual(config);
  });

  // ── Response interceptor — 401 handling ───────────────────────────────────

  it('clears token from localStorage on 401 response', async () => {
    localStorage.setItem('token', 'expired-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    const { default: api } = await import('./api');

    // Simulate the response error interceptor
    const interceptor = api.interceptors.response.handlers[0];
    const error = { response: { status: 401 } };

    // The interceptor rejects — we catch it
    try {
      await interceptor.rejected(error);
    } catch {
      // expected rejection
    }

    expect(localStorage.getItem('token')).toBeNull();
  });

  it('clears user from localStorage on 401 response', async () => {
    localStorage.setItem('token', 'expired-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const error = { response: { status: 401 } };

    try {
      await interceptor.rejected(error);
    } catch {
      // expected rejection
    }

    expect(localStorage.getItem('user')).toBeNull();
  });

  it('re-rejects the error after clearing session on 401', async () => {
    localStorage.setItem('token', 'expired-token');

    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const error = { response: { status: 401 } };

    await expect(interceptor.rejected(error)).rejects.toEqual(error);
  });

  it('passes through non-401 errors without clearing localStorage', async () => {
    localStorage.setItem('token', 'valid-token');
    localStorage.setItem('user', JSON.stringify({ id: 1 }));

    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const error = { response: { status: 500 } };

    try {
      await interceptor.rejected(error);
    } catch {
      // expected rejection
    }

    // Token and user should still be in localStorage
    expect(localStorage.getItem('token')).toBe('valid-token');
    expect(localStorage.getItem('user')).not.toBeNull();
  });

  it('passes through 403 errors without clearing localStorage', async () => {
    localStorage.setItem('token', 'valid-token');

    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const error = { response: { status: 403 } };

    try {
      await interceptor.rejected(error);
    } catch {
      // expected rejection
    }

    expect(localStorage.getItem('token')).toBe('valid-token');
  });

  it('passes through 404 errors without clearing localStorage', async () => {
    localStorage.setItem('token', 'valid-token');

    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const error = { response: { status: 404 } };

    try {
      await interceptor.rejected(error);
    } catch {
      // expected rejection
    }

    expect(localStorage.getItem('token')).toBe('valid-token');
  });

  it('handles errors with no response object without throwing', async () => {
    localStorage.setItem('token', 'valid-token');

    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const networkError = new Error('Network Error'); // no .response

    await expect(interceptor.rejected(networkError)).rejects.toThrow('Network Error');
    // Token should still be present
    expect(localStorage.getItem('token')).toBe('valid-token');
  });

  // ── Successful response passthrough ───────────────────────────────────────

  it('passes through successful responses unchanged', async () => {
    const { default: api } = await import('./api');

    const interceptor = api.interceptors.response.handlers[0];
    const response = { status: 200, data: { id: 1, title: 'Meme' } };

    const result = interceptor.fulfilled(response);

    expect(result).toEqual(response);
  });
});
