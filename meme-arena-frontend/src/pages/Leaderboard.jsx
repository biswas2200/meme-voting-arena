import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Trophy, Crown, Medal, Star, TrendingUp, Users, Award, Loader, Zap } from 'lucide-react';
import { useNotification } from '../contexts/NotificationContext';
import '../styles/CommonPages.css';
import '../styles/Leaderboard.css';

const Leaderboard = () => {
  const { showNotification } = useNotification();
  
  const [leaderboardData, setLeaderboardData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewType, setViewType] = useState('memes'); // memes, users
  const [timeFrame, setTimeFrame] = useState('all'); // all, month, week

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1
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

  const rankVariants = {
    hidden: { opacity: 0, x: -20 },
    visible: {
      opacity: 1,
      x: 0,
      transition: {
        duration: 0.4,
        ease: 'easeOut'
      }
    },
    hover: {
      scale: 1.02,
      x: 10,
      transition: {
        duration: 0.2
      }
    }
  };

  // Fetch leaderboard data
  const fetchLeaderboard = async () => {
    try {
      setLoading(true);
      const response = await fetch('http://localhost:8080/api/memes/leaderboard');
      if (response.ok) {
        const data = await response.json();
        setLeaderboardData(data);
      } else {
        showNotification('Failed to load leaderboard', 'error');
      }
    } catch (error) {
      console.error('Error fetching leaderboard:', error);
      showNotification('Network error. Please try again.', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLeaderboard();
  }, [viewType, timeFrame]);

  // Get rank icon based on position
  const getRankIcon = (rank) => {
    switch (rank) {
      case 1:
        return <Crown size={24} className="rank-icon gold" />;
      case 2:
        return <Medal size={24} className="rank-icon silver" />;
      case 3:
        return <Award size={24} className="rank-icon bronze" />;
      default:
        return <Star size={20} className="rank-icon default" />;
    }
  };

  // Get rank class for styling
  const getRankClass = (rank) => {
    if (rank === 1) return 'rank-first';
    if (rank === 2) return 'rank-second';
    if (rank === 3) return 'rank-third';
    if (rank <= 10) return 'rank-top-ten';
    return 'rank-default';
  };

  // Calculate stats
  const totalMemes = leaderboardData.length;
  const totalVotes = leaderboardData.reduce((sum, item) => sum + (item.voteCount || 0), 0);
  const averageScore = totalMemes > 0 ? (totalVotes / totalMemes).toFixed(1) : 0;
  const topScore = leaderboardData.length > 0 ? leaderboardData[0]?.voteCount || 0 : 0;

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
            <Trophy size={40} />
            Leaderboard
          </h1>
          <p className="page-description">
            Witness the hall of fame. See who reigns supreme in the meme kingdom.
          </p>
        </motion.div>
        
        <motion.div className="page-actions" variants={itemVariants}>
          <button 
            className={`btn ${viewType === 'memes' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setViewType('memes')}
          >
            <Trophy size={18} />
            Top Memes
          </button>
          <button 
            className={`btn ${viewType === 'users' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setViewType('users')}
          >
            <Users size={18} />
            Top Creators
          </button>
        </motion.div>
      </motion.div>

      {/* Stats Section */}
      <motion.div
        className="leaderboard-stats"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        <motion.div className="stat-card" variants={itemVariants}>
          <div className="stat-icon">
            <Trophy size={24} />
          </div>
          <div className="stat-content">
            <div className="stat-value">{totalMemes}</div>
            <div className="stat-label">Total Memes</div>
          </div>
        </motion.div>

        <motion.div className="stat-card" variants={itemVariants}>
          <div className="stat-icon">
            <TrendingUp size={24} />
          </div>
          <div className="stat-content">
            <div className="stat-value">{totalVotes}</div>
            <div className="stat-label">Total Votes</div>
          </div>
        </motion.div>

        <motion.div className="stat-card" variants={itemVariants}>
          <div className="stat-icon">
            <Star size={24} />
          </div>
          <div className="stat-content">
            <div className="stat-value">{averageScore}</div>
            <div className="stat-label">Average Score</div>
          </div>
        </motion.div>

        <motion.div className="stat-card" variants={itemVariants}>
          <div className="stat-icon">
            <Zap size={24} />
          </div>
          <div className="stat-content">
            <div className="stat-value">{topScore}</div>
            <div className="stat-label">Top Score</div>
          </div>
        </motion.div>
      </motion.div>

      {loading ? (
        <motion.div
          className="loading-container"
          variants={itemVariants}
          initial="hidden"
          animate="visible"
        >
          <Loader className="loading-spinner" size={40} />
          <p>Loading rankings...</p>
        </motion.div>
      ) : leaderboardData.length === 0 ? (
        <motion.div
          className="empty-state"
          variants={itemVariants}
          initial="hidden"
          animate="visible"
        >
          <Trophy size={80} />
          <h3>No Rankings Yet</h3>
          <p>Be the first to upload a meme and claim your spot on the leaderboard!</p>
        </motion.div>
      ) : (
        <motion.div
          className="leaderboard-content"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {/* Top 3 Podium */}
          {leaderboardData.length >= 3 && (
            <motion.div className="podium-section" variants={itemVariants}>
              <h2 className="section-title">🏆 Top Champions 🏆</h2>
              <div className="podium">
                {/* Second Place */}
                <motion.div 
                  className="podium-place second"
                  variants={rankVariants}
                  whileHover="hover"
                >
                  <div className="podium-rank">2nd</div>
                  <div className="podium-content">
                    <Medal size={32} className="podium-icon silver" />
                    <img 
                      src={leaderboardData[1]?.imageUrl} 
                      alt={leaderboardData[1]?.title}
                      className="podium-image"
                      onError={(e) => {
                        e.target.style.display = 'none';
                      }}
                    />
                    <h3 className="podium-title">{leaderboardData[1]?.title}</h3>
                    <div className="podium-score">{leaderboardData[1]?.voteCount} votes</div>
                    <div className="podium-creator">by {leaderboardData[1]?.uploadedBy}</div>
                  </div>
                </motion.div>

                {/* First Place */}
                <motion.div 
                  className="podium-place first"
                  variants={rankVariants}
                  whileHover="hover"
                >
                  <div className="podium-rank">1st</div>
                  <div className="podium-content">
                    <Crown size={36} className="podium-icon gold" />
                    <img 
                      src={leaderboardData[0]?.imageUrl} 
                      alt={leaderboardData[0]?.title}
                      className="podium-image"
                      onError={(e) => {
                        e.target.style.display = 'none';
                      }}
                    />
                    <h3 className="podium-title">{leaderboardData[0]?.title}</h3>
                    <div className="podium-score">{leaderboardData[0]?.voteCount} votes</div>
                    <div className="podium-creator">by {leaderboardData[0]?.uploadedBy}</div>
                  </div>
                </motion.div>

                {/* Third Place */}
                <motion.div 
                  className="podium-place third"
                  variants={rankVariants}
                  whileHover="hover"
                >
                  <div className="podium-rank">3rd</div>
                  <div className="podium-content">
                    <Award size={28} className="podium-icon bronze" />
                    <img 
                      src={leaderboardData[2]?.imageUrl} 
                      alt={leaderboardData[2]?.title}
                      className="podium-image"
                      onError={(e) => {
                        e.target.style.display = 'none';
                      }}
                    />
                    <h3 className="podium-title">{leaderboardData[2]?.title}</h3>
                    <div className="podium-score">{leaderboardData[2]?.voteCount} votes</div>
                    <div className="podium-creator">by {leaderboardData[2]?.uploadedBy}</div>
                  </div>
                </motion.div>
              </div>
            </motion.div>
          )}

          {/* Full Rankings List */}
          <motion.div className="rankings-section" variants={itemVariants}>
            <h2 className="section-title">📊 Full Rankings</h2>
            <div className="rankings-list">
              {leaderboardData.map((item, index) => (
                <motion.div
                  key={item.id}
                  className={`ranking-item ${getRankClass(index + 1)}`}
                  variants={rankVariants}
                  whileHover="hover"
                >
                  <div className="ranking-position">
                    <span className="rank-number">#{index + 1}</span>
                    {getRankIcon(index + 1)}
                  </div>

                  <div className="ranking-image">
                    <img 
                      src={item.imageUrl} 
                      alt={item.title}
                      onError={(e) => {
                        e.target.src = '/placeholder-meme.png';
                      }}
                    />
                  </div>

                  <div className="ranking-info">
                    <h3 className="ranking-title">{item.title}</h3>
                    <div className="ranking-meta">
                      <span className="creator">by {item.uploadedBy || 'Anonymous'}</span>
                      <span className="date">
                        {new Date(item.uploadDate).toLocaleDateString()}
                      </span>
                    </div>
                  </div>

                  <div className="ranking-score">
                    <div className="score-value">{item.voteCount || 0}</div>
                    <div className="score-label">votes</div>
                  </div>

                  <div className="ranking-badge">
                    {index + 1 <= 3 && <Star size={16} className="badge-icon" />}
                  </div>
                </motion.div>
              ))}
            </div>
          </motion.div>
        </motion.div>
      )}
    </div>
  );
};

export default Leaderboard;
