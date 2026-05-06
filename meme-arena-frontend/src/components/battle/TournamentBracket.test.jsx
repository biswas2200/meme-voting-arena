import React from 'react';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import TournamentBracket from './TournamentBracket';

// ── Mock react-router-dom ────────────────────────────────────────────────────
vi.mock('react-router-dom', () => ({
  useParams: () => ({ id: '1' }),
  Link: ({ children, to, ...rest }) =>
    React.createElement('a', { href: to, ...rest }, children),
}));

// ── Mock api.js ──────────────────────────────────────────────────────────────
vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

// ── Mock AuthContext ─────────────────────────────────────────────────────────
// Default: authenticated user. Individual tests override via mockAuthUser.
let mockUser = { id: 1, username: 'testuser' };
vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({ user: mockUser }),
}));

// ── Mock SockJS ──────────────────────────────────────────────────────────────
vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation(() => ({})),
}));

// ── Mock @stomp/stompjs ──────────────────────────────────────────────────────
// Capture the subscription callback so tests can push WS messages manually.
let capturedWsCallback = null;

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(function (config) {
    this.activate = vi.fn().mockImplementation(() => {
      // Immediately invoke onConnect so the subscription is registered synchronously
      if (config.onConnect) config.onConnect();
    });
    this.deactivate = vi.fn();
    this.subscribe = vi.fn().mockImplementation((_topic, cb) => {
      capturedWsCallback = cb;
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

// Push a WebSocket message to the captured subscription callback
function sendWsMessage(payload) {
  if (capturedWsCallback) {
    capturedWsCallback({ body: JSON.stringify(payload) });
  }
}

// ── Fixture data ─────────────────────────────────────────────────────────────

const MEME_A = { id: 10, title: 'Meme Alpha', imageUrl: 'http://example.com/a.jpg', voteCount: 50 };
const MEME_B = { id: 20, title: 'Meme Beta',  imageUrl: 'http://example.com/b.jpg', voteCount: 30 };
const MEME_C = { id: 30, title: 'Meme Gamma', imageUrl: 'http://example.com/c.jpg', voteCount: 40 };
const MEME_D = { id: 40, title: 'Meme Delta', imageUrl: 'http://example.com/d.jpg', voteCount: 20 };

/**
 * Build a minimal active tournament with 4 memes (2 matchups in round 1).
 * currentRound = 1, status = ACTIVE.
 */
function buildActiveTournament(overrides = {}) {
  return {
    id: 1,
    name: 'Test Tournament',
    creator: 'creator_user',
    status: 'ACTIVE',
    roundDurationHours: 24,
    memeCount: 4,
    currentRound: 1,
    currentRoundEndsAt: new Date(Date.now() + 3_600_000).toISOString(), // 1 hour from now
    champion: null,
    createdAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    matchups: [
      {
        id: 101,
        roundNumber: 1,
        bracketPosition: 1,
        memeA: MEME_A,
        memeB: MEME_B,
        votesA: 5,
        votesB: 3,
        winner: null,
      },
      {
        id: 102,
        roundNumber: 1,
        bracketPosition: 2,
        memeA: MEME_C,
        memeB: MEME_D,
        votesA: 2,
        votesB: 7,
        winner: null,
      },
    ],
    ...overrides,
  };
}

/**
 * Build a completed tournament with 2 rounds (4 memes).
 * Round 1 has 2 matchups (both complete), Round 2 (Final) has 1 matchup (complete).
 * Champion is MEME_A.
 */
function buildCompletedTournament() {
  return {
    id: 2,
    name: 'Finished Tournament',
    creator: 'creator_user',
    status: 'COMPLETED',
    roundDurationHours: 6,
    memeCount: 4,
    currentRound: null,
    currentRoundEndsAt: null,
    champion: MEME_A,
    createdAt: '2026-01-01T00:00:00Z',
    completedAt: '2026-01-02T12:00:00Z',
    matchups: [
      {
        id: 101,
        roundNumber: 1,
        bracketPosition: 1,
        memeA: MEME_A,
        memeB: MEME_B,
        votesA: 10,
        votesB: 4,
        winner: MEME_A,
      },
      {
        id: 102,
        roundNumber: 1,
        bracketPosition: 2,
        memeA: MEME_C,
        memeB: MEME_D,
        votesA: 3,
        votesB: 8,
        winner: MEME_D,
      },
      {
        id: 201,
        roundNumber: 2,
        bracketPosition: 1,
        memeA: MEME_A,
        memeB: MEME_D,
        votesA: 15,
        votesB: 6,
        winner: MEME_A,
      },
    ],
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('TournamentBracket', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedWsCallback = null;
    mockUser = { id: 1, username: 'testuser' };

    // Default: GET returns an active tournament
    api.get.mockResolvedValue({ data: buildActiveTournament() });
    // Default: POST vote returns a result
    api.post.mockResolvedValue({
      data: { matchupId: 101, memeAVotes: 6, memeBVotes: 3, chosenMemeId: 10 },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // ── Test 1: renders all rounds and matchups ──────────────────────────────

  describe('renders all rounds and matchups', () => {
    it('displays the tournament name in the header', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Test Tournament')).toBeInTheDocument();
      });
    });

    it('renders a section for each round', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        // Active tournament has 1 round. Since it's the only round it's also
        // the final, so the component labels it "Final" (isFinalRound = true).
        expect(screen.getByRole('region', { name: /final/i })).toBeInTheDocument();
      });
    });

    it('renders all matchup meme titles within each round', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
        expect(screen.getByText('Meme Beta')).toBeInTheDocument();
        expect(screen.getByText('Meme Gamma')).toBeInTheDocument();
        expect(screen.getByText('Meme Delta')).toBeInTheDocument();
      });
    });

    it('renders vote counts for each meme in a matchup', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        // Matchup 1: votesA=5, votesB=3; Matchup 2: votesA=2, votesB=7
        expect(screen.getByText('5')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByText('2')).toBeInTheDocument();
        expect(screen.getByText('7')).toBeInTheDocument();
      });
    });

    it('renders all rounds for a multi-round completed tournament', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        // Should have Round 1 and Final (round 2 = final for 4-meme bracket)
        expect(screen.getByRole('region', { name: /round 1/i })).toBeInTheDocument();
        expect(screen.getByRole('region', { name: /final/i })).toBeInTheDocument();
      });
    });

    it('renders all matchup memes across multiple rounds', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        // All 4 memes appear across the bracket (some appear in multiple rounds)
        expect(screen.getAllByText('Meme Alpha').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Meme Beta').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Meme Gamma').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Meme Delta').length).toBeGreaterThanOrEqual(1);
      });
    });

    it('shows "VS" separator between the two memes in each matchup', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        const vsElements = screen.getAllByText('VS');
        // 2 matchups → 2 VS separators
        expect(vsElements).toHaveLength(2);
      });
    });

    it('shows loading state before data arrives', () => {
      // Never resolve so we stay in loading state
      api.get.mockReturnValue(new Promise(() => {}));
      render(<TournamentBracket />);

      expect(screen.getByText(/loading bracket/i)).toBeInTheDocument();
    });

    it('shows error state when API call fails', async () => {
      api.get.mockRejectedValue({
        response: { status: 500, data: { message: 'Server error' } },
      });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      });
    });

    it('shows not-found error when tournament does not exist', async () => {
      api.get.mockRejectedValue({ response: { status: 404, data: {} } });
      render(<TournamentBracket />);

      await waitFor(() => {
        // The heading specifically (not the paragraph that also contains the phrase)
        expect(screen.getByRole('heading', { name: /tournament not found/i })).toBeInTheDocument();
      });
    });
  });

  // ── Test 2: shows countdown timer for active round ───────────────────────

  describe('shows countdown timer for active round', () => {
    it('displays a countdown timer when tournament is ACTIVE', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        // The countdown text should contain "Round ends in:"
        expect(screen.getByText(/round ends in:/i)).toBeInTheDocument();
      });
    });

    it('countdown includes hours, minutes, and seconds for a future timestamp', async () => {
      // Set endsAt 2 hours from now
      const endsAt = new Date(Date.now() + 2 * 3_600_000).toISOString();
      api.get.mockResolvedValue({ data: buildActiveTournament({ currentRoundEndsAt: endsAt }) });
      render(<TournamentBracket />);

      await waitFor(() => {
        const countdown = screen.getByText(/round ends in:/i);
        // Should contain hours (e.g. "1h" or "2h")
        expect(countdown.textContent).toMatch(/\dh/);
      });
    });

    it('shows "Round ending soon..." when the round end time is in the past', async () => {
      const pastEndsAt = new Date(Date.now() - 1000).toISOString();
      api.get.mockResolvedValue({
        data: buildActiveTournament({ currentRoundEndsAt: pastEndsAt }),
      });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Round ending soon...')).toBeInTheDocument();
      });
    });

    it('does NOT show a countdown timer when tournament is COMPLETED', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        // Champion banner should appear (confirms data loaded)
        expect(screen.getByText('Tournament Champion')).toBeInTheDocument();
      });

      // No countdown should be present
      expect(screen.queryByText(/round ends in:/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/round ending soon/i)).not.toBeInTheDocument();
    });

    it('does NOT show a countdown timer when tournament is PENDING_APPROVAL', async () => {
      api.get.mockResolvedValue({
        data: buildActiveTournament({
          status: 'PENDING_APPROVAL',
          currentRound: null,
          currentRoundEndsAt: null,
        }),
      });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Test Tournament')).toBeInTheDocument();
      });

      expect(screen.queryByText(/round ends in:/i)).not.toBeInTheDocument();
    });

    it('countdown timer has aria-live="polite" for accessibility', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        const timer = screen.getByText(/round ends in:/i).closest('[aria-live]');
        expect(timer).toHaveAttribute('aria-live', 'polite');
      });
    });
  });

  // ── Test 3: displays voting controls for active matchups ─────────────────

  describe('displays voting controls for active matchups', () => {
    it('shows vote buttons for each meme in the active round when user is authenticated', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        // Matchup 1: vote buttons for Meme Alpha and Meme Beta
        expect(screen.getByRole('button', { name: /vote for meme alpha/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /vote for meme beta/i })).toBeInTheDocument();
        // Matchup 2: vote buttons for Meme Gamma and Meme Delta
        expect(screen.getByRole('button', { name: /vote for meme gamma/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /vote for meme delta/i })).toBeInTheDocument();
      });
    });

    it('does NOT show vote buttons when user is not authenticated', async () => {
      mockUser = null; // unauthenticated
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      });

      expect(screen.queryByRole('button', { name: /vote for/i })).not.toBeInTheDocument();
    });

    it('does NOT show vote buttons for completed matchups', async () => {
      // Matchup 101 is complete (has a winner)
      const tournament = buildActiveTournament({
        matchups: [
          {
            id: 101,
            roundNumber: 1,
            bracketPosition: 1,
            memeA: MEME_A,
            memeB: MEME_B,
            votesA: 10,
            votesB: 4,
            winner: MEME_A, // complete
          },
        ],
      });
      api.get.mockResolvedValue({ data: tournament });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      });

      expect(screen.queryByRole('button', { name: /vote for/i })).not.toBeInTheDocument();
    });

    it('shows "Winner" badge on the winning meme of a completed matchup', async () => {
      const tournament = buildActiveTournament({
        matchups: [
          {
            id: 101,
            roundNumber: 1,
            bracketPosition: 1,
            memeA: MEME_A,
            memeB: MEME_B,
            votesA: 10,
            votesB: 4,
            winner: MEME_A,
          },
        ],
      });
      api.get.mockResolvedValue({ data: tournament });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByLabelText('Winner')).toBeInTheDocument();
      });
    });

    it('disables vote buttons after the user casts a vote on a matchup', async () => {
      const user = userEvent.setup();
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /vote for meme alpha/i })).toBeInTheDocument();
      });

      // Vote for Meme Alpha in matchup 101
      await user.click(screen.getByRole('button', { name: /vote for meme alpha/i }));

      // Both vote buttons for matchup 101 should now be gone (matchup voted)
      await waitFor(() => {
        expect(screen.queryByRole('button', { name: /vote for meme alpha/i })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /vote for meme beta/i })).not.toBeInTheDocument();
      });
    });

    it('shows "You voted for [meme title]" indicator after voting', async () => {
      const user = userEvent.setup();
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /vote for meme alpha/i })).toBeInTheDocument();
      });

      await user.click(screen.getByRole('button', { name: /vote for meme alpha/i }));

      await waitFor(() => {
        // The voted indicator contains "You voted for" followed by the meme title
        const indicator = screen.getByText(/you voted for/i);
        expect(indicator).toBeInTheDocument();
        // The strong element inside the indicator should contain the meme title
        expect(indicator.closest('.tb-voted-indicator')).toHaveTextContent('Meme Alpha');
      });
    });

    it('updates vote counts in the matchup after a successful vote', async () => {
      const user = userEvent.setup();
      api.post.mockResolvedValue({
        data: { matchupId: 101, memeAVotes: 6, memeBVotes: 3, chosenMemeId: 10 },
      });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('5')).toBeInTheDocument(); // initial votesA
      });

      await user.click(screen.getByRole('button', { name: /vote for meme alpha/i }));

      await waitFor(() => {
        expect(screen.getByText('6')).toBeInTheDocument(); // updated votesA
      });
    });

    it('does NOT show vote buttons for non-active rounds', async () => {
      // Tournament with 2 rounds; currentRound = 2 (only round 2 is active)
      // Use MEME_C/MEME_D in round 2 to avoid duplicate meme titles across rounds
      const tournament = {
        ...buildActiveTournament(),
        currentRound: 2,
        matchups: [
          {
            id: 101,
            roundNumber: 1, // past round
            bracketPosition: 1,
            memeA: MEME_A,
            memeB: MEME_B,
            votesA: 10,
            votesB: 4,
            winner: MEME_A,
          },
          {
            id: 201,
            roundNumber: 2, // active round
            bracketPosition: 1,
            memeA: MEME_C,
            memeB: MEME_D,
            votesA: 0,
            votesB: 0,
            winner: null,
          },
        ],
      };
      api.get.mockResolvedValue({ data: tournament });
      render(<TournamentBracket />);

      await waitFor(() => {
        // Wait for data to load — all memes should be visible
        expect(screen.getAllByText('Meme Alpha').length).toBeGreaterThanOrEqual(1);
      });

      // Round 1 matchup (past, has winner) should have no vote buttons
      // Round 2 matchup (active) should have vote buttons for MEME_C and MEME_D
      const voteButtons = screen.getAllByRole('button', { name: /vote for/i });
      // Only the active round matchup should have vote buttons (2 buttons for 1 matchup)
      expect(voteButtons).toHaveLength(2);
      expect(screen.getByRole('button', { name: /vote for meme gamma/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /vote for meme delta/i })).toBeInTheDocument();
    });
  });

  // ── Test 4: updates vote counts on WebSocket message ────────────────────

  describe('updates vote counts on WebSocket message', () => {
    it('updates vote counts for a matchup when a WebSocket message is received', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      });

      // Initial vote counts: matchup 101 has votesA=5, votesB=3
      expect(screen.getByText('5')).toBeInTheDocument();
      expect(screen.getByText('3')).toBeInTheDocument();

      // Push a WebSocket message with updated counts
      act(() => {
        sendWsMessage({ matchupId: 101, votesA: 12, votesB: 8, winnerId: null });
      });

      await waitFor(() => {
        expect(screen.getByText('12')).toBeInTheDocument();
        expect(screen.getByText('8')).toBeInTheDocument();
      });
    });

    it('updates only the affected matchup, leaving others unchanged', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      });

      // Initial: matchup 101 votesA=5, votesB=3; matchup 102 votesA=2, votesB=7
      expect(screen.getByText('5')).toBeInTheDocument();
      expect(screen.getByText('2')).toBeInTheDocument();

      // Update only matchup 101
      act(() => {
        sendWsMessage({ matchupId: 101, votesA: 20, votesB: 15, winnerId: null });
      });

      await waitFor(() => {
        expect(screen.getByText('20')).toBeInTheDocument();
        expect(screen.getByText('15')).toBeInTheDocument();
      });

      // Matchup 102 counts should remain unchanged
      expect(screen.getByText('2')).toBeInTheDocument();
      expect(screen.getByText('7')).toBeInTheDocument();
    });

    it('handles malformed WebSocket messages without crashing', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      });

      // Send a malformed message — should not throw
      expect(() => {
        act(() => {
          if (capturedWsCallback) {
            capturedWsCallback({ body: 'not valid json {{' });
          }
        });
      }).not.toThrow();

      // Component should still be rendered correctly
      expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
    });

    it('subscribes to the correct tournament-specific WebSocket topic', async () => {
      const { Client } = await import('@stomp/stompjs');
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Meme Alpha')).toBeInTheDocument();
      });

      // The Client constructor should have been called
      expect(Client).toHaveBeenCalled();

      // The subscribe call should target the tournament-specific topic
      const clientInstance = Client.mock.instances[0];
      expect(clientInstance.subscribe).toHaveBeenCalledWith(
        '/topic/battle/tournament/1',
        expect.any(Function)
      );
    });
  });

  // ── Test 5: highlights champion when tournament completed ────────────────

  describe('highlights champion when tournament completed', () => {
    it('displays the champion banner when tournament status is COMPLETED', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByLabelText('Tournament Champion')).toBeInTheDocument();
      });
    });

    it('shows "Tournament Champion" heading in the champion banner', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Tournament Champion')).toBeInTheDocument();
      });
    });

    it('displays the champion meme title in the banner', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        // Champion is MEME_A ("Meme Alpha") — should appear in the banner
        const banner = screen.getByLabelText('Tournament Champion');
        expect(banner).toHaveTextContent('Meme Alpha');
      });
    });

    it('displays the champion meme image in the banner', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        const banner = screen.getByLabelText('Tournament Champion');
        const img = banner.querySelector('img');
        expect(img).toBeInTheDocument();
        expect(img).toHaveAttribute('alt', 'Meme Alpha');
      });
    });

    it('does NOT show the champion banner for an ACTIVE tournament', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Test Tournament')).toBeInTheDocument();
      });

      expect(screen.queryByLabelText('Tournament Champion')).not.toBeInTheDocument();
      expect(screen.queryByText('Tournament Champion')).not.toBeInTheDocument();
    });

    it('does NOT show the champion banner for a PENDING_APPROVAL tournament', async () => {
      api.get.mockResolvedValue({
        data: buildActiveTournament({
          status: 'PENDING_APPROVAL',
          currentRound: null,
          currentRoundEndsAt: null,
          champion: null,
        }),
      });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Test Tournament')).toBeInTheDocument();
      });

      expect(screen.queryByText('Tournament Champion')).not.toBeInTheDocument();
    });

    it('shows "Completed" status badge when tournament is COMPLETED', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        expect(screen.getByText('Completed')).toBeInTheDocument();
      });
    });

    it('shows "Active" status badge when tournament is ACTIVE', async () => {
      render(<TournamentBracket />);

      await waitFor(() => {
        // The status badge specifically (not the round "Active" indicator)
        expect(screen.getByText('Active', { selector: '.tb-status-badge' })).toBeInTheDocument();
      });
    });

    it('shows winner badges on all completed matchups in a finished tournament', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        // 3 matchups, each with a winner → 3 "Winner" badges
        const winnerBadges = screen.getAllByLabelText('Winner');
        expect(winnerBadges).toHaveLength(3);
      });
    });

    it('shows the champion meme vote count in the banner', async () => {
      api.get.mockResolvedValue({ data: buildCompletedTournament() });
      render(<TournamentBracket />);

      await waitFor(() => {
        const banner = screen.getByLabelText('Tournament Champion');
        // MEME_A has voteCount: 50
        expect(banner).toHaveTextContent('50');
      });
    });
  });
});
