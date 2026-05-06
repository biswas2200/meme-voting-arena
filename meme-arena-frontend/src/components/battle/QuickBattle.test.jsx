import React from 'react';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import QuickBattle from './QuickBattle';

// ── Mock api.js ──────────────────────────────────────────────────────────────
vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

// ── Mock SockJS ──────────────────────────────────────────────────────────────
vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation(() => ({})),
}));

// ── Mock @stomp/stompjs ──────────────────────────────────────────────────────
// We capture the subscription callback so tests can push WS messages manually.
let capturedSubscriptionCallback = null;

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(function (config) {
    this.activate = vi.fn().mockImplementation(() => {
      // Immediately invoke onConnect so the subscription is registered synchronously
      if (config.onConnect) config.onConnect();
    });
    this.deactivate = vi.fn();
    this.subscribe = vi.fn().mockImplementation((_topic, cb) => {
      capturedSubscriptionCallback = cb;
      return { unsubscribe: vi.fn() };
    });
  }),
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

// ── Helpers ──────────────────────────────────────────────────────────────────

import api from '../../services/api';

const PAIR_1 = {
  pairId: 1,
  memeA: { id: 10, title: 'Meme Alpha', imageUrl: 'http://example.com/a.jpg', voteCount: 5 },
  memeB: { id: 20, title: 'Meme Beta',  imageUrl: 'http://example.com/b.jpg', voteCount: 3 },
};

const PAIR_2 = {
  pairId: 2,
  memeA: { id: 30, title: 'Meme Gamma', imageUrl: 'http://example.com/c.jpg', voteCount: 0 },
  memeB: { id: 40, title: 'Meme Delta', imageUrl: 'http://example.com/d.jpg', voteCount: 0 },
};

const VOTE_RESULT = {
  pairId: 1,
  memeAVotes: 6,
  memeBVotes: 3,
  chosenMemeId: 10,
};

// Simulate a WebSocket message arriving on /topic/battle/quick
function sendWsMessage(payload) {
  if (capturedSubscriptionCallback) {
    capturedSubscriptionCallback({ body: JSON.stringify(payload) });
  }
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('QuickBattle', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedSubscriptionCallback = null;

    // Default: GET /api/battle/quick/pair returns PAIR_1
    api.get.mockResolvedValue({ data: PAIR_1 });
    // Default: POST /api/battle/vote/quick returns VOTE_RESULT
    api.post.mockResolvedValue({ data: VOTE_RESULT });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // ── Test 1: renders pair with two memes ─────────────────────────────────

  it('renders pair with two memes', async () => {
    render(<QuickBattle />);

    // Both meme titles should appear after the API call resolves
    await waitFor(() => {
      expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      expect(screen.getByText('Meme Beta')).toBeInTheDocument();
    });

    // Both meme images should be present
    expect(screen.getByAltText('Meme Alpha')).toBeInTheDocument();
    expect(screen.getByAltText('Meme Beta')).toBeInTheDocument();

    // Vote counts from the initial pair should be displayed
    expect(screen.getByText('5')).toBeInTheDocument(); // memeA voteCount
    expect(screen.getByText('3')).toBeInTheDocument(); // memeB voteCount

    // Vote buttons should be present and enabled
    expect(screen.getByRole('button', { name: /vote for meme alpha/i })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: /vote for meme beta/i })).not.toBeDisabled();
  });

  // ── Test 2: disables voting controls after vote ──────────────────────────

  it('disables voting controls after vote', async () => {
    const user = userEvent.setup();
    render(<QuickBattle />);

    // Wait for pair to load
    await waitFor(() => {
      expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
    });

    const voteButtonA = screen.getByRole('button', { name: /vote for meme alpha/i });
    expect(voteButtonA).not.toBeDisabled();

    // Click the vote button for meme A
    await user.click(voteButtonA);

    // After voting, both vote buttons should be disabled
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /vote for meme alpha/i })).toBeDisabled();
      expect(screen.getByRole('button', { name: /vote for meme beta/i })).toBeDisabled();
    });
  });

  // ── Test 3: shows countdown animation after vote ─────────────────────────
  // Strategy: render and load the pair first (real timers), then switch to fake
  // timers so the 1500ms setTimeout doesn't fire, then vote and check countdown.

  it('shows countdown animation after vote', async () => {
    render(<QuickBattle />);

    // Wait for pair to load with real timers
    await waitFor(() => {
      expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
    });

    // Now switch to fake timers to freeze the 1500ms auto-advance
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });

    // Click vote button and flush the POST promise
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /vote for meme alpha/i }));
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    // Countdown indicator should appear after voting (before the 1500ms fires)
    expect(screen.getByText(/loading next pair/i)).toBeInTheDocument();
  });

  // ── Test 4: auto-fetches next pair after 1.5 seconds ────────────────────
  // Strategy: render and load the pair first (real timers), then switch to fake
  // timers, vote, advance 1500ms, and verify the second GET was called.

  it('auto-fetches next pair after 1.5 seconds', async () => {
    // Second GET call returns PAIR_2
    api.get
      .mockResolvedValueOnce({ data: PAIR_1 })
      .mockResolvedValueOnce({ data: PAIR_2 });

    render(<QuickBattle />);

    // Wait for first pair to load with real timers
    await waitFor(() => {
      expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
    });

    // api.get should have been called once so far (initial fetch)
    expect(api.get).toHaveBeenCalledTimes(1);

    // Switch to fake timers to control the 1500ms auto-advance
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });

    // Trigger vote and flush the POST promise
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /vote for meme alpha/i }));
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    // Advance fake timers by 1500ms to trigger auto-advance
    await act(async () => {
      vi.advanceTimersByTime(1500);
      // Flush the next GET promise
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    // api.get should now have been called a second time for the next pair
    expect(api.get).toHaveBeenCalledTimes(2);
    expect(api.get).toHaveBeenLastCalledWith('/api/battle/quick/pair');
  });

  // ── Test 5: updates vote counts on WebSocket message ────────────────────

  it('updates vote counts on WebSocket message', async () => {
    render(<QuickBattle />);

    // Wait for pair to load
    await waitFor(() => {
      expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
    });

    // Initial vote counts: 5 and 3
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();

    // The STOMP mock auto-triggers onConnect on activate(), which registers the
    // subscription and sets capturedSubscriptionCallback. Send a WS message.
    act(() => {
      sendWsMessage({
        pairId: PAIR_1.pairId,
        memeAId: PAIR_1.memeA.id,
        memeBId: PAIR_1.memeB.id,
        votesA: 10,
        votesB: 7,
      });
    });

    // Vote counts should update to reflect the WebSocket message
    await waitFor(() => {
      expect(screen.getByText('10')).toBeInTheDocument();
      expect(screen.getByText('7')).toBeInTheDocument();
    });
  });
});
