import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Zap, Trophy, Swords, Timer, LogIn, X } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import '../styles/CommonPages.css';

const BattleArena = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [loginPrompt, setLoginPrompt] = useState(null); // null | 'quick-battle' | 'create-tournament'

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.15
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.5,
        ease: 'easeOut'
      }
    }
  };

  const handleStartQuickBattle = () => {
    if (!user) {
      setLoginPrompt('quick-battle');
      return;
    }
    navigate('/battle/quick');
  };

  const handleViewTournaments = () => {
    navigate('/battle/tournaments');
  };

  const handleCreateTournament = () => {
    if (!user) {
      setLoginPrompt('create-tournament');
      return;
    }
    navigate('/battle/tournaments/new');
  };

  const handleGoToLogin = () => {
    navigate('/login');
  };

  const dismissPrompt = () => {
    setLoginPrompt(null);
  };

  const promptMessages = {
    'quick-battle': 'You need to be logged in to start a Quick Battle and cast votes.',
    'create-tournament': 'You need to be logged in to create a Tournament.'
  };

  return (
    <div className="page-container">
      <motion.div
        className="page-header"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        <motion.div className="page-title-section" variants={itemVariants}>
          <h1 className="page-title">
            <Zap size={40} />
            Battle Arena
          </h1>
          <p className="page-description">
            Enter the ultimate meme combat zone. Choose your battle mode and prove your meme supremacy.
          </p>
        </motion.div>
      </motion.div>

      {/* Login prompt banner */}
      <AnimatePresence>
        {loginPrompt && (
          <motion.div
            className="login-prompt-banner"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.3 }}
          >
            <div className="login-prompt-content">
              <LogIn size={20} />
              <span>{promptMessages[loginPrompt]}</span>
              <div className="login-prompt-actions">
                <button className="btn btn-primary btn-sm" onClick={handleGoToLogin}>
                  Log In
                </button>
                <button className="btn btn-ghost btn-sm" onClick={dismissPrompt} aria-label="Dismiss">
                  <X size={16} />
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Mode cards */}
      <motion.div
        className="content-grid grid-2"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {/* Quick Battle card */}
        <motion.div
          className="content-card battle-mode-card"
          variants={itemVariants}
          whileHover={{ y: -6 }}
          transition={{ duration: 0.3 }}
        >
          <div className="card-header">
            <div className="card-icon">
              <Swords size={28} />
            </div>
            <h2 className="card-title">Quick Battle</h2>
          </div>

          <div className="card-content">
            <p className="battle-mode-description">
              Jump straight into action. The system picks two random memes and you vote for your
              favorite. After each vote, the next pair loads automatically — fast, fun, and
              endlessly replayable.
            </p>

            {!user && (
              <p className="auth-notice">
                <LogIn size={14} />
                Log in to cast votes. Browsing is open to everyone.
              </p>
            )}
          </div>

          <div className="card-actions">
            <motion.button
              className="btn btn-primary"
              onClick={handleStartQuickBattle}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
            >
              <Zap size={18} />
              Start Quick Battle
            </motion.button>
          </div>
        </motion.div>

        {/* Tournament Mode card */}
        <motion.div
          className="content-card battle-mode-card"
          variants={itemVariants}
          whileHover={{ y: -6 }}
          transition={{ duration: 0.3 }}
        >
          <div className="card-header">
            <div className="card-icon">
              <Trophy size={28} />
            </div>
            <h2 className="card-title">Tournament Mode</h2>
          </div>

          <div className="card-content">
            <p className="battle-mode-description">
              Organize or join a structured bracket competition. Select 8 or 16 memes, set a round
              duration, and watch the bracket unfold as rounds advance automatically. The last meme
              standing is crowned champion.
            </p>

            {!user && (
              <p className="auth-notice">
                <LogIn size={14} />
                Log in to create tournaments and vote. Viewing brackets is open to everyone.
              </p>
            )}
          </div>

          <div className="card-actions">
            <motion.button
              className="btn btn-secondary"
              onClick={handleViewTournaments}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
            >
              <Timer size={18} />
              View Tournaments
            </motion.button>

            <motion.button
              className="btn btn-primary"
              onClick={handleCreateTournament}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
            >
              <Trophy size={18} />
              Create Tournament
            </motion.button>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
};

export default BattleArena;
