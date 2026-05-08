import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  User, Edit, Trophy, Image, ThumbsUp, ThumbsDown,
  Zap, Calendar, Shield, Star, TrendingUp, Award, Loader, Camera
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNotification } from '../contexts/NotificationContext';
import api from '../services/api';
import EditProfileModal from '../components/profile/EditProfileModal';
import '../styles/CommonPages.css';
import '../styles/Profile.css';

const containerVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.08 } }
};
const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.45, ease: 'easeOut' } }
};

/* ─── small helpers ─────────────────────────────────────── */
const StatCard = ({ icon: Icon, label, value, accent }) => (
  <motion.div className={`profile-stat-card ${accent ? 'accent' : ''}`} variants={itemVariants}>
    <div className="psc-icon"><Icon size={22} /></div>
    <div className="psc-body">
      <span className="psc-value">{value ?? 0}</span>
      <span className="psc-label">{label}</span>
    </div>
  </motion.div>
);

const AvatarUploader = ({ stats, onUploaded }) => {
  const inputRef = React.useRef(null);
  const [uploading, setUploading] = React.useState(false);
  const { error: notifyError } = useNotification();

  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { notifyError('Only image files allowed', 'Error'); return; }
    if (file.size > 2 * 1024 * 1024) { notifyError('Avatar must be under 2 MB', 'Error'); return; }

    setUploading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await api.post('/api/auth/profile/avatar', form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      onUploaded(res.data);
    } catch (err) {
      notifyError(err.response?.data?.message || 'Upload failed', 'Error');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const avatarSrc = stats.avatarUrl
    ? (stats.avatarUrl.startsWith('http') ? stats.avatarUrl : `${import.meta.env.VITE_API_URL || ''}${stats.avatarUrl}`)
    : null;

  return (
    <div className="profile-avatar-wrap">
      <motion.div
        className="profile-avatar profile-avatar-clickable"
        onClick={() => !uploading && inputRef.current?.click()}
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
        title="Click to change avatar"
      >
        {uploading ? (
          <Loader size={28} className="profile-loader-icon spin-slow" />
        ) : avatarSrc ? (
          <img src={avatarSrc} alt="avatar"
            onError={e => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }}
          />
        ) : null}
        {/* Fallback initial — always rendered, hidden when img loads */}
        <span style={{ display: avatarSrc && !uploading ? 'none' : 'flex' }}>
          {stats.username?.charAt(0).toUpperCase()}
        </span>
        <div className="avatar-overlay">
          <Camera size={18} />
        </div>
      </motion.div>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      <span className={`profile-role-badge ${stats.role?.toLowerCase()}`}>
        {stats.role === 'ADMIN' ? <Shield size={12} /> : <Star size={12} />}
        {stats.role}
      </span>
    </div>
  );
};

