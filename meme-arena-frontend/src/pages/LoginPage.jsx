import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../contexts/AuthContext';
import { useNotification } from '../contexts/NotificationContext';
import { User, Lock, LogIn, Zap, Eye, EyeOff } from 'lucide-react';
import './AuthPages.css';

const LoginPage = () => {
  const [formData, setFormData] = useState({ username: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const { success, error } = useNotification();
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const result = await login(formData);
      if (result.success) {
        success(`Welcome back, ${result.user.username}!`, 'Login Successful');
        navigate('/');
      } else {
        error(result.message, 'Login Failed');
      }
    } catch (err) {
      error('An unexpected error occurred', 'Login Failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <motion.div
          className="auth-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
        >
          <div className="auth-header">
            <div className="auth-icon"><LogIn size={32} /></div>
            <h1 className="auth-title">Enter the Arena</h1>
            <p className="auth-subtitle">Welcome back, warrior. Ready for battle?</p>
          </div>

          <form onSubmit={handleSubmit} className="auth-form">
            {/* Username */}
            <div className="form-group">
              <label htmlFor="username" className="form-label">
                <User size={16} /> Username
              </label>
              <input
                type="text"
                id="username"
                name="username"
                value={formData.username}
                onChange={handleChange}
                className="form-input"
                placeholder="Enter your username"
                required
                disabled={loading}
                autoComplete="username"
              />
            </div>

            {/* Password with eye toggle */}
            <div className="form-group">
              <label htmlFor="password" className="form-label">
                <Lock size={16} /> Password
              </label>
              <div className="password-input-wrap">
                <input
                  type={showPassword ? 'text' : 'password'}
                  id="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  className="form-input"
                  placeholder="Enter your password"
                  required
                  disabled={loading}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="password-eye-btn"
                  onClick={() => setShowPassword(v => !v)}
                  tabIndex={-1}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <motion.button
              type="submit"
              className="btn btn-primary auth-btn"
              disabled={loading}
              whileHover={{ scale: loading ? 1 : 1.02 }}
              whileTap={{ scale: loading ? 1 : 0.98 }}
            >
              {loading ? (
                <><div className="spinner"></div> Authenticating...</>
              ) : (
                <><Zap size={18} /> Enter Arena</>
              )}
            </motion.button>
          </form>

          <div className="auth-footer">
            <p>
              New to the arena?{' '}
              <Link to="/register" className="auth-link">Create Account</Link>
            </p>
          </div>
        </motion.div>

        <div className="auth-visual">
          <div className="visual-orb"><div className="orb-pulse"></div></div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
