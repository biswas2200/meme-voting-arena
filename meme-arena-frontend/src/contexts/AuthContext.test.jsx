import React from 'react';
import { render, screen, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';

// ── Mock api.js ──────────────────────────────────────────────────────────────
vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import api from '../services/api';

// ── Helper component that exposes AuthContext values ─────────────────────────
function AuthConsumer({ onRender }) {
  const auth = useAuth();
  onRender(auth);
  return (
    <div>
      <span data-testid="username">{auth.user?.username ?? 'null'}</span>
      <span data-testid="token">{auth.token ?? 'null'}</span>
      <span data-testid="loading">{String(auth.loading)}</span>
      <span data-testid="isAuthenticated">{String(auth.isAuthenticated)}</span>
    </div>
  );
}

function renderWithAuth(onRender = () => {}) {
  return render(
    <AuthProvider>
      <AuthConsumer onRender={onRender} />
    </AuthProvider>
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  // ── useAuth outside provider ──────────────────────────────────────────────

  it('throws when useAuth is called outside AuthProvider', () => {
    // Suppress the expected error output
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => {
      render(
        <AuthConsumer onRender={() => {}} />
      );
    }).toThrow('useAuth must be used within an AuthProvider');
    spy.mockRestore();
  });

  // ── Initial state — no saved session ─────────────────────────────────────

  it('starts with user=null and token=null when localStorage is empty', async () => {
    api.get.mockResolvedValue({ data: {} }); // won't be called, but safe stub

    renderWithAuth();

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    expect(screen.getByTestId('username').textContent).toBe('null');
    expect(screen.getByTestId('token').textContent).toBe('null');
    expect(screen.getByTestId('isAuthenticated').textContent).toBe('false');
  });

  // ── Session restoration ───────────────────────────────────────────────────

  it('restores user from localStorage when token and user are saved', async () => {
    const savedUser = { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER' };
    localStorage.setItem('token', 'saved-jwt-token');
    localStorage.setItem('user', JSON.stringify(savedUser));

    // Profile refresh returns fresh data
    api.get.mockResolvedValue({
      data: { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER' },
    });

    renderWithAuth();

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    expect(screen.getByTestId('username').textContent).toBe('alice');
    expect(screen.getByTestId('token').textContent).toBe('saved-jwt-token');
    expect(screen.getByTestId('isAuthenticated').textContent).toBe('true');
  });

  it('clears session silently when profile refresh fails (expired token)', async () => {
    localStorage.setItem('token', 'expired-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    api.get.mockRejectedValue(new Error('Unauthorized'));

    renderWithAuth();

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    expect(screen.getByTestId('username').textContent).toBe('null');
    expect(screen.getByTestId('token').textContent).toBe('null');
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('updates user with fresh data from profile endpoint on restore', async () => {
    localStorage.setItem('token', 'valid-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'old_name' }));

    api.get.mockResolvedValue({
      data: { id: 1, username: 'new_name', email: 'new@example.com', role: 'ADMIN' },
    });

    renderWithAuth();

    await waitFor(() => {
      expect(screen.getByTestId('username').textContent).toBe('new_name');
    });
  });

  // ── login ─────────────────────────────────────────────────────────────────

  it('login — sets user and token on success', async () => {
    api.get.mockResolvedValue({ data: {} }); // profile refresh on mount (no saved session)

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.post.mockResolvedValue({
      data: {
        token: 'new-jwt-token',
        user: { id: 2, username: 'bob', email: 'bob@example.com', role: 'USER' },
      },
    });

    await act(async () => {
      const result = await authRef.login({ username: 'bob', password: 'secret' });
      expect(result.success).toBe(true);
      expect(result.user.username).toBe('bob');
    });

    expect(screen.getByTestId('username').textContent).toBe('bob');
    expect(screen.getByTestId('token').textContent).toBe('new-jwt-token');
    expect(screen.getByTestId('isAuthenticated').textContent).toBe('true');
  });

  it('login — persists token and user to localStorage on success', async () => {
    api.get.mockResolvedValue({ data: {} });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.post.mockResolvedValue({
      data: {
        token: 'persisted-token',
        user: { id: 3, username: 'carol', email: 'carol@example.com', role: 'USER' },
      },
    });

    await act(async () => {
      await authRef.login({ username: 'carol', password: 'pass' });
    });

    expect(localStorage.getItem('token')).toBe('persisted-token');
    expect(JSON.parse(localStorage.getItem('user')).username).toBe('carol');
  });

  it('login — returns success=false and message on failure', async () => {
    api.get.mockResolvedValue({ data: {} });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.post.mockRejectedValue({
      response: { data: { message: 'Invalid username or password!' } },
    });

    let result;
    await act(async () => {
      result = await authRef.login({ username: 'wrong', password: 'wrong' });
    });

    expect(result.success).toBe(false);
    expect(result.message).toBe('Invalid username or password!');
    expect(screen.getByTestId('username').textContent).toBe('null');
  });

  it('login — returns generic message when API provides no message', async () => {
    api.get.mockResolvedValue({ data: {} });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.post.mockRejectedValue({ response: { data: {} } });

    let result;
    await act(async () => {
      result = await authRef.login({ username: 'x', password: 'y' });
    });

    expect(result.success).toBe(false);
    expect(result.message).toBe('Login failed. Please try again.');
  });

  // ── register ──────────────────────────────────────────────────────────────

  it('register — sets user and token on success', async () => {
    api.get.mockResolvedValue({ data: {} });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.post.mockResolvedValue({
      data: {
        token: 'reg-token',
        user: { id: 4, username: 'dave', email: 'dave@example.com', role: 'USER' },
      },
    });

    await act(async () => {
      const result = await authRef.register({
        username: 'dave',
        email: 'dave@example.com',
        password: 'pass123',
      });
      expect(result.success).toBe(true);
      expect(result.user.username).toBe('dave');
    });

    expect(screen.getByTestId('username').textContent).toBe('dave');
    expect(screen.getByTestId('token').textContent).toBe('reg-token');
  });

  it('register — returns success=false on failure', async () => {
    api.get.mockResolvedValue({ data: {} });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.post.mockRejectedValue({
      response: { data: { message: 'Username is already taken!' } },
    });

    let result;
    await act(async () => {
      result = await authRef.register({ username: 'taken', email: 'x@x.com', password: 'p' });
    });

    expect(result.success).toBe(false);
    expect(result.message).toBe('Username is already taken!');
  });

  // ── logout ────────────────────────────────────────────────────────────────

  it('logout — clears user, token, and localStorage', async () => {
    localStorage.setItem('token', 'active-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    api.get.mockResolvedValue({
      data: { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER' },
    });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('username').textContent).toBe('alice');
    });

    act(() => {
      authRef.logout();
    });

    expect(screen.getByTestId('username').textContent).toBe('null');
    expect(screen.getByTestId('token').textContent).toBe('null');
    expect(screen.getByTestId('isAuthenticated').textContent).toBe('false');
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  // ── refreshUserData ───────────────────────────────────────────────────────

  it('refreshUserData — updates user state with fresh data from server', async () => {
    localStorage.setItem('token', 'valid-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    // First call: session restore
    api.get.mockResolvedValueOnce({
      data: { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER' },
    });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('username').textContent).toBe('alice');
    });

    // Second call: refreshUserData
    api.get.mockResolvedValueOnce({
      data: { id: 1, username: 'alice_updated', email: 'alice@example.com', role: 'ADMIN' },
    });

    await act(async () => {
      const fresh = await authRef.refreshUserData();
      expect(fresh.username).toBe('alice_updated');
      expect(fresh.role).toBe('ADMIN');
    });

    expect(screen.getByTestId('username').textContent).toBe('alice_updated');
  });

  it('refreshUserData — updates localStorage with fresh user data', async () => {
    localStorage.setItem('token', 'valid-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    api.get.mockResolvedValueOnce({
      data: { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER' },
    });

    let authRef;
    renderWithAuth((auth) => { authRef = auth; });

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    api.get.mockResolvedValueOnce({
      data: { id: 1, username: 'alice_v2', email: 'alice@example.com', role: 'USER' },
    });

    await act(async () => {
      await authRef.refreshUserData();
    });

    const stored = JSON.parse(localStorage.getItem('user'));
    expect(stored.username).toBe('alice_v2');
  });

  // ── isAuthenticated ───────────────────────────────────────────────────────

  it('isAuthenticated is true when user is set', async () => {
    localStorage.setItem('token', 'valid-token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'alice' }));

    api.get.mockResolvedValue({
      data: { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER' },
    });

    renderWithAuth();

    await waitFor(() => {
      expect(screen.getByTestId('isAuthenticated').textContent).toBe('true');
    });
  });

  it('isAuthenticated is false when user is null', async () => {
    renderWithAuth();

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    expect(screen.getByTestId('isAuthenticated').textContent).toBe('false');
  });
});
