import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import TournamentCreate from './TournamentCreate';

// ── Mock react-router-dom ────────────────────────────────────────────────────
const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

// ── Mock api.js ──────────────────────────────────────────────────────────────
vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

// ── Mock AuthContext ─────────────────────────────────────────────────────────
vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, username: 'testuser' } }),
}));

// ── Mock NotificationContext ─────────────────────────────────────────────────
const mockShowNotification = vi.fn();
vi.mock('../contexts/NotificationContext', () => ({
  useNotification: () => ({ showNotification: mockShowNotification }),
}));

// ── Mock framer-motion to avoid animation complexity in tests ────────────────
vi.mock('framer-motion', () => {
  const React = require('react');
  const motion = new Proxy(
    {},
    {
      get: (_target, tag) =>
        React.forwardRef(({ children, ...props }, ref) => {
          const {
            initial, animate, exit, variants, transition, layout,
            whileHover, whileTap, ...rest
          } = props;
          return React.createElement(tag, { ...rest, ref }, children);
        }),
    }
  );
  return {
    motion,
    AnimatePresence: ({ children }) => React.createElement(React.Fragment, null, children),
  };
});

// ── Mock CSS imports ─────────────────────────────────────────────────────────
vi.mock('../styles/CommonPages.css', () => ({}));
vi.mock('../styles/TournamentCreate.css', () => ({}));

// ── Helpers ──────────────────────────────────────────────────────────────────

import api from '../services/api';

// Build a list of N meme objects with sequential IDs
function buildMemes(count) {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    title: `Meme ${i + 1}`,
    imageUrl: `http://example.com/meme${i + 1}.jpg`,
  }));
}

const MEMES_20 = buildMemes(20);

// Render the component and wait for the meme gallery to load
async function renderAndLoad(memes = MEMES_20) {
  api.get.mockResolvedValue({ data: memes });
  render(<TournamentCreate />);
  // Wait for the gallery to finish loading (spinner disappears, memes appear)
  await waitFor(() => {
    expect(screen.getByText('Meme 1')).toBeInTheDocument();
  });
}

// Set the tournament name input value atomically (avoids character-by-character timing issues)
function setName(value) {
  const input = screen.getByLabelText(/tournament name/i);
  fireEvent.change(input, { target: { value } });
}

