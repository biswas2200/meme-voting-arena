import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../contexts/AuthContext';
import { Zap, Trophy, Users, Image, ArrowRight, Play, Sparkles } from 'lucide-react';
import './HomePage.css';

const HomePage = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState({
    totalMemes: 0,
    totalUsers: 0,
    totalVotes: 0,
    activeBattles: 0
  });

  useEffect(() => {
    // Simulate loading stats
    const loadStats = async () => {
      // In a real app, this would be an API call
      setTimeout(() => {
        setStats({
          totalMemes: 1247,
          totalUsers: 892,
          totalVotes: 15634,
          activeBattles: 23
        });
      }, 1000);
    };

    loadStats();
  }, []);

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.2
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.6,
        ease: 'easeOut'
      }
    }
  };

  const statVariants = {
    hidden: { opacity: 0, scale: 0.8 },
    visible: {
      opacity: 1,
      scale: 1,
      transition: {
        duration: 0.5,
        ease: 'easeOut'
      }
    }
  };

  const features = [
    {
      icon: Image,
      title: 'Meme Gallery',
      description: 'Browse through thousands of hilarious memes uploaded by our community',
      link: '/gallery',
      color: 'var(--primary-blue)'
    },
    {
      icon: Zap,
      title: 'Battle Arena',
      description: 'Pit your memes against others in epic head-to-head battles',
      link: '/battle',
      color: 'var(--accent-blue)',
      requireAuth: true
    },
    {
      icon: Trophy,
      title: 'Leaderboard',
      description: 'See who reigns supreme in the meme kingdom',
      link: '/leaderboard',
      color: 'var(--secondary-blue)'
    }
  ];

  return (
    <div className="homepage">
      <div className="homepage-container">
        {/* Hero Section */}
        <motion.section 
          className="hero-section"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          <motion.div className="hero-content" variants={itemVariants}>
            <motion.div className="hero-badge" variants={itemVariants}>
              <Sparkles size={16} />
              <span>Ultimate Meme Battle Platform</span>
            </motion.div>
            
            <motion.h1 className="hero-title" variants={itemVariants}>
              Welcome to the
              <span className="hero-title-accent"> Meme Arena</span>
            </motion.h1>
            
            <motion.p className="hero-description" variants={itemVariants}>
              Enter the ultimate battlefield where memes clash, votes decide destiny, 
              and only the funniest survive. Upload your best content, challenge rivals, 
              and climb the leaderboard in this futuristic meme combat zone.
            </motion.p>
            
            <motion.div className="hero-actions" variants={itemVariants}>
              {user ? (
                <>
                  <Link to="/battle" className="btn btn-primary hero-btn">
                    <Play size={18} />
                    Enter Battle Arena
                    <ArrowRight size={18} />
                  </Link>
                  <Link to="/upload" className="btn btn-secondary hero-btn">
                    Upload Memes
                  </Link>
                </>
              ) : (
                <>
                  <Link to="/register" className="btn btn-primary hero-btn">
                    <Zap size={18} />
                    Join the Arena
                    <ArrowRight size={18} />
                  </Link>
                  <Link to="/gallery" className="btn btn-secondary hero-btn">
                    Explore Gallery
                  </Link>
                </>
              )}
            </motion.div>
          </motion.div>
          
          <motion.div className="hero-visual" variants={itemVariants}>
            <div className="hero-orb">
              <div className="orb-core"></div>
              <div className="orb-ring ring-1"></div>
              <div className="orb-ring ring-2"></div>
              <div className="orb-ring ring-3"></div>
            </div>
          </motion.div>
        </motion.section>

        {/* Stats Section */}
        <motion.section 
          className="stats-section"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.3 }}
        >
          <motion.h2 className="section-title" variants={itemVariants}>
            Arena Statistics
          </motion.h2>
          
          <div className="stats-grid">
            <motion.div className="stat-card" variants={statVariants}>
              <div className="stat-icon">
                <Image size={24} />
              </div>
              <div className="stat-number">{stats.totalMemes.toLocaleString()}</div>
              <div className="stat-label">Total Memes</div>
            </motion.div>
            
            <motion.div className="stat-card" variants={statVariants}>
              <div className="stat-icon">
                <Users size={24} />
              </div>
              <div className="stat-number">{stats.totalUsers.toLocaleString()}</div>
              <div className="stat-label">Active Warriors</div>
            </motion.div>
            
            <motion.div className="stat-card" variants={statVariants}>
              <div className="stat-icon">
                <Trophy size={24} />
              </div>
              <div className="stat-number">{stats.totalVotes.toLocaleString()}</div>
              <div className="stat-label">Votes Cast</div>
            </motion.div>
            
            <motion.div className="stat-card" variants={statVariants}>
              <div className="stat-icon">
                <Zap size={24} />
              </div>
              <div className="stat-number">{stats.activeBattles}</div>
              <div className="stat-label">Live Battles</div>
            </motion.div>
          </div>
        </motion.section>

        {/* Features Section */}
        <motion.section 
          className="features-section"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.3 }}
        >
          <motion.h2 className="section-title" variants={itemVariants}>
            Choose Your Path
          </motion.h2>
          
          <div className="features-grid">
            {features.map((feature, index) => {
              const Icon = feature.icon;
              const isAccessible = !feature.requireAuth || user;
              
              return (
                <motion.div
                  key={feature.title}
                  className={`feature-card ${!isAccessible ? 'locked' : ''}`}
                  variants={itemVariants}
                  whileHover={isAccessible ? { y: -8, scale: 1.02 } : {}}
                  transition={{ duration: 0.3 }}
                >
                  {isAccessible ? (
                    <Link to={feature.link} className="feature-link">
                      <div className="feature-icon" style={{ color: feature.color }}>
                        <Icon size={32} />
                      </div>
                      <h3 className="feature-title">{feature.title}</h3>
                      <p className="feature-description">{feature.description}</p>
                      <div className="feature-arrow">
                        <ArrowRight size={20} />
                      </div>
                    </Link>
                  ) : (
                    <div className="feature-content">
                      <div className="feature-icon locked-icon">
                        <Icon size={32} />
                      </div>
                      <h3 className="feature-title">{feature.title}</h3>
                      <p className="feature-description">{feature.description}</p>
                      <div className="feature-lock">
                        <span>Login Required</span>
                      </div>
                    </div>
                  )}
                </motion.div>
              );
            })}
          </div>
        </motion.section>

        {/* CTA Section */}
        {!user && (
          <motion.section 
            className="cta-section"
            variants={containerVariants}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, amount: 0.3 }}
          >
            <motion.div className="cta-content" variants={itemVariants}>
              <h2 className="cta-title">Ready to Dominate?</h2>
              <p className="cta-description">
                Join thousands of meme warriors in the ultimate comedy battleground. 
                Create your account and start your journey to meme supremacy today.
              </p>
              <div className="cta-actions">
                <Link to="/register" className="btn btn-primary cta-btn">
                  <Zap size={18} />
                  Start Your Journey
                </Link>
                <Link to="/login" className="btn btn-secondary cta-btn">
                  Already a Warrior?
                </Link>
              </div>
            </motion.div>
          </motion.section>
        )}
      </div>
    </div>
  );
};

export default HomePage;
