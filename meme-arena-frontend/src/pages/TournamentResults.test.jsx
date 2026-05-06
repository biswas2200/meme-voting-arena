import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import TournamentResults from './TournamentResults';

// ── Mock react-router-dom ────────────────────────────────────────────────────
vi.mock('react-router-dom', () => ({
  useParams: () => ({ id: '42' }),
  Link: ({ children, to, ...rest }) =>
    React.createElement('a', { href: to, ...rest }, children),
}));

// ── Mock api.js ──────────────────────────────────────────────────────────────
vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
  },
}));

// ── Mock framer-motion ───────────────────────────────────────────────────────
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

// ── Fixture data ─────────────────────────────────────────────────────────────

import api from '../services/api';

const MEME_A = { id: 10, title: 'Meme Alpha', imageUrl: 'http://example.com/a.jpg', voteCount: 50 };
const MEME_B = { id: 20, title: 'Meme Beta',  imageUrl: 'http://example.com/b.jpg', voteCount: 30 };
const MEME_C = { id: 30, title: 'Meme Gamma', imageUrl: 'http://example.com/c.jpg', voteCount: 40 };
const MEME_D = { id: 40, title: 'Meme Delta', imageUrl: 'http://example.com/d.jpg', voteCount: 20 };

/**
 * Build a completed tournament with 2 rounds (4 memes).
 * Round 1 has 2 matchups (both complete), Round 2 (Final) has 1 matchup (complete).
 * Champion is MEME_A.
 */
function buildCompletedTournament(overrides = {}) {
  return {
    id: 42,
    name: 'Grand Meme Championship',
    creator: { username: 'tournament_creator' },
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
    ...overrides,
  };
}

/**
 * Build an active (non-completed) tournament.
 */