// Click N meme cards by their aria-label (exact match to avoid "Meme 1" matching "Meme 10")
async function selectMemes(user, count) {
  for (let i = 1; i <= count; i++) {
    const btn = screen.getByRole('button', { name: `Select Meme ${i}` });
    await user.click(btn);
  }
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('TournamentCreate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
    mockShowNotification.mockReset();
  });

  // ── Test 1: submit button disabled when 0 memes selected ────────────────

  it('disables submit button when no memes are selected', async () => {
    await renderAndLoad();

    const submitBtn = screen.getByRole('button', { name: /create tournament/i });
    expect(submitBtn).toBeDisabled();
  });

  // ── Test 2: submit button disabled for invalid counts (not 8 or 16) ─────

  it('disables submit button when an invalid meme count is selected (e.g. 5)', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 5);

    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });

  it('disables submit button when 7 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 7);

    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });

  it('disables submit button when 9 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 9);

    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });

  it('disables submit button when 15 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 15);

    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });

  it('disables submit button when 17 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad(buildMemes(20));

    setName('My Tournament');
    await selectMemes(user, 17);

    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });

  // ── Test 3: submit button enabled for exactly 8 memes ───────────────────

  it('enables submit button when exactly 8 memes are selected and name is provided', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 8);

    expect(screen.getByRole('button', { name: /create tournament/i })).not.toBeDisabled();
  });

  // ── Test 4: submit button enabled for exactly 16 memes ──────────────────

  it('enables submit button when exactly 16 memes are selected and name is provided', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 16);

    expect(screen.getByRole('button', { name: /create tournament/i })).not.toBeDisabled();
  });

  // ── Test 5: submit button disabled when name is empty (even with 8 memes) ─

  it('disables submit button when name is empty even if 8 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    // Do NOT set a name — leave it empty
    await selectMemes(user, 8);

    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });

  // ── Test 6: shows count hint for invalid selection ───────────────────────

  it('shows a hint message when fewer than 8 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    await selectMemes(user, 3);

    // Should show a hint about needing more memes
    expect(screen.getByText(/select.*more/i)).toBeInTheDocument();
  });

  it('shows a hint message when between 8 and 16 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    await selectMemes(user, 10);

    // Should show a hint about reaching 16 or deselecting to reach 8
    expect(screen.getByText(/select.*more.*16|deselect.*8/i)).toBeInTheDocument();
  });

  it('shows a hint message when more than 16 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad(buildMemes(20));

    await selectMemes(user, 17);

    // Should show a hint about too many selected
    expect(screen.getByText(/too many selected/i)).toBeInTheDocument();
  });

  // ── Test 7: shows "ready to submit" message for valid counts ────────────

  it('shows ready-to-submit message when exactly 8 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    await selectMemes(user, 8);

    expect(screen.getByText(/8 memes selected.*ready to submit/i)).toBeInTheDocument();
  });

  it('shows ready-to-submit message when exactly 16 memes are selected', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    await selectMemes(user, 16);

    expect(screen.getByText(/16 memes selected.*ready to submit/i)).toBeInTheDocument();
  });

  // ── Test 8: submits correct payload to API ───────────────────────────────

  it('submits correct payload with name, memeIds, and roundDurationHours', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValue({ data: { id: 42 } });
    await renderAndLoad();

    // Fill in tournament name atomically
    setName('Epic Meme Battle');

    // Select exactly 8 memes (IDs 1–8)
    await selectMemes(user, 8);

    // Default round duration is 24h — submit
    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/battle/tournaments', {
        name: 'Epic Meme Battle',
        memeIds: expect.arrayContaining([1, 2, 3, 4, 5, 6, 7, 8]),
        roundDurationHours: 24,
      });
    });

    // Verify the memeIds array has exactly 8 entries
    const [, payload] = api.post.mock.calls[0];
    expect(payload.memeIds).toHaveLength(8);
  });

  it('submits with roundDurationHours = 1 when 1 Hour is selected', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValue({ data: { id: 43 } });
    await renderAndLoad();

    setName('Fast Battle');
    await selectMemes(user, 8);

    // Select 1 Hour radio
    await user.click(screen.getByRole('radio', { name: /1 hour/i }));

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/battle/tournaments', expect.objectContaining({
        roundDurationHours: 1,
      }));
    });
  });

  it('submits with roundDurationHours = 6 when 6 Hours is selected', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValue({ data: { id: 44 } });
    await renderAndLoad();

    setName('Medium Battle');
    await selectMemes(user, 8);

    // Select 6 Hours radio
    await user.click(screen.getByRole('radio', { name: /6 hours/i }));

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/battle/tournaments', expect.objectContaining({
        roundDurationHours: 6,
      }));
    });
  });

  it('submits with 16 meme IDs when 16 memes are selected', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValue({ data: { id: 45 } });
    await renderAndLoad();

    setName('Big Tournament');
    await selectMemes(user, 16);

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      const [, payload] = api.post.mock.calls[0];
      expect(payload.memeIds).toHaveLength(16);
    });
  });

  it('trims whitespace from tournament name before submitting', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValue({ data: { id: 46 } });
    await renderAndLoad();

    // Set name with surrounding whitespace — component trims on submit
    setName('  Trimmed Name  ');
    await selectMemes(user, 8);

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      const [, payload] = api.post.mock.calls[0];
      expect(payload.name).toBe('Trimmed Name');
    });
  });

  // ── Test 9: shows validation error from API response ────────────────────

  it('shows API error message when submission fails', async () => {
    const user = userEvent.setup();
    api.post.mockRejectedValue({
      response: { data: { message: 'Tournament requires exactly 8 or 16 memes' } },
    });
    await renderAndLoad();

    setName('Bad Tournament');
    await selectMemes(user, 8);

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Tournament requires exactly 8 or 16 memes'
      );
    });
  });

  it('shows generic error message when API returns no message', async () => {
    const user = userEvent.setup();
    api.post.mockRejectedValue({ response: { data: {} } });
    await renderAndLoad();

    setName('Bad Tournament');
    await selectMemes(user, 8);

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        /failed to create tournament/i
      );
    });
  });

  it('shows duplicate meme error from API', async () => {
    const user = userEvent.setup();
    api.post.mockRejectedValue({
      response: { data: { message: 'Duplicate meme IDs are not allowed' } },
    });
    await renderAndLoad();

    setName('Dup Tournament');
    await selectMemes(user, 8);

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Duplicate meme IDs are not allowed'
      );
    });
  });

  // ── Test 10: navigates to tournament list on success ────────────────────

  it('navigates to /battle/tournaments after successful submission', async () => {
    const user = userEvent.setup();
    api.post.mockResolvedValue({ data: { id: 99 } });
    await renderAndLoad();

    setName('Success Tournament');
    await selectMemes(user, 8);

    await user.click(screen.getByRole('button', { name: /create tournament/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/battle/tournaments');
    });
  });

  // ── Test 11: meme gallery loading and error states ───────────────────────

  it('shows loading state while fetching memes', () => {
    // Never resolve the promise so we stay in loading state
    api.get.mockReturnValue(new Promise(() => {}));
    render(<TournamentCreate />);

    // Loading spinner should be visible
    expect(document.querySelector('.loading-spinner')).toBeInTheDocument();
  });

  it('shows error state when meme gallery fails to load', async () => {
    api.get.mockRejectedValue(new Error('Network error'));
    render(<TournamentCreate />);

    await waitFor(() => {
      expect(screen.getByText(/failed to load meme gallery/i)).toBeInTheDocument();
    });
  });

  // ── Test 12: meme toggle (select / deselect) ─────────────────────────────

  it('toggles meme selection on click', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    const meme1Btn = screen.getByRole('button', { name: 'Select Meme 1' });

    // Initially not selected
    expect(meme1Btn).toHaveAttribute('aria-pressed', 'false');

    // Click to select
    await user.click(meme1Btn);
    expect(screen.getByRole('button', { name: 'Deselect Meme 1' })).toHaveAttribute('aria-pressed', 'true');

    // Click again to deselect
    await user.click(screen.getByRole('button', { name: 'Deselect Meme 1' }));
    expect(screen.getByRole('button', { name: 'Select Meme 1' })).toHaveAttribute('aria-pressed', 'false');
  });

  // ── Test 13: clear selection button ─────────────────────────────────────

  it('clears all selected memes when "Clear selection" is clicked', async () => {
    const user = userEvent.setup();
    await renderAndLoad();

    setName('My Tournament');
    await selectMemes(user, 8);

    // "Clear selection" button should appear
    const clearBtn = screen.getByRole('button', { name: /clear selection/i });
    await user.click(clearBtn);

    // Count should reset to 0 — submit button should be disabled again
    expect(screen.getByRole('button', { name: /create tournament/i })).toBeDisabled();
  });
});
