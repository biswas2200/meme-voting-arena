import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Trophy,
  Check,
  AlertCircle,
  Loader,
  Clock,
  Image as ImageIcon,
  ArrowLeft,
  Info,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNotification } from '../contexts/NotificationContext';
import api from '../services/api';
import '../styles/CommonPages.css';
import '../styles/TournamentCreate.css';

// ── Animation variants ────────────────────────────────────────────────────────

const containerVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.08 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
};

// ── Round duration options ────────────────────────────────────────────────────

const ROUND_DURATIONS = [
  { value: 1, label: '1 Hour', description: 'Fast-paced competition' },
  { value: 6, label: '6 Hours', description: 'Half-day rounds' },
  { value: 24, label: '24 Hours', description: 'Full-day rounds' },
];

// ── Valid meme counts ─────────────────────────────────────────────────────────

const VALID_COUNTS = [8, 16];

// ── Helper: resolve image URL ─────────────────────────────────────────────────

function resolveImageUrl(imageUrl) {
  if (!imageUrl) return null;
  if (imageUrl.startsWith('http')) return imageUrl;
  const base = import.meta.env.VITE_API_URL || 'http://localhost:8080';
  return `${base}${imageUrl}`;
}

// ── Main component ────────────────────────────────────────────────────────────

const TournamentCreate = () => {
  const { user } = useAuth();
  const { showNotification } = useNotification();
  const navigate = useNavigate();

  // Form state
  const [name, setName] = useState('');
  const [roundDurationHours, setRoundDurationHours] = useState(24);
  const [selectedMemeIds, setSelectedMemeIds] = useState(new Set());

  // Meme gallery state
  const [memes, setMemes] = useState([]);
  const [memesLoading, setMemesLoading] = useState(true);
  const [memesError, setMemesError] = useState(null);

  // Submission state
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // ── Fetch meme gallery ──────────────────────────────────────────────────────

  const fetchMemes = useCallback(async () => {
    setMemesLoading(true);
    setMemesError(null);
    try {
      const res = await api.get('/api/memes');
      const data = res.data;
      // Handle both paginated and plain array responses
      const memesArray = data.content ?? (Array.isArray(data) ? data : []);
      setMemes(memesArray);
    } catch (err) {
      console.error('Failed to fetch memes:', err);
      setMemesError('Failed to load meme gallery. Please try again.');
    } finally {
      setMemesLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMemes();
  }, [fetchMemes]);

  // ── Meme selection ──────────────────────────────────────────────────────────

  const toggleMeme = (id) => {
    setSelectedMemeIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
    // Clear submit error when selection changes
    if (submitError) setSubmitError(null);
  };

  const selectedCount = selectedMemeIds.size;
  const isValidCount = VALID_COUNTS.includes(selectedCount);

  // ── Validation ──────────────────────────────────────────────────────────────

  const nameError = name.trim().length === 0 ? 'Tournament name is required.' : null;

  const countHint = (() => {
    if (selectedCount === 0) return 'Select 8 or 16 memes to continue.';
    if (selectedCount < 8) return `Select ${8 - selectedCount} more meme${8 - selectedCount !== 1 ? 's' : ''} (minimum 8).`;
    if (selectedCount > 8 && selectedCount < 16) return `Select ${16 - selectedCount} more to reach 16, or deselect ${selectedCount - 8} to reach 8.`;
    if (selectedCount > 16) return `Too many selected — deselect ${selectedCount - 16} meme${selectedCount - 16 !== 1 ? 's' : ''}.`;
    return null; // exactly 8 or 16
  })();

  const canSubmit = !nameError && isValidCount && !submitting;

  // ── Submit ──────────────────────────────────────────────────────────────────

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;

    setSubmitting(true);
    setSubmitError(null);

    const payload = {
      name: name.trim(),
      memeIds: Array.from(selectedMemeIds),
      roundDurationHours,
    };

    try {
      await api.post('/api/battle/tournaments', payload);
      showNotification('Tournament created! It is now pending admin approval.', 'success');
      navigate('/battle/tournaments');
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        'Failed to create tournament. Please try again.';
      setSubmitError(msg);
      showNotification(msg, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="page-container">
      {/* Header */}
      <motion.div
        className="page-header"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        <motion.div className="page-title-section" variants={itemVariants}>
          <button
            className="btn btn-secondary btn-sm tc-back-btn"
            onClick={() => navigate('/battle/tournaments')}
            aria-label="Back to tournaments"
          >
            <ArrowLeft size={16} />
            Back
          </button>
          <h1 className="page-title" style={{ marginTop: '1rem' }}>
            <Trophy size={36} />
            Create Tournament
          </h1>
          <p className="page-description">
            Pick 8 or 16 memes, set a name and round duration, then submit for admin approval.
          </p>
        </motion.div>
      </motion.div>

      <form onSubmit={handleSubmit} noValidate>
        <motion.div
          className="tc-layout"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {/* ── Left column: settings ── */}
          <motion.div className="tc-settings-panel content-card" variants={itemVariants}>
            {/* Tournament name */}
            <div className="tc-field-group">
              <label htmlFor="tc-name" className="tc-label">
                Tournament Name
              </label>
              <input
                id="tc-name"
                type="text"
                className={`tc-input${nameError && name.length > 0 ? ' tc-input-error' : ''}`}
                placeholder="Enter a tournament name…"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={120}
                autoComplete="off"
              />
              <div className="tc-char-count">{name.length}/120</div>
            </div>

            {/* Round duration */}
            <div className="tc-field-group">
              <label className="tc-label">
                <Clock size={16} />
                Round Duration
              </label>
              <div className="tc-duration-options" role="radiogroup" aria-label="Round duration">
                {ROUND_DURATIONS.map((opt) => (
                  <label
                    key={opt.value}
                    className={`tc-duration-option${roundDurationHours === opt.value ? ' tc-duration-selected' : ''}`}
                  >
                    <input
                      type="radio"
                      name="roundDuration"
                      value={opt.value}
                      checked={roundDurationHours === opt.value}
                      onChange={() => setRoundDurationHours(opt.value)}
                      className="tc-radio-hidden"
                    />
                    <span className="tc-duration-label">{opt.label}</span>
                    <span className="tc-duration-desc">{opt.description}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* Selection summary */}
            <div className={`tc-selection-summary${isValidCount ? ' tc-summary-valid' : ''}`}>
              <div className="tc-summary-count">
                <span className={`tc-count-number${isValidCount ? ' tc-count-valid' : ''}`}>
                  {selectedCount}
                </span>
                <span className="tc-count-label">/ 8 or 16 memes selected</span>
              </div>

              {/* Progress bar */}
              <div className="tc-progress-bar" aria-hidden="true">
                <div
                  className={`tc-progress-fill${isValidCount ? ' tc-progress-valid' : ''}`}
                  style={{ width: `${Math.min((selectedCount / 16) * 100, 100)}%` }}
                />
                {/* Markers at 8 and 16 */}
                <div className="tc-progress-marker" style={{ left: '50%' }} aria-hidden="true" />
              </div>

              {countHint && (
                <p className="tc-count-hint">
                  <Info size={13} />
                  {countHint}
                </p>
              )}

              {isValidCount && (
                <p className="tc-count-ok">
                  <Check size={13} />
                  {selectedCount} memes selected — ready to submit!
                </p>
              )}
            </div>

            {/* Submit error */}
            <AnimatePresence>
              {submitError && (
                <motion.div
                  className="tc-error-banner"
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  role="alert"
                >
                  <AlertCircle size={16} />
                  {submitError}
                </motion.div>
              )}
            </AnimatePresence>

            {/* Submit button */}
            <motion.button
              type="submit"
              className="btn btn-primary tc-submit-btn"
              disabled={!canSubmit}
              whileHover={canSubmit ? { scale: 1.02 } : {}}
              whileTap={canSubmit ? { scale: 0.98 } : {}}
            >
              {submitting ? (
                <>
                  <Loader size={18} className="tc-spin" />
                  Submitting…
                </>
              ) : (
                <>
                  <Trophy size={18} />
                  Create Tournament
                </>
              )}
            </motion.button>

            <p className="tc-approval-note">
              <Info size={13} />
              Tournaments require admin approval before going live.
            </p>
          </motion.div>

          {/* ── Right column: meme picker ── */}
          <motion.div className="tc-picker-panel" variants={itemVariants}>
            <div className="tc-picker-header">
              <h2 className="tc-picker-title">
                <ImageIcon size={20} />
                Meme Gallery
              </h2>
              {selectedCount > 0 && (
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => setSelectedMemeIds(new Set())}
                >
                  Clear selection
                </button>
              )}
            </div>

            {memesLoading ? (
              <div className="loading-placeholder" style={{ minHeight: 300 }}>
                <Loader className="loading-spinner" size={40} />
              </div>
            ) : memesError ? (
              <div className="tc-gallery-error">
                <AlertCircle size={40} />
                <p>{memesError}</p>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={fetchMemes}
                >
                  Retry
                </button>
              </div>
            ) : memes.length === 0 ? (
              <div className="tc-gallery-empty">
                <ImageIcon size={48} />
                <p>No memes in the gallery yet.</p>
              </div>
            ) : (
              <div className="tc-meme-grid" role="group" aria-label="Select memes for tournament">
                {memes.map((meme) => {
                  const selected = selectedMemeIds.has(meme.id);
                  const imgSrc = resolveImageUrl(meme.imageUrl || meme.filePath);
                  return (
                    <motion.button
                      key={meme.id}
                      type="button"
                      className={`tc-meme-card${selected ? ' tc-meme-selected' : ''}`}
                      onClick={() => toggleMeme(meme.id)}
                      aria-pressed={selected}
                      aria-label={`${selected ? 'Deselect' : 'Select'} ${meme.title}`}
                      whileHover={{ scale: 1.03 }}
                      whileTap={{ scale: 0.97 }}
                      transition={{ duration: 0.15 }}
                    >
                      {/* Checkbox overlay */}
                      <div className={`tc-meme-checkbox${selected ? ' tc-checkbox-checked' : ''}`} aria-hidden="true">
                        {selected && <Check size={14} strokeWidth={3} />}
                      </div>

                      {/* Image */}
                      <div className="tc-meme-img-wrap">
                        {imgSrc ? (
                          <img
                            src={imgSrc}
                            alt={meme.title}
                            className="tc-meme-img"
                            loading="lazy"
                            onError={(e) => {
                              e.target.style.display = 'none';
                            }}
                          />
                        ) : (
                          <div className="tc-meme-img-placeholder">
                            <ImageIcon size={32} />
                          </div>
                        )}
                      </div>

                      {/* Title */}
                      <div className="tc-meme-title">{meme.title}</div>
                    </motion.button>
                  );
                })}
              </div>
            )}
          </motion.div>
        </motion.div>
      </form>
    </div>
  );
};

export default TournamentCreate;
