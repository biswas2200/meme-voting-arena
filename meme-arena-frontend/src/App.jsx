import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { NotificationProvider } from './contexts/NotificationContext';
import Navbar from './components/common/Navbar';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import MemeGallery from './pages/MemeGallery';
import BattleArena from './pages/BattleArena';
import QuickBattle from './components/battle/QuickBattle';
import TournamentList from './pages/TournamentList';
import TournamentCreate from './pages/TournamentCreate';
import TournamentBracket from './components/battle/TournamentBracket';
import TournamentResults from './pages/TournamentResults';
import Leaderboard from './pages/Leaderboard';
import Profile from './pages/Profile';
import UploadMeme from './pages/UploadMeme';
import NotificationContainer from './components/common/NotificationContainer';
import './styles/App.css';

const pageVariants = {
  initial: { opacity: 0, x: -20 },
  in: { opacity: 1, x: 0 },
  out: { opacity: 0, x: 20 }
};

const pageTransition = {
  type: 'tween',
  ease: 'anticipate',
  duration: 0.4
};

function AppContent() {
  const { user, loading } = useAuth();
  const [currentPath, setCurrentPath] = useState(window.location.pathname);

  useEffect(() => {
    const handleLocationChange = () => {
      setCurrentPath(window.location.pathname);
    };
    
    window.addEventListener('popstate', handleLocationChange);
    return () => window.removeEventListener('popstate', handleLocationChange);
  }, []);

  if (loading) {
    return (
      <div className="loading-container">
        <motion.div 
          className="tron-spinner"
          animate={{ rotate: 360 }}
          transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
        >
          <div className="spinner-ring"></div>
          <div className="spinner-ring ring-2"></div>
        </motion.div>
        <motion.p 
          className="loading-text"
          animate={{ opacity: [0.5, 1, 0.5] }}
          transition={{ duration: 1.5, repeat: Infinity }}
        >
          Initializing Arena...
        </motion.p>
      </div>
    );
  }

  return (
    <div className="app">
      <div className="tron-grid-background"></div>
      <div className="app-content">
        <Navbar />
        <main className="main-content">
          <AnimatePresence mode="wait">
            <Routes key={currentPath}>
              <Route 
                path="/" 
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <HomePage />
                  </motion.div>
                } 
              />
              <Route 
                path="/login" 
                element={
                  user ? <Navigate to="/" replace /> : (
                    <motion.div
                      initial="initial"
                      animate="in"
                      exit="out"
                      variants={pageVariants}
                      transition={pageTransition}
                    >
                      <LoginPage />
                    </motion.div>
                  )
                } 
              />
              <Route 
                path="/register" 
                element={
                  user ? <Navigate to="/" replace /> : (
                    <motion.div
                      initial="initial"
                      animate="in"
                      exit="out"
                      variants={pageVariants}
                      transition={pageTransition}
                    >
                      <RegisterPage />
                    </motion.div>
                  )
                } 
              />
              <Route 
                path="/gallery" 
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <MemeGallery />
                  </motion.div>
                } 
              />
              <Route 
                path="/battle" 
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <BattleArena />
                  </motion.div>
                } 
              />
              <Route
                path="/battle/quick"
                element={
                  user ? (
                    <motion.div
                      initial="initial"
                      animate="in"
                      exit="out"
                      variants={pageVariants}
                      transition={pageTransition}
                    >
                      <QuickBattle />
                    </motion.div>
                  ) : <Navigate to="/login" replace />
                }
              />
              <Route
                path="/battle/tournaments"
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <TournamentList />
                  </motion.div>
                }
              />
              <Route
                path="/battle/tournaments/new"
                element={
                  user ? (
                    <motion.div
                      initial="initial"
                      animate="in"
                      exit="out"
                      variants={pageVariants}
                      transition={pageTransition}
                    >
                      <TournamentCreate />
                    </motion.div>
                  ) : <Navigate to="/login" replace />
                }
              />
              <Route
                path="/battle/tournaments/:id"
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <TournamentBracket />
                  </motion.div>
                }
              />
              <Route
                path="/battle/tournaments/:id/results"
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <TournamentResults />
                  </motion.div>
                }
              />
              <Route 
                path="/leaderboard" 
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                  >
                    <Leaderboard />
                  </motion.div>
                } 
              />
              <Route 
                path="/profile" 
                element={
                  user ? (
                    <motion.div
                      initial="initial"
                      animate="in"
                      exit="out"
                      variants={pageVariants}
                      transition={pageTransition}
                    >
                      <Profile />
                    </motion.div>
                  ) : <Navigate to="/login" replace />
                } 
              />
              <Route 
                path="/upload" 
                element={
                  user ? (
                    <motion.div
                      initial="initial"
                      animate="in"
                      exit="out"
                      variants={pageVariants}
                      transition={pageTransition}
                    >
                      <UploadMeme />
                    </motion.div>
                  ) : <Navigate to="/login" replace />
                } 
              />
              <Route 
                path="*" 
                element={
                  <motion.div
                    initial="initial"
                    animate="in"
                    exit="out"
                    variants={pageVariants}
                    transition={pageTransition}
                    className="not-found"
                  >
                    <div className="not-found-content">
                      <h1 className="neon-text">404</h1>
                      <p>Arena location not found</p>
                      <motion.button 
                        className="btn btn-primary"
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        onClick={() => window.location.href = '/'}
                      >
                        Return to Arena
                      </motion.button>
                    </div>
                  </motion.div>
                } 
              />
            </Routes>
          </AnimatePresence>
        </main>
      </div>
      <NotificationContainer />
    </div>
  );
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <NotificationProvider>
          <AppContent />
        </NotificationProvider>
      </AuthProvider>
    </Router>
  );
}

export default App;
