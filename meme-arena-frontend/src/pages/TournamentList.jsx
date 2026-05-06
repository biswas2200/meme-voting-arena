import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Trophy,
  Plus,
  Clock,
  CheckCircle,
  XCircle,
  Hourglass,
  Users,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Loader,
  ShieldAlert,
  ThumbsUp,
  ThumbsDown,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNotification } from '../contexts/NotificationContext';
import api from '../services/api';
import '../styles/CommonPages.css';
import '../styles/TournamentList.css';

// ── Status helpers ────────────────────────────────────────────────────────────

const STATUS_TABS = [
  { key: 'ALL', label: 'All' },
  { key: 'ACTIVE', label: 'Active' },
  { key: 'COMPLETED', label: 'Completed' },
];

const ADMIN_TAB = { key: 'PENDING_APPROVAL', label: 'Pending Approval' };

function StatusBadge({ status }) {
  const config = {
    ACTIVE: { icon: <Clock size={13} />, label: 'Active', cls: 'badge-active' },
    COMPLETED: { icon: <CheckCircle size={13} />, label: 'Completed', cls: 'badge-completed' },
    PENDING_APPROVAL: { icon: <Hourglass size={13} />, label: 'Pending', cls: 'badge-pending' },
    REJECTED: { icon: <XCircle size={13} />, label: 'Rejected', cls: 'badge-rejected' },
  };
  const { icon, label, cls } = config[status] ?? { icon: null, label: status, cls: '' };
  return (
    <span className={`status-badge ${cls}`}>
      {icon}
      {label}
    </span>
  );
}

// ── Animation variants ────────────────────────────────────────────────────────

const containerVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.08 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
};

// ── Confirmation dialog ───────────────────────────────────────────────────────

function ConfirmDialog({ isOpen, title, message, confirmLabel, confirmVariant, onConfirm, onCancel }) {
  if (!isOpen) return null;

  return (
    <div className="confirm-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title">
      <motion.div
        className="confirm-dialog"
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.9 }}
        transition={{ duration: 0.2 }}
      >
        <h3 id="confirm-dialog-title" className="confirm-dialog-title">{title}</h3>
        <p className="confirm-dialog-message">{message}</p>
        <div className="confirm-dialog-actions">
          <button className="btn btn-secondary btn-sm" onClick={onCancel}>
            Cancel
          </button>
          <button
            className={`btn btn-sm ${confirmVariant === 'danger' ? 'btn-danger' : 'btn-success'}`}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </motion.div>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

