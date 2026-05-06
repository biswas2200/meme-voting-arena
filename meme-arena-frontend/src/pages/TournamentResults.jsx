import React, { useState, useEffect, useCallback } from 'react';
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
  Calendar,
  Clock,
  User,
} from 'lucide-react';
import api from '../services/api';
import '../styles/TournamentResults.css';

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
  for (const [, list] of map) {
    list.sort((a, b) => a.bracketPosition - b.bracketPosition);
  }
  return new Map([...map.entries()].sort((a, b) => a[0] - b[0]));
}

function getTotalRounds(roundsMap) {
  if (roundsMap.size === 0) return 0;
  return Math.max(...roundsMap.keys());
}

function resolveImageUrl(imageUrl) {
  if (!imageUrl) return null;
  if (imageUrl.startsWith('http')) return imageUrl;
  const base = import.meta.env.VITE_API_URL || 'http://localhost:8080';
  return `${base}${imageUrl}`;
}

function formatDate(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

// ── Sub-component: ChampionBanner ─────────────────────────────────────────────

function ChampionBanner({ champion }) {
  const [imgError, setImgError] = useState(false);
  const imgSrc = resolveImageUrl(champion?.imageUrl);

  return (
    <motion.div
      className="tr-champion-banner"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.6, ease: 'easeOut' }}
      aria-label="Tournament Champion"
    >
      <div className="tr-champion-crown">
        <Crown size={40} />
      </div>

      <h2 className="tr-champion-label">Tournament Champion</h2>

      <div className="tr-champion-card">
        <div className="tr-champion-image-wrap">
          {imgSrc && !imgError ? (
            <img
              src={imgSrc}
              alt={champion.title}
              className="tr-champion-image"
              onError={() => setImgError(true)}
            />
          ) : (
            <div className="tr-champion-image-placeholder">
              <ImageOff size={48} />
            </div>
          )}
        </div>
        <div className="tr-champion-info">
          <h3 className="tr-champion-title">{champion.title}</h3>
          <div className="tr-champion-votes">
            <ThumbsUp size={16} />
            <span>{champion.voteCount ?? 0} total votes</span>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// ── Sub-component: MemeSlot (read-only, no voting controls) ───────────────────

function MemeSlot({ meme, votes, isWinner, matchupComplete }) {
  const [imgError, setImgError] = useState(false);

  if (!meme) {
    return (
      <div className="tr-meme-slot tr-meme-slot--tbd">
        <div className="tr-meme-slot-image tr-meme-slot-image--placeholder">
          <span className="tr-tbd-label">TBD</span>
        </div>
        <div className="tr-meme-slot-info">
          <span className="tr-meme-slot-title tr-meme-slot-title--tbd">To Be Determined</span>
        </div>
      </div>
    );
  }

  const imgSrc = resolveImageUrl(meme.imageUrl);

  return (
    <div
      className={`tr-meme-slot${isWinner ? ' tr-meme-slot--winner' : ''}${matchupComplete && !isWinner ? ' tr-meme-slot--loser' : ''}`}
    >
      {/* Image */}
      <div className="tr-meme-slot-image">
        {imgSrc && !imgError ? (
          <img
            src={imgSrc}
            alt={meme.title}
            className="tr-meme-img"
            onError={() => setImgError(true)}
            loading="lazy"
          />
        ) : (
          <div className="tr-meme-img-placeholder">
            <ImageOff size={24} />
          </div>
        )}
      </div>

      {/* Info */}
      <div className="tr-meme-slot-info">
        <span className="tr-meme-slot-title" title={meme.title}>
          {meme.title}
        </span>

        <div className="tr-meme-slot-votes">
          <ThumbsUp size={12} />
          <span className="tr-vote-number">{votes ?? 0}</span>
          <span className="tr-vote-label">votes</span>
        </div>
      </div>

      {/* Winner badge */}
      {isWinner && (
        <div className="tr-winner-badge" aria-label="Winner">
          <CheckCircle size={12} />
          Winner
        </div>
      )}
    </div>
  );
}

// ── Sub-component: MatchupCard (read-only) ────────────────────────────────────

function MatchupCard({ matchup }) {
  const isComplete = matchup.winner !== null;
  const winnerId = matchup.winner?.id ?? null;

  return (
    <motion.div
      className={`tr-matchup-card${isComplete ? ' tr-matchup-card--complete' : ''}`}
      variants={itemVariants}
      layout
    >
      <MemeSlot
        meme={matchup.memeA}
        votes={matchup.votesA}
        isWinner={isComplete && winnerId === matchup.memeA?.id}
        matchupComplete={isComplete}
      />

      <div className="tr-matchup-vs">
        <span className="tr-vs-text">VS</span>
      </div>

      <MemeSlot
        meme={matchup.memeB}
        votes={matchup.votesB}
        isWinner={isComplete && winnerId === matchup.memeB?.id}
        matchupComplete={isComplete}
      />
    </motion.div>
  );
}

// ── Sub-component: RoundSection ───────────────────────────────────────────────

function RoundSection({ roundNumber, matchups, totalRounds }) {
  const isFinalRound = roundNumber === totalRounds;
  const roundLabel = isFinalRound ? 'Final' : `Round ${roundNumber}`;

  return (
    <motion.section
      className="tr-round-section"
      variants={itemVariants}
      aria-label={roundLabel}
    >
      <div className="tr-round-header">
        <h2 className="tr-round-title">
          {isFinalRound && <Trophy size={18} />}
          {roundLabel}
        </h2>
      </div>

      <div className="tr-matchups-list">
        {matchups.map((matchup) => (
          <MatchupCard key={matchup.id} matchup={matchup} />
        ))}
      </div>
    </motion.section>
  );
}

// ── Sub-component: MetadataRow ────────────────────────────────────────────────

function MetadataRow({ tournament, creatorName }) {
  const completedDate = formatDate(tournament.completedAt);

  return (
    <motion.div className="tr-metadata" variants={itemVariants}>
      <div className="tr-metadata-item">
        <User size={14} aria-hidden="true" />
        <span className="tr-metadata-label">Creator</span>
        <span className="tr-metadata-value">{creatorName}</span>
      </div>
      <span className="tr-metadata-separator" aria-hidden="true">·</span>
      <div className="tr-metadata-item">
        <Clock size={14} aria-hidden="true" />
        <span className="tr-metadata-label">Round Duration</span>
        <span className="tr-metadata-value">{tournament.roundDurationHours}h</span>
      </div>
      <span className="tr-metadata-separator" aria-hidden="true">·</span>
      <div className="tr-metadata-item">
        <Calendar size={14} aria-hidden="true" />
        <span className="tr-metadata-label">Completed</span>
        <span className="tr-metadata-value">{completedDate}</span>
      </div>
    </motion.div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export default function TournamentResults() {
  const { id } = useParams();

  const [tournament, setTournament] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // ── Fetch tournament data ─────────────────────────────────────────────────

  const fetchTournament = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      // Public endpoint — no auth header needed
      const res = await api.get(`/api/battle/tournaments/${id}`);
      const data = res.data;

      if (data.status !== 'COMPLETED') {
        setError({
          type: 'not_completed',
          message: 'This tournament is not yet completed.',
        });
      } else {
        setTournament(data);
      }
    } catch (err) {
      const status = err.response?.status;
      const message = err.response?.data?.message || '';
      if (status === 404) {
        setError({ type: 'not_found', message: 'Tournament not found.' });
      } else {
        setError({
          type: 'generic',
          message: message || 'Failed to load tournament results. Please try again.',
        });
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchTournament();
  }, [fetchTournament]);

  // ── Derived data ──────────────────────────────────────────────────────────

  const roundsMap = tournament
    ? groupMatchupsByRound(tournament.matchups ?? [])
    : new Map();

  const totalRounds = getTotalRounds(roundsMap);

  const creatorName =
    typeof tournament?.creator === 'string'
      ? tournament.creator
      : tournament?.creator?.username ?? 'Unknown';

  // ── Render: loading ───────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="tr-container">
        <div className="tr-loading">
          <motion.div
            className="tr-loading-spinner"
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
          />
          <p>Loading results…</p>
        </div>
      </div>
    );
  }

  // ── Render: error ─────────────────────────────────────────────────────────

  if (error) {
    return (
      <div className="tr-container">
        <motion.div
          className="tr-error"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <AlertTriangle size={56} className="tr-error-icon" />
          <h2>
            {error.type === 'not_found'
              ? 'Tournament Not Found'
              : error.type === 'not_completed'
              ? 'Results Not Available'
              : 'Something Went Wrong'}
          </h2>
          <p>{error.message}</p>
          <div className="tr-error-actions">
            {error.type === 'not_completed' && (
              <Link
                to={`/battle/tournaments/${id}`}
                className="btn btn-primary"
              >
                View Live Bracket
              </Link>
            )}
            {error.type === 'generic' && (
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

  // ── Render: results ───────────────────────────────────────────────────────

  return (
    <div className="tr-container">
      {/* ── Page header ── */}
      <motion.div
        className="tr-header"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        <motion.div className="tr-header-main" variants={itemVariants}>
          <div className="tr-header-title-row">
            <h1 className="tr-title">
              <Trophy size={32} />
              {tournament.name}
            </h1>
            <span className="tr-completed-badge">Completed</span>
          </div>

          {/* Tournament metadata: creator, round duration, completion date */}
          <MetadataRow tournament={tournament} creatorName={creatorName} />
        </motion.div>

        {/* Back link */}
        <motion.div variants={itemVariants}>
          <Link to="/battle/tournaments" className="btn btn-secondary btn-sm">
            <ChevronRight size={14} style={{ transform: 'rotate(180deg)' }} />
            All Tournaments
          </Link>
        </motion.div>
      </motion.div>

      {/* ── Champion banner ── */}
      <AnimatePresence>
        {tournament.champion && (
          <ChampionBanner champion={tournament.champion} />
        )}
      </AnimatePresence>

      {/* ── Full bracket (read-only) ── */}
      {roundsMap.size === 0 ? (
        <motion.div
          className="tr-empty"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        >
          <Trophy size={56} className="tr-empty-icon" />
          <p>No bracket data available for this tournament.</p>
        </motion.div>
      ) : (
        <motion.div
          className="tr-bracket"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {[...roundsMap.entries()].map(([roundNumber, matchups]) => (
            <RoundSection
              key={roundNumber}
              roundNumber={roundNumber}
              matchups={matchups}
              totalRounds={totalRounds}
            />
          ))}
        </motion.div>
      )}
    </div>
  );
}
