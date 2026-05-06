import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Swords, ThumbsUp, RefreshCw, AlertTriangle, ImageOff, Timer, CheckCircle } from 'lucide-react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import api from '../../services/api';
import './QuickBattle.css';

// ── Animation variants ──────────────────────────────────────────────────────

const cardVariants = {
  hidden:  { opacity: 0, y: 30 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.45, ease: 'easeOut' } },
  exit:    { opacity: 0, y: -20, transition: { duration: 0.3, ease: 'easeIn' } },
};

const vsVariants = {
  hidden:  { opacity: 0, scale: 0.6 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.4, ease: 'backOut' } },
};

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Compute the vote percentage for one meme given both vote counts.
 * Returns 50 when both are 0 (no votes yet).
 */
function votePercent(myVotes, otherVotes) {
  const total = myVotes + otherVotes;
  if (total === 0) return 50;
  return Math.round((myVotes / total) * 100);
}

// ── Sub-component: MemeCard ──────────────────────────────────────────────────

function MemeCard({ meme, voteCount, totalVotes, hasVoted, isVotedFor, onVote, votingDisabled }) {
  const [imgError, setImgError] = useState(false);
  const pct = votePercent(voteCount, totalVotes - voteCount);

  return (
    <motion.div
      className={`battle-meme-card${isVotedFor ? ' voted-card' : ''}`}
      variants={cardVariants}
      initial="hidden"
      animate="visible"
      exit="exit"
      layout
    >
      {/* Image */}
      <div className="battle-meme-image-wrapper">
        {meme.imageUrl && !imgError ? (
          <img
            className="battle-meme-image"
            src={meme.imageUrl}
            alt={meme.title}
            onError={() => setImgError(true)}
          />
        ) : (
          <div className="battle-meme-image-placeholder">
            <ImageOff size={48} />
          </div>
        )}

        {/* "Your vote" overlay */}
        <AnimatePresence>
          {isVotedFor && (
            <motion.div
              className="voted-overlay"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            >
              <div className="voted-badge">
                <CheckCircle size={16} />
                Your Vote
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Info */}
      <div className="battle-meme-info">
        <h3 className="battle-meme-title">{meme.title}</h3>

        {/* Vote count */}
        <div className="battle-vote-count">
          <ThumbsUp size={16} />
          <span className="vote-count-number">{voteCount}</span>
          <span>votes</span>
        </div>

        {/* Vote bar — only shown after voting */}
        <AnimatePresence>
          {hasVoted && (
            <motion.div
              className="vote-bar-wrapper"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
            >
              <div className="vote-bar-track">
                <div className="vote-bar-fill" style={{ width: `${pct}%` }} />
              </div>
              <span className="vote-bar-label">{pct}%</span>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Vote button */}
        <motion.button
          className="battle-vote-btn"
          onClick={() => onVote(meme.id)}
          disabled={votingDisabled}
          whileHover={votingDisabled ? {} : { scale: 1.02 }}
          whileTap={votingDisabled ? {} : { scale: 0.97 }}
          aria-label={`Vote for ${meme.title}`}
        >
          <ThumbsUp size={16} />
          {isVotedFor ? 'Voted!' : 'Vote'}
        </motion.button>
      </div>
    </motion.div>
  );
}

// ── Main component ───────────────────────────────────────────────────────────

export default function QuickBattle() {
  // ── State ──
  const [pair, setPair]           = useState(null);   // BattlePairResponse
  const [voteCounts, setVoteCounts] = useState({ a: 0, b: 0 }); // live counts (updated by WS too)
  const [votedMemeId, setVotedMemeId] = useState(null); // id of the meme the user voted for
  const [loading, setLoading]     = useState(true);
  const [voting, setVoting]       = useState(false);
  const [error, setError]         = useState(null);   // { type: 'insufficient' | 'generic', message }
  const [countdown, setCountdown] = useState(false);  // true while 1.5 s auto-advance is running

  // Ref so WebSocket handler (task 11.3) can always read the latest pairId
  const pairRef = useRef(null);

  // ── Fetch a new pair ──────────────────────────────────────────────────────

  const fetchPair = useCallback(async () => {
    setLoading(true);
    setError(null);
    setVotedMemeId(null);
    setCountdown(false);

    try {
      const res = await api.get('/api/battle/quick/pair');
      const data = res.data; // BattlePairResponse: { pairId, memeA, memeB }
      setPair(data);
      pairRef.current = data;
      setVoteCounts({
        a: data.memeA.voteCount ?? 0,
        b: data.memeB.voteCount ?? 0,
      });
    } catch (err) {
      const status  = err.response?.status;
      const message = err.response?.data?.message || '';

      if (status === 400 && message.toLowerCase().includes('insufficient')) {
        setError({
          type: 'insufficient',
          message: 'There aren\'t enough memes in the gallery to start a battle. Add at least 2 memes first.',
        });
      } else {
        setError({
          type: 'generic',
          message: message || 'Failed to load a battle pair. Please try again.',
        });
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // Fetch on mount
  useEffect(() => {
    fetchPair();
  }, [fetchPair]);

  // ── WebSocket vote-count updates ──────────────────────────────────────────

  const handleWsMessage = useCallback((payload) => {
    // payload: { pairId, memeAId, memeBId, votesA, votesB }
    if (!pairRef.current) return;
    if (payload.pairId !== pairRef.current.pairId) return;
    setVoteCounts({ a: payload.votesA, b: payload.votesB });
  }, []);

  // STOMP WebSocket subscription to /topic/battle/quick (Requirements 3.1, 3.2, 3.3)
  useEffect(() => {
    const wsUrl = `${window.location.protocol}//${window.location.host}/ws`;

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe('/topic/battle/quick', (message) => {
          try {
            const payload = JSON.parse(message.body);
            handleWsMessage(payload);
          } catch (e) {
            // Ignore malformed messages
          }
        });
      },
    });

    stompClient.activate();

    // Clean up on unmount
    return () => {
      stompClient.deactivate();
    };
  }, [handleWsMessage]);

  // ── Voting ────────────────────────────────────────────────────────────────

  const handleVote = useCallback(async (chosenMemeId) => {
    if (!pair || voting || votedMemeId) return;

    setVoting(true);
    try {
      const res = await api.post('/api/battle/vote/quick', {
        battlePairId: pair.pairId,
        chosenMemeId,
      });
      // BattleVoteResult: { pairId, memeAVotes, memeBVotes, chosenMemeId }
      const result = res.data;
      setVotedMemeId(chosenMemeId);
      setVoteCounts({ a: result.memeAVotes, b: result.memeBVotes });

      // Start 1.5 s countdown then auto-fetch next pair (Requirement 4.1)
      setCountdown(true);
      setTimeout(() => {
        setCountdown(false);
        fetchPair();
      }, 1500);
    } catch (err) {
      const status  = err.response?.status;
      const message = err.response?.data?.message || '';

      if (status === 409) {
        // Already voted — disable controls and show counts if available
        setVotedMemeId(chosenMemeId);
      } else {
        setError({
          type: 'generic',
          message: message || 'Failed to submit your vote. Please try again.',
        });
      }
    } finally {
      setVoting(false);
    }
  }, [pair, voting, votedMemeId, fetchPair]);

  // ── Derived state ─────────────────────────────────────────────────────────

  const votingDisabled = !!votedMemeId || voting || countdown;
  const totalVotes     = voteCounts.a + voteCounts.b;

  // ── Render: loading ───────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="quick-battle-container">
        <div className="quick-battle-loading">
          <motion.div
            className="loading-spinner"
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
            style={{ width: 56, height: 56 }}
          />
          <p>Loading battle pair…</p>
        </div>
      </div>
    );
  }

  // ── Render: error ─────────────────────────────────────────────────────────

  if (error) {
    return (
      <div className="quick-battle-container">
        <motion.div
          className="quick-battle-error"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <AlertTriangle size={56} className="quick-battle-error-icon" />
          <h2>
            {error.type === 'insufficient' ? 'Not Enough Memes' : 'Something Went Wrong'}
          </h2>
          <p>{error.message}</p>
          {error.type !== 'insufficient' && (
            <div className="quick-battle-error-actions">
              <motion.button
                className="btn btn-primary"
                onClick={fetchPair}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                <RefreshCw size={16} />
                Try Again
              </motion.button>
            </div>
          )}
        </motion.div>
      </div>
    );
  }

  // ── Render: battle ────────────────────────────────────────────────────────

  return (
    <div className="quick-battle-container">
      {/* Header */}
      <div className="quick-battle-header">
        <div>
          <h1 className="quick-battle-title">
            <Swords size={32} />
            Quick Battle
          </h1>
          <p className="quick-battle-subtitle">
            Vote for your favorite — the next pair loads automatically after each vote.
          </p>
        </div>

        <motion.button
          className="btn btn-secondary"
          onClick={fetchPair}
          disabled={loading || voting}
          whileHover={{ scale: 1.03 }}
          whileTap={{ scale: 0.97 }}
          aria-label="Skip to next pair"
        >
          <RefreshCw size={16} />
          Skip
        </motion.button>
      </div>

      {/* Battle arena */}
      <AnimatePresence mode="wait">
        {pair && (
          <motion.div
            key={pair.pairId}
            className="quick-battle-arena"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            {/* Meme cards + VS divider */}
            <div className="battle-memes-row">
              <MemeCard
                meme={pair.memeA}
                voteCount={voteCounts.a}
                totalVotes={totalVotes}
                hasVoted={!!votedMemeId}
                isVotedFor={votedMemeId === pair.memeA.id}
                onVote={handleVote}
                votingDisabled={votingDisabled}
              />

              <div className="battle-vs-divider">
                <motion.span
                  className="battle-vs-text"
                  variants={vsVariants}
                  initial="hidden"
                  animate="visible"
                >
                  VS
                </motion.span>
              </div>

              <MemeCard
                meme={pair.memeB}
                voteCount={voteCounts.b}
                totalVotes={totalVotes}
                hasVoted={!!votedMemeId}
                isVotedFor={votedMemeId === pair.memeB.id}
                onVote={handleVote}
                votingDisabled={votingDisabled}
              />
            </div>

            {/* Countdown bar — shown after voting (Requirement 4.2) */}
            <AnimatePresence>
              {countdown && (
                <motion.div
                  className="battle-countdown-bar"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.25 }}
                >
                  <div className="battle-countdown-text">
                    <Timer size={16} />
                    Loading next pair…
                  </div>
                  <div className="battle-countdown-progress">
                    <div className="battle-countdown-fill" />
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