const TournamentList = () => {
  const { user } = useAuth();
  const { showNotification } = useNotification();
  const navigate = useNavigate();

  const isAdmin = user?.role === 'ROLE_ADMIN' || user?.role === 'ADMIN';

  const tabs = isAdmin ? [...STATUS_TABS, ADMIN_TAB] : STATUS_TABS;

  const [activeTab, setActiveTab] = useState('ALL');
  const [tournaments, setTournaments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Confirmation dialog state
  const [confirmDialog, setConfirmDialog] = useState({
    isOpen: false,
    title: '',
    message: '',
    confirmLabel: '',
    confirmVariant: 'success',
    onConfirm: null,
  });
  const [actionLoading, setActionLoading] = useState(false);

  const PAGE_SIZE = 9;

  // ── Fetch ─────────────────────────────────────────────────────────────────

  const fetchTournaments = useCallback(async () => {
    setLoading(true);
    try {
      if (activeTab === 'PENDING_APPROVAL') {
        // Admin-only endpoint — returns a plain list, not paginated
        const res = await api.get('/api/battle/tournaments/pending');
        setTournaments(res.data);
        setTotalPages(1);
      } else {
        const params = { page, size: PAGE_SIZE };
        if (activeTab !== 'ALL') params.status = activeTab;
        const res = await api.get('/api/battle/tournaments', { params });
        const data = res.data;
        // Spring Page response
        setTournaments(data.content ?? data);
        setTotalPages(data.totalPages ?? 1);
      }
    } catch (err) {
      console.error('Failed to fetch tournaments:', err);
      showNotification('Failed to load tournaments. Please try again.', 'error');
      setTournaments([]);
    } finally {
      setLoading(false);
    }
  }, [activeTab, page, showNotification]);

  useEffect(() => {
    fetchTournaments();
  }, [fetchTournaments]);

  // Reset to page 0 when tab changes
  const handleTabChange = (key) => {
    setActiveTab(key);
    setPage(0);
  };

  // ── Admin actions ─────────────────────────────────────────────────────────

  const closeConfirmDialog = () => {
    setConfirmDialog((prev) => ({ ...prev, isOpen: false, onConfirm: null }));
  };

  const handleApprove = (tournament) => {
    setConfirmDialog({
      isOpen: true,
      title: 'Approve Tournament',
      message: `Are you sure you want to approve "${tournament.name}"? This will make it live and start the first round timer.`,
      confirmLabel: 'Approve',
      confirmVariant: 'success',
      onConfirm: async () => {
        closeConfirmDialog();
        setActionLoading(true);
        try {
          await api.post(`/api/battle/tournaments/${tournament.id}/approve`);
          showNotification(`Tournament "${tournament.name}" has been approved and is now active.`, 'success');
          fetchTournaments();
        } catch (err) {
          const msg = err.response?.data?.message || 'Failed to approve tournament. Please try again.';
          showNotification(msg, 'error');
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  const handleReject = (tournament) => {
    setConfirmDialog({
      isOpen: true,
      title: 'Reject Tournament',
      message: `Are you sure you want to reject "${tournament.name}"? The creator will be notified.`,
      confirmLabel: 'Reject',
      confirmVariant: 'danger',
      onConfirm: async () => {
        closeConfirmDialog();
        setActionLoading(true);
        try {
          await api.post(`/api/battle/tournaments/${tournament.id}/reject`);
          showNotification(`Tournament "${tournament.name}" has been rejected.`, 'success');
          fetchTournaments();
        } catch (err) {
          const msg = err.response?.data?.message || 'Failed to reject tournament. Please try again.';
          showNotification(msg, 'error');
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="page-container">
      {/* Confirmation dialog */}
      <AnimatePresence>
        {confirmDialog.isOpen && (
          <ConfirmDialog
            isOpen={confirmDialog.isOpen}
            title={confirmDialog.title}
            message={confirmDialog.message}
            confirmLabel={confirmDialog.confirmLabel}
            confirmVariant={confirmDialog.confirmVariant}
            onConfirm={confirmDialog.onConfirm}
            onCancel={closeConfirmDialog}
          />
        )}
      </AnimatePresence>
      {/* Header */}
      <motion.div
        className="page-header"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        <motion.div className="page-title-section" variants={itemVariants}>
          <h1 className="page-title">
            <Trophy size={36} />
            Tournaments
          </h1>
          <p className="page-description">
            Browse bracket competitions, vote on matchups, and watch champions emerge.
          </p>
        </motion.div>

        <motion.div className="page-actions" variants={itemVariants}>
          {user ? (
            <motion.button
              className="btn btn-primary"
              onClick={() => navigate('/battle/tournaments/new')}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
            >
              <Plus size={18} />
              Create Tournament
            </motion.button>
          ) : (
            <motion.button
              className="btn btn-secondary"
              onClick={() => navigate('/login')}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
            >
              Log in to Create
            </motion.button>
          )}
        </motion.div>
      </motion.div>

      {/* Status filter tabs */}
      <motion.div
        className="tournament-tabs"
        variants={itemVariants}
        initial="hidden"
        animate="visible"
      >
        {tabs.map((tab) => (
          <button
            key={tab.key}
            className={`tab-btn ${activeTab === tab.key ? 'tab-active' : ''}`}
            onClick={() => handleTabChange(tab.key)}
          >
            {tab.key === 'PENDING_APPROVAL' && <ShieldAlert size={14} />}
            {tab.label}
          </button>
        ))}
      </motion.div>

      {/* Content */}
      {loading ? (
        <div className="loading-placeholder" style={{ minHeight: 300 }}>
          <Loader className="loading-spinner" size={40} />
        </div>
      ) : tournaments.length === 0 ? (
        <motion.div
          className="empty-state"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        >
          <Trophy size={64} className="empty-icon" />
          <h3>No tournaments found</h3>
          <p>
            {activeTab === 'PENDING_APPROVAL'
              ? 'No tournaments are awaiting approval.'
              : activeTab === 'ALL'
              ? 'Be the first to create a tournament!'
              : `No ${activeTab.toLowerCase()} tournaments right now.`}
          </p>
          {user && activeTab !== 'PENDING_APPROVAL' && (
            <button
              className="btn btn-primary"
              onClick={() => navigate('/battle/tournaments/new')}
            >
              <Plus size={16} />
              Create Tournament
            </button>
          )}
        </motion.div>
      ) : (
        <>
          <motion.div
            className="content-grid grid-3 tournament-grid"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
          >
            <AnimatePresence>
              {tournaments.map((t) => (
                <TournamentCard
                  key={t.id}
                  tournament={t}
                  showAdminActions={isAdmin && activeTab === 'PENDING_APPROVAL'}
                  onApprove={handleApprove}
                  onReject={handleReject}
                  actionLoading={actionLoading}
                />
              ))}
            </AnimatePresence>
          </motion.div>

          {/* Pagination */}
          {totalPages > 1 && (
            <motion.div
              className="pagination"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <button
                className="btn btn-secondary btn-sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                aria-label="Previous page"
              >
                <ChevronLeft size={16} />
              </button>
              <span className="pagination-info">
                Page {page + 1} of {totalPages}
              </span>
              <button
                className="btn btn-secondary btn-sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                aria-label="Next page"
              >
                <ChevronRight size={16} />
              </button>
            </motion.div>
          )}
        </>
      )}
    </div>
  );
};

// ── Tournament card ───────────────────────────────────────────────────────────

function TournamentCard({ tournament, showAdminActions, onApprove, onReject, actionLoading }) {
  const { id, name, creator, status, memeCount, createdAt } = tournament;

  const formattedDate = createdAt
    ? new Date(createdAt).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    : '—';

  const creatorName =
    typeof creator === 'string' ? creator : creator?.username ?? 'Unknown';

  return (
    <motion.div variants={itemVariants} layout>
      <Link to={`/battle/tournaments/${id}`} className="tournament-card-link">
        <motion.div
          className={`content-card tournament-card${showAdminActions ? ' tournament-card-admin' : ''}`}
          whileHover={{ y: -4 }}
          transition={{ duration: 0.25 }}
        >
          {/* Top row: name + status badge */}
          <div className="tournament-card-header">
            <h3 className="tournament-card-title">{name}</h3>
            <StatusBadge status={status} />
          </div>

          {/* Meta row */}
          <div className="tournament-card-meta">
            <span className="meta-item">
              <Users size={14} />
              {memeCount} memes
            </span>
            <span className="meta-item">
              <Calendar size={14} />
              {formattedDate}
            </span>
          </div>

          {/* Creator */}
          <div className="tournament-card-creator">
            by <span className="creator-name">{creatorName}</span>
          </div>

          {/* Admin approval actions */}
          {showAdminActions && (
            <div
              className="tournament-card-admin-actions"
              onClick={(e) => e.preventDefault()}
            >
              <button
                className="btn btn-success btn-sm admin-action-btn"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  onApprove(tournament);
                }}
                disabled={actionLoading}
                aria-label={`Approve tournament ${name}`}
              >
                <ThumbsUp size={14} />
                Approve
              </button>
              <button
                className="btn btn-danger btn-sm admin-action-btn"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  onReject(tournament);
                }}
                disabled={actionLoading}
                aria-label={`Reject tournament ${name}`}
              >
                <ThumbsDown size={14} />
                Reject
              </button>
            </div>
          )}
        </motion.div>
      </Link>
    </motion.div>
  );
}

export default TournamentList;
