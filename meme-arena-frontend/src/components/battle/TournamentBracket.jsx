import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Trophy,
  Crown,
  ImageOff,
  AlertTriangle,
  RefreshCw,
  ThumbsUp,
  CheckCircle,
  ChevronRight,
  Clock,
} from 'lucide-react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import api from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import './TournamentBracket.css';

// ── Animation variants ────────────────────────────────────────────────────────

const containerVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.1 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
};

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Group matchups by roundNumber, sorted by bracketPosition within each round.
 * Returns a Map<roundNumber, MatchupResponse[]> ordered by round ascending.
 */
function groupMatchupsByRound(matchups) {
  const map = new Map();
  for (const matchup of matchups) {
    const round = matchup.roundNumber;
    if (!map.has(round)) map.set(round, []);
    map.get(round).push(matchup);
  }
  // Sort matchups within each round by bracketPosition
  for (const [, list] of map) {
    list.sort((a, b) => a.bracketPosition - b.bracketPosition);
  }
  // Return sorted by round number
  return new Map([...map.entries()].sort((a, b) => a[0] - b[0]));
}

/**
 * Compute total rounds from the matchup data.
 * For 8 memes: 3 rounds; for 16 memes: 4 rounds.
 */
function getTotalRounds(roundsMap) {
  if (roundsMap.size === 0) return 0;
  return Math.max(...roundsMap.keys());
}

/**
 * Resolve image URL — handles relative paths from the backend.
 */
function resolveImageUrl(imageUrl) {
  if (!imageUrl) return null;
  if (imageUrl.startsWith('http')) return imageUrl;
  const base = import.meta.env.VITE_API_URL || 'http://localhost:8080';
  return `${base}${imageUrl}`;
}

// ── Countdown helpers ─────────────────────────────────────────────────────────

/**
 * Given an ISO-8601 timestamp string (or null), compute the time remaining
 * as a human-readable string.
 *
 * Returns:
 *   - "Round ending soon..." when the timestamp is in the past or null
 *   - "Round ends in: 5h 23m 15s" (zero-value units omitted, e.g. "23m 15s")
 */