function buildActiveTournament() {
  return {
    id: 42,
    name: 'Ongoing Tournament',
    creator: { username: 'creator_user' },
    status: 'ACTIVE',
    roundDurationHours: 24,
    memeCount: 4,
    currentRound: 1,
    currentRoundEndsAt: new Date(Date.now() + 3_600_000).toISOString(),
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
    ],
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('TournamentResults', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: GET returns a completed tournament
    api.get.mockResolvedValue({ data: buildCompletedTournament() });
  });

  // ── Test 1: displays full bracket for completed tournament ───────────────

  describe('displays full bracket for completed tournament', () => {
    it('renders the tournament name in the header', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('Grand Meme Championship')).toBeInTheDocument();
      });
    });

    it('renders a section for each round', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByRole('region', { name: /round 1/i })).toBeInTheDocument();
        expect(screen.getByRole('region', { name: /final/i })).toBeInTheDocument();
      });
    });

    it('renders all meme titles across all rounds', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getAllByText('Meme Alpha').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Meme Beta').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Meme Gamma').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Meme Delta').length).toBeGreaterThanOrEqual(1);
      });
    });

    it('renders final vote counts for each matchup', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        // Round 1 matchup 1: votesA=10, votesB=4
        expect(screen.getByText('10')).toBeInTheDocument();
        expect(screen.getByText('4')).toBeInTheDocument();
        // Round 1 matchup 2: votesA=3, votesB=8
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByText('8')).toBeInTheDocument();
        // Final: votesA=15, votesB=6
        expect(screen.getByText('15')).toBeInTheDocument();
        expect(screen.getByText('6')).toBeInTheDocument();
      });
    });

    it('shows "VS" separator between memes in each matchup', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        // 3 matchups → 3 VS separators
        const vsElements = screen.getAllByText('VS');
        expect(vsElements).toHaveLength(3);
      });
    });

    it('shows winner badges on all completed matchups', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        // 3 matchups, each with a winner → 3 "Winner" badges
        const winnerBadges = screen.getAllByLabelText('Winner');
        expect(winnerBadges).toHaveLength(3);
      });
    });

    it('shows loading state before data arrives', () => {
      api.get.mockReturnValue(new Promise(() => {}));
      render(<TournamentResults />);

      expect(screen.getByText(/loading results/i)).toBeInTheDocument();
    });

    it('shows error state when API call fails', async () => {
      api.get.mockRejectedValue({
        response: { status: 500, data: { message: 'Server error' } },
      });
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      });
    });

    it('shows not-found error when tournament does not exist', async () => {
      api.get.mockRejectedValue({ response: { status: 404, data: {} } });
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /tournament not found/i })).toBeInTheDocument();
      });
    });

    it('does NOT show voting controls (vote buttons) on the results page', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('Grand Meme Championship')).toBeInTheDocument();
      });

      // No vote buttons should be present — this is a read-only results page
      expect(screen.queryByRole('button', { name: /vote for/i })).not.toBeInTheDocument();
    });
  });

  // ── Test 2: highlights champion ──────────────────────────────────────────

  describe('highlights champion', () => {
    it('displays the champion banner for a completed tournament', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByLabelText('Tournament Champion')).toBeInTheDocument();
      });
    });

    it('shows "Tournament Champion" heading in the banner', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('Tournament Champion')).toBeInTheDocument();
      });
    });

    it('displays the champion meme title in the banner', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        const banner = screen.getByLabelText('Tournament Champion');
        expect(banner).toHaveTextContent('Meme Alpha');
      });
    });

    it('displays the champion meme image in the banner', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        const banner = screen.getByLabelText('Tournament Champion');
        const img = banner.querySelector('img');
        expect(img).toBeInTheDocument();
        expect(img).toHaveAttribute('alt', 'Meme Alpha');
      });
    });

    it('shows the champion meme vote count in the banner', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        const banner = screen.getByLabelText('Tournament Champion');
        // MEME_A has voteCount: 50
        expect(banner).toHaveTextContent('50');
      });
    });

    it('shows the "Completed" badge in the header', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        // Use the CSS class to target the badge specifically (not the metadata label)
        const badge = document.querySelector('.tr-completed-badge');
        expect(badge).toBeInTheDocument();
        expect(badge).toHaveTextContent('Completed');
      });
    });
  });

  // ── Test 3: shows tournament metadata ────────────────────────────────────

  describe('shows tournament metadata', () => {
    it('displays the creator username', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('tournament_creator')).toBeInTheDocument();
      });
    });

    it('displays the creator username when creator is a plain string', async () => {
      api.get.mockResolvedValue({
        data: buildCompletedTournament({ creator: 'string_creator' }),
      });
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('string_creator')).toBeInTheDocument();
      });
    });

    it('displays the round duration', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        // roundDurationHours = 6 → displayed as "6h"
        expect(screen.getByText('6h')).toBeInTheDocument();
      });
    });

    it('displays the completion date', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        // completedAt = '2026-01-02T12:00:00Z' → formatted as a locale date string
        // We check for the year to avoid locale-specific formatting issues
        const metadataSection = screen.getByText(/2026/);
        expect(metadataSection).toBeInTheDocument();
      });
    });

    it('shows metadata labels for creator, round duration, and completion date', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('Creator')).toBeInTheDocument();
        expect(screen.getByText('Round Duration')).toBeInTheDocument();
        // "Completed" appears as both the badge and the metadata label — use getAllByText
        const completedElements = screen.getAllByText('Completed');
        expect(completedElements.length).toBeGreaterThanOrEqual(2);
      });
    });
  });

  // ── Test 4: accessible without authentication ────────────────────────────

  describe('accessible without authentication', () => {
    it('renders the full results page without any auth context', async () => {
      // TournamentResults does NOT use useAuth — it should render without it.
      // The api.js interceptor attaches the token if present, but the component
      // itself does not require the user to be logged in.
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('Grand Meme Championship')).toBeInTheDocument();
        expect(screen.getByLabelText('Tournament Champion')).toBeInTheDocument();
      });
    });

    it('calls the public GET /api/battle/tournaments/{id} endpoint without extra auth logic', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText('Grand Meme Championship')).toBeInTheDocument();
      });

      // Should have called the correct endpoint
      expect(api.get).toHaveBeenCalledWith('/api/battle/tournaments/42');
    });

    it('does NOT import or use useAuth', () => {
      // Verify the component source does not depend on AuthContext
      // by checking that it renders correctly without any auth mock
      // (the mock is not set up for AuthContext in this test file)
      render(<TournamentResults />);
      // If it tried to call useAuth() without a provider, it would throw.
      // The fact that it renders without error confirms no auth dependency.
      expect(screen.getByText(/loading results/i)).toBeInTheDocument();
    });

    it('shows "not yet completed" error for a non-COMPLETED tournament', async () => {
      api.get.mockResolvedValue({ data: buildActiveTournament() });
      render(<TournamentResults />);

      await waitFor(() => {
        expect(screen.getByText(/this tournament is not yet completed/i)).toBeInTheDocument();
      });
    });

    it('shows "Results Not Available" heading for a non-COMPLETED tournament', async () => {
      api.get.mockResolvedValue({ data: buildActiveTournament() });
      render(<TournamentResults />);

      await waitFor(() => {
        expect(
          screen.getByRole('heading', { name: /results not available/i })
        ).toBeInTheDocument();
      });
    });

    it('shows a "View Live Bracket" link for a non-COMPLETED tournament', async () => {
      api.get.mockResolvedValue({ data: buildActiveTournament() });
      render(<TournamentResults />);

      await waitFor(() => {
        const link = screen.getByRole('link', { name: /view live bracket/i });
        expect(link).toBeInTheDocument();
        expect(link).toHaveAttribute('href', '/battle/tournaments/42');
      });
    });

    it('shows a "Back to Tournaments" link on the results page', async () => {
      render(<TournamentResults />);

      await waitFor(() => {
        const link = screen.getByRole('link', { name: /all tournaments/i });
        expect(link).toBeInTheDocument();
        expect(link).toHaveAttribute('href', '/battle/tournaments');
      });
    });
  });
});
