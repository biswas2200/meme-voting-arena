import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../../contexts/AuthContext';
import { useNotification } from '../../contexts/NotificationContext';
import { 
  Menu, 
  X, 
  User, 
  LogOut, 
  Upload, 
  Trophy, 
  Zap, 
  Image,
  Home 
} from 'lucide-react';
import './Navbar.css';

const Navbar = () => {
  const { user, logout } = useAuth();
  const { success, error } = useNotification();
  const location = useLocation();
  const navigate = useNavigate();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleLogout = async () => {
    try {
      logout();
      success('Logged out successfully', 'See you later!');
      navigate('/');
      setIsMenuOpen(false);
    } catch (err) {
      error('Logout failed', 'Something went wrong');
    }
  };

  const navItems = [
    { path: '/', label: 'Home', icon: Home },
    { path: '/gallery', label: 'Gallery', icon: Image },
    { path: '/battle', label: 'Battle', icon: Zap, requireAuth: true },
    { path: '/leaderboard', label: 'Leaderboard', icon: Trophy },
  ];

  const isActive = (path) => location.pathname === path;

  const menuVariants = {
    closed: {
      opacity: 0,
      x: '100%',
      transition: {
        duration: 0.3,
        ease: 'easeInOut'
      }
    },
    open: {
      opacity: 1,
      x: 0,
      transition: {
        duration: 0.3,
        ease: 'easeInOut'
      }
    }
  };

  const itemVariants = {
    closed: { opacity: 0, x: 20 },
    open: { opacity: 1, x: 0 }
  };

  return (
    <>
      <motion.nav 
        className={`navbar ${scrolled ? 'scrolled' : ''}`}
        initial={{ y: -100 }}
        animate={{ y: 0 }}
        transition={{ duration: 0.5, ease: 'easeOut' }}
      >
        <div className="navbar-container">
          {/* Logo */}
          <Link to="/" className="navbar-logo">
            <motion.div 
              className="logo-container"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              <span className="logo-text">MEME</span>
              <span className="logo-accent">ARENA</span>
            </motion.div>
          </Link>

          {/* Desktop Navigation */}
          <div className="navbar-menu desktop-menu">
            {navItems.map((item) => {
              const Icon = item.icon;
              if (item.requireAuth && !user) return null;
              
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`nav-link ${isActive(item.path) ? 'active' : ''}`}
                >
                  <motion.div 
                    className="nav-link-content"
                    whileHover={{ y: -2 }}
                    whileTap={{ y: 0 }}
                  >
                    <Icon size={18} />
                    <span>{item.label}</span>
                  </motion.div>
                </Link>
              );
            })}
          </div>

          {/* User Actions */}
          <div className="navbar-actions">
            {user ? (
              <div className="user-menu">
                <div className="desktop-actions">
                  <Link to="/upload" className="nav-button upload-btn">
                    <Upload size={18} />
                    <span>Upload</span>
                  </Link>
                  <Link to="/profile" className="nav-button profile-btn">
                    <User size={18} />
                    <span>{user.username}</span>
                  </Link>
                  <motion.button
                    className="nav-button logout-btn"
                    onClick={handleLogout}
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                  >
                    <LogOut size={18} />
                  </motion.button>
                </div>
              </div>
            ) : (
              <div className="auth-buttons desktop-actions">
                <Link to="/login" className="nav-button login-btn">
                  Login
                </Link>
                <Link to="/register" className="nav-button register-btn">
                  Register
                </Link>
              </div>
            )}

            {/* Mobile Menu Toggle */}
            <motion.button
              className="mobile-menu-toggle"
              onClick={() => setIsMenuOpen(true)}
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
            >
              <Menu size={24} />
            </motion.button>
          </div>
        </div>
      </motion.nav>

      {/* Mobile Menu Overlay */}
      <AnimatePresence>
        {isMenuOpen && (
          <>
            <motion.div
              className="mobile-menu-overlay"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsMenuOpen(false)}
            />
            <motion.div
              className="mobile-menu"
              variants={menuVariants}
              initial="closed"
              animate="open"
              exit="closed"
            >
              <div className="mobile-menu-header">
                <span className="mobile-menu-title">Menu</span>
                <motion.button
                  className="mobile-menu-close"
                  onClick={() => setIsMenuOpen(false)}
                  whileHover={{ scale: 1.1 }}
                  whileTap={{ scale: 0.9 }}
                >
                  <X size={24} />
                </motion.button>
              </div>

              <div className="mobile-menu-content">
                {/* Navigation Items */}
                <div className="mobile-nav-items">
                  {navItems.map((item, index) => {
                    const Icon = item.icon;
                    if (item.requireAuth && !user) return null;
                    
                    return (
                      <motion.div
                        key={item.path}
                        variants={itemVariants}
                        initial="closed"
                        animate="open"
                        transition={{ delay: index * 0.1 }}
                      >
                        <Link
                          to={item.path}
                          className={`mobile-nav-link ${isActive(item.path) ? 'active' : ''}`}
                          onClick={() => setIsMenuOpen(false)}
                        >
                          <Icon size={20} />
                          <span>{item.label}</span>
                        </Link>
                      </motion.div>
                    );
                  })}
                </div>

                {/* User Actions */}
                <div className="mobile-user-actions">
                  {user ? (
                    <>
                      <motion.div
                        variants={itemVariants}
                        initial="closed"
                        animate="open"
                        transition={{ delay: 0.4 }}
                      >
                        <Link
                          to="/upload"
                          className="mobile-action-btn primary"
                          onClick={() => setIsMenuOpen(false)}
                        >
                          <Upload size={20} />
                          Upload Meme
                        </Link>
                      </motion.div>
                      <motion.div
                        variants={itemVariants}
                        initial="closed"
                        animate="open"
                        transition={{ delay: 0.5 }}
                      >
                        <Link
                          to="/profile"
                          className="mobile-action-btn secondary"
                          onClick={() => setIsMenuOpen(false)}
                        >
                          <User size={20} />
                          {user.username}
                        </Link>
                      </motion.div>
                      <motion.div
                        variants={itemVariants}
                        initial="closed"
                        animate="open"
                        transition={{ delay: 0.6 }}
                      >
                        <button
                          className="mobile-action-btn danger"
                          onClick={handleLogout}
                        >
                          <LogOut size={20} />
                          Logout
                        </button>
                      </motion.div>
                    </>
                  ) : (
                    <>
                      <motion.div
                        variants={itemVariants}
                        initial="closed"
                        animate="open"
                        transition={{ delay: 0.4 }}
                      >
                        <Link
                          to="/login"
                          className="mobile-action-btn primary"
                          onClick={() => setIsMenuOpen(false)}
                        >
                          Login
                        </Link>
                      </motion.div>
                      <motion.div
                        variants={itemVariants}
                        initial="closed"
                        animate="open"
                        transition={{ delay: 0.5 }}
                      >
                        <Link
                          to="/register"
                          className="mobile-action-btn secondary"
                          onClick={() => setIsMenuOpen(false)}
                        >
                          Register
                        </Link>
                      </motion.div>
                    </>
                  )}
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
};

export default Navbar;