const MemeThumb = ({ meme, rank }) => (
  <motion.div className="profile-meme-thumb" variants={itemVariants} whileHover={{ scale: 1.04 }}>
    {rank && <span className="thumb-rank">#{rank}</span>}
    <img
      src={meme.imageUrl?.startsWith('http') ? meme.imageUrl : `${import.meta.env.VITE_API_URL || ''}${meme.imageUrl}`}
      alt={meme.title}
      onError={e => { e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjE1MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMWExYTJlIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iIzAwY2NmZiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPk5vIEltYWdlPC90ZXh0Pjwvc3ZnPg=='; }}
    />
    <div className="thumb-info">
      <p className="thumb-title">{meme.title}</p>
      <span className="thumb-votes">
        <ThumbsUp size={12} /> {meme.voteCount ?? 0}
      </span>
    </div>
  </motion.div>
);

/* ─── main component ─────────────────────────────────────── */
const Profile = () => {
  const { user, token } = useAuth();
  const { success, error: notifyError } = useNotification();

  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showEdit, setShowEdit] = useState(false);
  const [activeTab, setActiveTab] = useState('overview'); // overview | memes

  const fetchStats = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.get('/api/auth/profile');
      setStats(res.data);
    } catch (err) {
      // 401 = token invalid/expired — AuthContext will handle clearing session
      // Don't show error toast for auth failures, only for genuine server errors
      if (err.response?.status !== 401 && err.response?.status !== 400) {
        notifyError('Failed to load profile data', 'Error');
      }
    } finally {
      setLoading(false);
    }
  }, [notifyError]);

  useEffect(() => { fetchStats(); }, [fetchStats]);

  const handleProfileSaved = (updatedStats) => {
    setStats(updatedStats);
    setShowEdit(false);
    success('Profile updated successfully!', 'Saved');
  };

  /* ── loading ── */
  if (loading) {
    return (
      <div className="page-container profile-loading">
        <motion.div animate={{ rotate: 360 }} transition={{ duration: 1.2, repeat: Infinity, ease: 'linear' }}>
          <Loader size={48} className="profile-loader-icon" />
        </motion.div>
        <p>Loading warrior data…</p>
      </div>
    );
  }

  /* ── no data ── */
  if (!stats) return null;

  const joinDate = stats.createdAt
    ? new Date(stats.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
    : '—';

  const winRate = stats.totalVotesReceived > 0
    ? Math.round((stats.totalUpvotes / stats.totalVotesReceived) * 100)
    : 0;

  return (
    <div className="page-container">
      {/* ── header ── */}
      <motion.div className="page-header" variants={containerVariants} initial="hidden" animate="visible">
        <motion.div className="page-title-section" variants={itemVariants}>
          <h1 className="page-title"><User size={36} /> Warrior Profile</h1>
          <p className="page-description">Your arena presence and battle statistics.</p>
        </motion.div>
        <motion.div className="page-actions" variants={itemVariants}>
          <motion.button
            className="btn btn-primary"
            onClick={() => setShowEdit(true)}
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
          >
            <Edit size={16} /> Edit Profile
          </motion.button>
        </motion.div>
      </motion.div>

      {/* ── hero card ── */}
      <motion.div className="profile-hero" variants={containerVariants} initial="hidden" animate="visible">
        <AvatarUploader stats={stats} onUploaded={(updated) => setStats(updated)} />

        <motion.div className="profile-hero-info" variants={itemVariants}>
          <h2 className="profile-username">{stats.username}</h2>
          <p className="profile-email">{stats.email}</p>
          <div className="profile-meta-row">
            <span className="profile-keyword">
              <Zap size={14} /> {stats.keyword}
            </span>
            <span className="profile-joined">
              <Calendar size={14} /> Joined {joinDate}
            </span>
          </div>
          <div className="profile-winrate-bar">
            <span className="winrate-label">Approval rate</span>
            <div className="winrate-track">
              <motion.div
                className="winrate-fill"
                initial={{ width: 0 }}
                animate={{ width: `${winRate}%` }}
                transition={{ duration: 1, ease: 'easeOut', delay: 0.3 }}
              />
            </div>
            <span className="winrate-pct">{winRate}%</span>
          </div>
        </motion.div>
      </motion.div>

      {/* ── stat cards ── */}
      <motion.div className="profile-stats-grid" variants={containerVariants} initial="hidden" animate="visible">
        <StatCard icon={Image}      label="Memes Uploaded"  value={stats.totalMemesUploaded} />
        <StatCard icon={TrendingUp} label="Total Votes"     value={stats.totalVotesReceived} />
        <StatCard icon={ThumbsUp}   label="Upvotes"         value={stats.totalUpvotes}  accent />
        <StatCard icon={ThumbsDown} label="Downvotes"       value={stats.totalDownvotes} />
        <StatCard icon={Award}      label="Votes Cast"      value={stats.totalVotesCast} />
        <StatCard icon={Trophy}     label="Approval Rate"   value={`${winRate}%`} accent />
      </motion.div>

      {/* ── tabs ── */}
      <div className="profile-tabs">
        {['overview', 'memes'].map(tab => (
          <button
            key={tab}
            className={`profile-tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {tab === 'overview' ? <><Trophy size={15} /> Overview</> : <><Image size={15} /> My Memes</>}
          </button>
        ))}
      </div>

      {/* ── tab content ── */}
      <AnimatePresence mode="wait">
        {activeTab === 'overview' && (
          <motion.div
            key="overview"
            className="profile-tab-content"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -16 }}
            transition={{ duration: 0.3 }}
          >
            {/* top memes */}
            <div className="profile-section">
              <h3 className="profile-section-title"><Trophy size={18} /> Top Memes</h3>
              {stats.topMemes?.length > 0 ? (
                <motion.div className="profile-memes-row" variants={containerVariants} initial="hidden" animate="visible">
                  {stats.topMemes.map((m, i) => <MemeThumb key={m.id} meme={m} rank={i + 1} />)}
                </motion.div>
              ) : (
                <p className="profile-empty">No memes uploaded yet. Upload your first meme!</p>
              )}
            </div>

            {/* recent memes */}
            <div className="profile-section">
              <h3 className="profile-section-title"><Zap size={18} /> Recent Uploads</h3>
              {stats.recentMemes?.length > 0 ? (
                <motion.div className="profile-memes-row" variants={containerVariants} initial="hidden" animate="visible">
                  {stats.recentMemes.map(m => <MemeThumb key={m.id} meme={m} />)}
                </motion.div>
              ) : (
                <p className="profile-empty">Nothing here yet.</p>
              )}
            </div>
          </motion.div>
        )}

        {activeTab === 'memes' && (
          <motion.div
            key="memes"
            className="profile-tab-content"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -16 }}
            transition={{ duration: 0.3 }}
          >
            <div className="profile-section">
              <h3 className="profile-section-title"><Image size={18} /> All Uploaded Memes ({stats.totalMemesUploaded})</h3>
              {stats.recentMemes?.length > 0 ? (
                <motion.div className="profile-memes-grid" variants={containerVariants} initial="hidden" animate="visible">
                  {stats.recentMemes.map(m => <MemeThumb key={m.id} meme={m} />)}
                </motion.div>
              ) : (
                <p className="profile-empty">You haven't uploaded any memes yet.</p>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── edit modal ── */}
      <AnimatePresence>
        {showEdit && (
          <EditProfileModal
            stats={stats}
            onClose={() => setShowEdit(false)}
            onSaved={handleProfileSaved}
          />
        )}
      </AnimatePresence>
    </div>
  );
};

export default Profile;