function formatCountdown(endsAtIso) {
  if (!endsAtIso) return null;

  const diffMs = new Date(endsAtIso).getTime() - Date.now();
  if (diffMs <= 0) return 'Round ending soon...';

  const totalSeconds = Math.floor(diffMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const parts = [];
  if (hours > 0) parts.push(`${hours}h`);
  if (hours > 0 || minutes > 0) parts.push(`${minutes}m`);
  parts.push(`${seconds}s`);

  return `Round ends in: ${parts.join(' ')}`;
}

/**
 * Hook that returns a live countdown string for the given ISO timestamp.
 * Updates every second. Returns null when endsAt is not provided.
 */
function useCountdown(endsAtIso) {
  const [display, setDisplay] = useState(() => formatCountdown(endsAtIso));
  const intervalRef = useRef(null);

  useEffect(() => {
    // Immediately compute on mount / when endsAt changes
    setDisplay(formatCountdown(endsAtIso));

    if (!endsAtIso) return;

    intervalRef.current = setInterval(() => {
      setDisplay(formatCountdown(endsAtIso));
    }, 1000);

    return () => {
      clearInterval(intervalRef.current);
    };
  }, [endsAtIso]);

  return display;
}

// ── Sub-component: CountdownTimer ─────────────────────────────────────────────

/**
 * Displays the live countdown for the active round.
 * Only rendered when the tournament status is ACTIVE.
 */
function CountdownTimer({ currentRoundEndsAt }) {
  const countdown = useCountdown(currentRoundEndsAt);

  if (!countdown) return null;

  const isSoon = countdown === 'Round ending soon...';

  return (
    <motion.div
      className={`tb-countdown${isSoon ? ' tb-countdown--ending' : ''}`}
      initial={{ opacity: 0, y: -6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      aria-live="polite"
      aria-label={countdown}
    >
      <Clock size={14} aria-hidden="true" />
      <span className="tb-countdown-text">{countdown}</span>
    </motion.div>
  );
}

// ── Sub-component: MemeSlot ───────────────────────────────────────────────────

/**
 * Renders one meme within a matchup card.
 * Shows image, title, vote count, winner badge, and optional vote button.
 */
function MemeSlot({ meme, votes, isWinner, matchupComplete, showVoteButton, onVote, voteDisabled, isVotedFor }) {
  const [imgError, setImgError] = useState(false);

  if (!meme) {
    return (
      <div className="tb-meme-slot tb-meme-slot--tbd">
        <div className="tb-meme-slot-image tb-meme-slot-image--placeholder">
          <span className="tb-tbd-label">TBD</span>
        </div>
        <div className="tb-meme-slot-info">
          <span className="tb-meme-slot-title tb-meme-slot-title--tbd">To Be Determined</span>
        </div>
      </div>
    );
  }

  const imgSrc = resolveImageUrl(meme.imageUrl);

  return (
    <div className={`tb-meme-slot${isWinner ? ' tb-meme-slot--winner' : ''}${matchupComplete && !isWinner ? ' tb-meme-slot--loser' : ''}${isVotedFor ? ' tb-meme-slot--voted' : ''}`}>
      {/* Image */}
      <div className="tb-meme-slot-image">
        {imgSrc && !imgError ? (
          <img
            src={imgSrc}
            alt={meme.title}
            className="tb-meme-img"
            onError={() => setImgError(true)}
            loading="lazy"
          />
        ) : (
          <div className="tb-meme-img-placeholder">
            <ImageOff size={24} />
          </div>
        )}
      </div>

      {/* Info */}
      <div className="tb-meme-slot-info">
        <span className="tb-meme-slot-title" title={meme.title}>
          {meme.title}
        </span>

        <div className="tb-meme-slot-votes">
          <ThumbsUp size={12} />
          <span className="tb-vote-number">{votes ?? 0}</span>
          <span className="tb-vote-label">votes</span>
        </div>
      </div>

      {/* Winner badge */}
      {isWinner && (
        <div className="tb-winner-badge" aria-label="Winner">
          <CheckCircle size={12} />
          Winner
        </div>
      )}

      {/* Vote button — only shown for active matchups when user is authenticated and hasn't voted */}
      {showVoteButton && (
        <motion.button
          className={`tb-vote-btn${isVotedFor ? ' tb-vote-btn--voted' : ''}`}
          onClick={() => onVote(meme.id)}
          disabled={voteDisabled}
          whileHover={voteDisabled ? {} : { scale: 1.04 }}
          whileTap={voteDisabled ? {} : { scale: 0.96 }}
          aria-label={`Vote for ${meme.title}`}
        >
          <ThumbsUp size={13} />
          Vote
        </motion.button>
      )}
    </div>
  );
}

// ── Sub-component: MatchupCard ────────────────────────────────────────────────

function MatchupCard({ matchup, isActiveRound, isAuthenticated, votedMemeId, onVote, voting }) {
  const isComplete = matchup.winner !== null;
  const winnerId = matchup.winner?.id ?? null;

  // Show vote buttons only when: active round, not complete, user is authenticated, and hasn't voted yet
  const canVote = isActiveRound && !isComplete && isAuthenticated && votedMemeId === null;
  const hasVoted = votedMemeId !== null;

  return (
    <motion.div
      className={`tb-matchup-card${isActiveRound ? ' tb-matchup-card--active' : ''}${isComplete ? ' tb-matchup-card--complete' : ''}`}
      variants={itemVariants}
      layout
    >
      {/* Active round indicator */}
      {isActiveRound && !isComplete && (
        <div className="tb-matchup-active-indicator" aria-label="Active matchup" />
      )}

      <MemeSlot
        meme={matchup.memeA}
        votes={matchup.votesA}
        isWinner={isComplete && winnerId === matchup.memeA?.id}
        matchupComplete={isComplete}
        showVoteButton={canVote}
        onVote={onVote}
        voteDisabled={voting}
        isVotedFor={hasVoted && votedMemeId === matchup.memeA?.id}
      />

      <div className="tb-matchup-vs">
        <span className="tb-vs-text">VS</span>
      </div>

      <MemeSlot
        meme={matchup.memeB}
        votes={matchup.votesB}
        isWinner={isComplete && winnerId === matchup.memeB?.id}
        matchupComplete={isComplete}
        showVoteButton={canVote}
        onVote={onVote}
        voteDisabled={voting}
        isVotedFor={hasVoted && votedMemeId === matchup.memeB?.id}
      />

      {/* "You voted for [meme title]" indicator — shown after voting */}
      <AnimatePresence>
        {hasVoted && isActiveRound && !isComplete && (
          <motion.div
            className="tb-voted-indicator"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.3 }}
          >
            <CheckCircle size={13} />
            You voted for{' '}
            <strong>
              {votedMemeId === matchup.memeA?.id
                ? matchup.memeA?.title
                : matchup.memeB?.title}
            </strong>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

// ── Sub-component: RoundSection ───────────────────────────────────────────────

function RoundSection({ roundNumber, matchups, isCurrentRound, totalRounds, isAuthenticated, votedMatchups, onVote, votingMatchupId }) {
  const isFinalRound = roundNumber === totalRounds;
  const roundLabel = isFinalRound ? 'Final' : `Round ${roundNumber}`;

  return (
    <motion.section
      className={`tb-round-section${isCurrentRound ? ' tb-round-section--active' : ''}`}
      variants={itemVariants}
      aria-label={roundLabel}
    >
      {/* Round header */}
      <div className="tb-round-header">
        <h2 className="tb-round-title">
          {isFinalRound && <Trophy size={18} />}
          {roundLabel}
        </h2>
        {isCurrentRound && (
          <span className="tb-round-active-badge">
            <span className="tb-round-active-dot" aria-hidden="true" />
            Active
          </span>
        )}
      </div>

      {/* Matchups */}
      <div className="tb-matchups-list">
        {matchups.map((matchup) => (
          <MatchupCard
            key={matchup.id}
            matchup={matchup}
            isActiveRound={isCurrentRound}
            isAuthenticated={isAuthenticated}
            votedMemeId={votedMatchups[matchup.id] ?? null}
            onVote={(chosenMemeId) => onVote(matchup.id, chosenMemeId)}
            voting={votingMatchupId === matchup.id}
          />
        ))}
      </div>
    </motion.section>
  );
}

// ── Sub-component: ChampionBanner ─────────────────────────────────────────────

function ChampionBanner({ champion }) {
  const [imgError, setImgError] = useState(false);
  const imgSrc = resolveImageUrl(champion?.imageUrl);

  return (
    <motion.div
      className="tb-champion-banner"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.6, ease: 'easeOut' }}
      aria-label="Tournament Champion"
    >
      <div className="tb-champion-crown">
        <Crown size={40} />
      </div>

      <h2 className="tb-champion-label">Tournament Champion</h2>

      <div className="tb-champion-card">
        <div className="tb-champion-image-wrap">
          {imgSrc && !imgError ? (
            <img
              src={imgSrc}
              alt={champion.title}
              className="tb-champion-image"
              onError={() => setImgError(true)}
            />
          ) : (
            <div className="tb-champion-image-placeholder">
              <ImageOff size={48} />
            </div>
          )}
        </div>
        <div className="tb-champion-info">
          <h3 className="tb-champion-title">{champion.title}</h3>
          <div className="tb-champion-votes">
            <ThumbsUp size={16} />
            <span>{champion.voteCount ?? 0} total votes</span>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// ── Sub-component: StatusBadge ────────────────────────────────────────────────

function StatusBadge({ status }) {
  const config = {
    ACTIVE: { label: 'Active', cls: 'tb-badge--active' },
    COMPLETED: { label: 'Completed', cls: 'tb-badge--completed' },
    PENDING_APPROVAL: { label: 'Pending Approval', cls: 'tb-badge--pending' },
    REJECTED: { label: 'Rejected', cls: 'tb-badge--rejected' },
  };
  const { label, cls } = config[status] ?? { label: status, cls: '' };
  return <span className={`tb-status-badge ${cls}`}>{label}</span>;
}

// ── Main component ────────────────────────────────────────────────────────────

export default function TournamentBracket({ tournamentId: tournamentIdProp }) {
  // Support both prop-based and route-based usage
  const params = useParams();
  const tournamentId = tournamentIdProp ?? params.id;

  const { user } = useAuth();

  const [tournament, setTournament] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // ── Voting state ──────────────────────────────────────────────────────────
  // Map of matchupId → chosenMemeId for matchups the user has voted on this session
  const [votedMatchups, setVotedMatchups] = useState({});
  // The matchupId currently being submitted (to show per-card loading state)
  const [votingMatchupId, setVotingMatchupId] = useState(null);

  // ── Fetch tournament data ─────────────────────────────────────────────────

  const fetchTournament = useCallback(async () => {
    if (!tournamentId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get(`/api/battle/tournaments/${tournamentId}`);
      setTournament(res.data);
    } catch (err) {
      const status = err.response?.status;
      const message = err.response?.data?.message || '';
      if (status === 404) {
        setError({ type: 'not_found', message: 'Tournament not found.' });
      } else {
        setError({
          type: 'generic',
          message: message || 'Failed to load tournament. Please try again.',
        });
      }
    } finally {
      setLoading(false);
    }
  }, [tournamentId]);

  useEffect(() => {
    fetchTournament();
  }, [fetchTournament]);

  // ── Polling: refresh bracket every 30 seconds (Requirements 9.3) ─────────

  // Keep a ref to the latest currentRound so the polling callback can compare
  // without needing to be re-created every time tournament state changes.
  const currentRoundRef = useRef(null);
  useEffect(() => {
    currentRoundRef.current = tournament?.currentRound ?? null;
  }, [tournament?.currentRound]);

  useEffect(() => {
    if (!tournamentId) return;

    const intervalId = setInterval(async () => {
      try {
        const res = await api.get(`/api/battle/tournaments/${tournamentId}`);
        const fresh = res.data;

        if (fresh.currentRound !== currentRoundRef.current) {
          // Round advanced — do a full bracket refresh
          setTournament(fresh);
        } else {
          // Same round — only update vote counts to avoid unnecessary re-renders
          setTournament((prev) => {
            if (!prev) return fresh;
            const updatedMatchups = prev.matchups.map((m) => {
              const freshMatchup = fresh.matchups.find((fm) => fm.id === m.id);
              if (!freshMatchup) return m;
              return { ...m, votesA: freshMatchup.votesA, votesB: freshMatchup.votesB };
            });
            return { ...prev, matchups: updatedMatchups };
          });
        }
      } catch {
        // Silently ignore polling errors — the user can manually refresh
      }
    }, 30_000);

    return () => {
      clearInterval(intervalId);
    };
  }, [tournamentId]);

  // ── WebSocket: live vote count updates (Requirements 9.7) ─────────────────

  useEffect(() => {
    if (!tournamentId) return;

    const wsUrl = `${window.location.protocol}//${window.location.host}/ws`;

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe(
          `/topic/battle/tournament/${tournamentId}`,
          (message) => {
            try {
              // Payload: { matchupId, votesA, votesB, winnerId }
              const payload = JSON.parse(message.body);
              const { matchupId, votesA, votesB } = payload;

              setTournament((prev) => {
                if (!prev) return prev;

                const prevRound = prev.currentRound;
                const updatedMatchups = prev.matchups.map((m) => {
                  if (m.id !== matchupId) return m;
                  return { ...m, votesA, votesB };
                });

                const updated = { ...prev, matchups: updatedMatchups };

                // If winnerId is set, a matchup just completed — check whether
                // the round has advanced by comparing currentRound. If the
                // backend has already bumped currentRound in the WS payload
                // we trigger a full refresh; otherwise we just update counts.
                if (payload.winnerId !== null && payload.winnerId !== undefined) {
                  // Schedule a full refresh to pick up the new round state
                  // (done asynchronously so we don't block the state update)
                  setTimeout(() => {
                    api
                      .get(`/api/battle/tournaments/${tournamentId}`)
                      .then((res) => {
                        if (res.data.currentRound !== prevRound) {
                          setTournament(res.data);
                        }
                      })
                      .catch(() => {});
                  }, 500);
                }

                return updated;
              });
            } catch {
              // Ignore malformed messages
            }
          }
        );
      },
    });

    stompClient.activate();

    return () => {
      stompClient.deactivate();
    };
  }, [tournamentId]);

  // ── Vote handler ──────────────────────────────────────────────────────────

  const handleVote = useCallback(async (matchupId, chosenMemeId) => {
    if (!user || votingMatchupId !== null) return;

    setVotingMatchupId(matchupId);
    try {
      const res = await api.post('/api/battle/vote/tournament', { matchupId, chosenMemeId });
      // BattleVoteResult: { matchupId, memeAVotes, memeBVotes, chosenMemeId }
      const result = res.data;

      // Mark this matchup as voted
      setVotedMatchups((prev) => ({ ...prev, [matchupId]: chosenMemeId }));

      // Update vote counts in the local tournament state
      setTournament((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          matchups: prev.matchups.map((m) => {
            if (m.id !== matchupId) return m;
            return { ...m, votesA: result.memeAVotes, votesB: result.memeBVotes };
          }),
        };
      });
    } catch (err) {
      const status = err.response?.status;
      if (status === 409) {
        // Already voted — mark as voted so UI reflects it
        setVotedMatchups((prev) => ({ ...prev, [matchupId]: chosenMemeId }));
      }
      // Other errors (400, 401, etc.) are silently ignored — the UI stays interactive
    } finally {
      setVotingMatchupId(null);
    }
  }, [user, votingMatchupId]);

  // ── Derived data ──────────────────────────────────────────────────────────

  const roundsMap = tournament
    ? groupMatchupsByRound(tournament.matchups ?? [])
    : new Map();

  const totalRounds = getTotalRounds(roundsMap);
  const currentRound = tournament?.currentRound ?? null;
  const isCompleted = tournament?.status === 'COMPLETED';

  const creatorName =
    typeof tournament?.creator === 'string'
      ? tournament.creator
      : tournament?.creator?.username ?? 'Unknown';

  // ── Render: loading ───────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="tb-container">
        <div className="tb-loading">
          <motion.div
            className="tb-loading-spinner"
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
          />
          <p>Loading bracket…</p>
        </div>
      </div>
    );
  }

  // ── Render: error ─────────────────────────────────────────────────────────

  if (error) {
    return (
      <div className="tb-container">
        <motion.div
          className="tb-error"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <AlertTriangle size={56} className="tb-error-icon" />
          <h2>{error.type === 'not_found' ? 'Tournament Not Found' : 'Something Went Wrong'}</h2>
          <p>{error.message}</p>
          <div className="tb-error-actions">
            {error.type !== 'not_found' && (
              <motion.button
                className="btn btn-primary"
                onClick={fetchTournament}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                <RefreshCw size={16} />
                Try Again
              </motion.button>
            )}
            <Link to="/battle/tournaments" className="btn btn-secondary">
              Back to Tournaments
            </Link>
          </div>
        </motion.div>
      </div>
    );
  }

  if (!tournament) return null;

  // ── Render: bracket ───────────────────────────────────────────────────────

  return (
    <div className="tb-container">
      {/* ── Page header ── */}
      <motion.div
        className="tb-header"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        <motion.div className="tb-header-main" variants={itemVariants}>
          <div className="tb-header-title-row">
            <h1 className="tb-title">
              <Trophy size={32} />
              {tournament.name}
            </h1>
            <StatusBadge status={tournament.status} />
          </div>

          <div className="tb-header-meta">
            <span className="tb-meta-item">
              by <span className="tb-meta-value">{creatorName}</span>
            </span>
            <span className="tb-meta-separator" aria-hidden="true">·</span>
            <span className="tb-meta-item">
              <span className="tb-meta-value">{tournament.roundDurationHours}h</span> rounds
            </span>
            {currentRound && !isCompleted && (
              <>
                <span className="tb-meta-separator" aria-hidden="true">·</span>
                <span className="tb-meta-item">
                  Round <span className="tb-meta-value">{currentRound}</span> of{' '}
                  <span className="tb-meta-value">{totalRounds}</span>
                </span>
              </>
            )}
          </div>

          {/* Countdown timer — only shown for ACTIVE tournaments */}
          {tournament.status === 'ACTIVE' && (
            <CountdownTimer currentRoundEndsAt={tournament.currentRoundEndsAt} />
          )}
        </motion.div>

        {/* Back link */}
        <motion.div variants={itemVariants}>
          <Link to="/battle/tournaments" className="btn btn-secondary btn-sm">
            <ChevronRight size={14} style={{ transform: 'rotate(180deg)' }} />
            All Tournaments
          </Link>
        </motion.div>
      </motion.div>

      {/* ── Champion banner (COMPLETED tournaments) ── */}
      <AnimatePresence>
        {isCompleted && tournament.champion && (
          <ChampionBanner champion={tournament.champion} />
        )}
      </AnimatePresence>

      {/* ── Bracket rounds ── */}
      {roundsMap.size === 0 ? (
        <motion.div
          className="tb-empty"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        >
          <Trophy size={56} className="tb-empty-icon" />
          <p>No matchups available yet. The bracket will appear once the tournament is approved.</p>
        </motion.div>
      ) : (
        <motion.div
          className="tb-bracket"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {[...roundsMap.entries()].map(([roundNumber, matchups]) => (
            <RoundSection
              key={roundNumber}
              roundNumber={roundNumber}
              matchups={matchups}
              isCurrentRound={roundNumber === currentRound}
              totalRounds={totalRounds}
              isAuthenticated={!!user}
              votedMatchups={votedMatchups}
              onVote={handleVote}
              votingMatchupId={votingMatchupId}
            />
          ))}
        </motion.div>
      )}
    </div>
  );
}
