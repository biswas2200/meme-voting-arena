import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../contexts/AuthContext';
import { useNotification } from '../contexts/NotificationContext';
import './MemeGallery.css';

const MemeGallery = () => {
  const [memes, setMemes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterBy, setFilterBy] = useState('all');
  const [sortBy, setSortBy] = useState('newest');
  const [authorFilter, setAuthorFilter] = useState('');
  const [voteFilter, setVoteFilter] = useState({ min: '', max: '' });
  const [dateFilter, setDateFilter] = useState({ from: '', to: '' });
  const [showFilters, setShowFilters] = useState(false);

  const { user } = useAuth();
  const { showNotification } = useNotification();

  // Fetch memes from backend
  useEffect(() => {
    console.log('🚀 DEBUG: MemeGallery useEffect triggered - fetching memes');
    fetchMemes();
  }, []);

  const fetchMemes = async () => {
    try {
      console.log('📡 DEBUG: Starting fetchMemes...');
      setLoading(true);
      
      console.log('📡 DEBUG: Making request to /api/memes');
      const response = await fetch(`${import.meta.env.VITE_API_URL || ''}/api/memes`);
      
      console.log('📡 DEBUG: Response status:', response.status);
      console.log('📡 DEBUG: Response headers:', Object.fromEntries(response.headers.entries()));
      
      if (response.ok) {
        const data = await response.json();
        console.log('📡 DEBUG: Raw response data:', data);
        
        // Handle both paginated and array responses
        const memesArray = data.content || data || [];
        console.log('📡 DEBUG: Processed memes array:', memesArray);
        console.log('📡 DEBUG: Number of memes:', memesArray.length);
        
        // Log each meme for debugging
        memesArray.forEach((meme, index) => {
          console.log(`📡 DEBUG: Meme ${index + 1}:`, {
            id: meme.id,
            title: meme.title,
            imageUrl: meme.imageUrl,
            uploadedBy: meme.uploadedBy,
            voteCount: meme.voteCount
          });
        });
        
        setMemes(memesArray);
        console.log('✅ DEBUG: Memes set successfully');
      } else {
        console.error('❌ DEBUG: Failed to fetch memes - Response not OK');
        console.error('❌ DEBUG: Response text:', await response.text());
        setMemes([]);
      }
    } catch (error) {
      console.error('❌ DEBUG: Error fetching memes:', error);
      console.error('❌ DEBUG: Error details:', {
        name: error.name,
        message: error.message,
        stack: error.stack
      });
      showNotification('Failed to load memes. Please try again.', 'error');
      setMemes([]);
    } finally {
      setLoading(false);
      console.log('🏁 DEBUG: fetchMemes completed');
    }
  };

  // Handle voting - requires authentication
  const handleVote = async (memeId, voteType) => {
    if (!user) {
      showNotification('Please log in to vote.', 'error');
      return;
    }
    try {
      const token = localStorage.getItem('token');
      const response = await fetch(`${import.meta.env.VITE_API_URL || ''}/api/memes/${memeId}/vote`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ voteType })
      });

      if (response.ok) {
        const updatedMeme = await response.json();
        setMemes(prevMemes =>
          prevMemes.map(meme =>
            meme.id === memeId
              ? { ...meme, voteCount: updatedMeme.voteCount || updatedMeme.votes || 0 }
              : meme
          )
        );
        showNotification(`${voteType === 'UPVOTE' ? 'Upvoted' : 'Downvoted'} successfully!`, 'success');
      } else {
        showNotification('Failed to vote. Please try again.', 'error');
      }
    } catch (error) {
      console.error('Error voting:', error);
      showNotification('Network error. Please try again.', 'error');
    }
  };

  // Filter and sort memes
  const getFilteredAndSortedMemes = () => {
    if (!Array.isArray(memes) || memes.length === 0) {
      return [];
    }
    
    let filteredMemes = memes.filter(meme => {
      // Search filter
      const matchesSearch = !searchTerm || 
        meme.title?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        meme.description?.toLowerCase().includes(searchTerm.toLowerCase());
      
      // Basic filter (all/my-memes)
      const matchesFilter = filterBy === 'all' || 
        (filterBy === 'my-memes' && user && meme.uploadedBy === user.username);
      
      // Author filter
      const matchesAuthor = !authorFilter || 
        meme.uploadedBy?.toLowerCase().includes(authorFilter.toLowerCase());
      
      // Vote count filter
      const voteCount = meme.voteCount || meme.votes || 0;
      const matchesVoteMin = !voteFilter.min || voteCount >= parseInt(voteFilter.min);
      const matchesVoteMax = !voteFilter.max || voteCount <= parseInt(voteFilter.max);
      
      // Date filter
      const memeDate = new Date(meme.createdAt || meme.uploadDate);
      const matchesDateFrom = !dateFilter.from || memeDate >= new Date(dateFilter.from);
      const matchesDateTo = !dateFilter.to || memeDate <= new Date(dateFilter.to);
      
      return matchesSearch && matchesFilter && matchesAuthor && 
             matchesVoteMin && matchesVoteMax && matchesDateFrom && matchesDateTo;
    });

    // Sort memes
    filteredMemes.sort((a, b) => {
      const aVotes = a.voteCount || a.votes || 0;
      const bVotes = b.voteCount || b.votes || 0;
      const aDate = new Date(a.createdAt || a.uploadDate);
      const bDate = new Date(b.createdAt || b.uploadDate);

      switch (sortBy) {
        case 'newest':
          return bDate - aDate;
        case 'oldest':
          return aDate - bDate;
        case 'most-popular':
          return bVotes - aVotes;
        case 'least-popular':
          return aVotes - bVotes;
        default:
          return bDate - aDate;
      }
    });

    return filteredMemes;
  };

  const clearFilters = () => {
    setSearchTerm('');
    setFilterBy('all');
    setSortBy('newest');
    setAuthorFilter('');
    setVoteFilter({ min: '', max: '' });
    setDateFilter({ from: '', to: '' });
  };

  const filteredMemes = getFilteredAndSortedMemes();
  
  // Debug logging for render state
  console.log('🎨 DEBUG: MemeGallery rendering...');
  console.log('🎨 DEBUG: loading:', loading);
  console.log('🎨 DEBUG: memes.length:', memes.length);
  console.log('🎨 DEBUG: filteredMemes.length:', filteredMemes.length);
  console.log('🎨 DEBUG: memes array:', memes);
  console.log('🎨 DEBUG: filteredMemes array:', filteredMemes);

  if (loading) {
    console.log('🎨 DEBUG: Showing loading spinner');
    return (
      <div className="gallery-container">
        <motion.div 
          className="loading-spinner"
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
        >
          <div className="spinner"></div>
        </motion.div>
        <p>Loading memes...</p>
      </div>
    );
  }

  return (
    <div className="gallery-container">
      <motion.div 
        className="gallery-header"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
      >
        <h1 className="gallery-title">
          <span className="neon-text">MEME GALLERY</span>
        </h1>
        
        {/* Search Bar */}
        <div className="search-section">
          <div className="search-bar">
            <input
              type="text"
              placeholder="Search memes by title or description..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="search-input"
            />
            <div className="search-icon">🔍</div>
          </div>
          
          <button 
            className="filter-toggle"
            onClick={() => setShowFilters(!showFilters)}
          >
            <span>Filters</span>
            <span className={`filter-arrow ${showFilters ? 'open' : ''}`}>▼</span>
          </button>
        </div>

        {/* Advanced Filters */}
        <AnimatePresence>
          {showFilters && (
            <motion.div 
              className="filters-panel"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.3 }}
            >
              <div className="filters-grid">
                <div className="filter-group">
                  <label>Category</label>
                  <select value={filterBy} onChange={(e) => setFilterBy(e.target.value)}>
                    <option value="all">All Memes</option>
                    <option value="my-memes">My Memes</option>
                  </select>
                </div>

                <div className="filter-group">
                  <label>Sort By</label>
                  <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                    <option value="newest">Newest First</option>
                    <option value="oldest">Oldest First</option>
                    <option value="most-popular">Most Popular</option>
                    <option value="least-popular">Least Popular</option>
                  </select>
                </div>

                <div className="filter-group">
                  <label>Author</label>
                  <input
                    type="text"
                    placeholder="Filter by author..."
                    value={authorFilter}
                    onChange={(e) => setAuthorFilter(e.target.value)}
                  />
                </div>

                <div className="filter-group">
                  <label>Vote Range</label>
                  <div className="range-inputs">
                    <input
                      type="number"
                      placeholder="Min"
                      value={voteFilter.min}
                      onChange={(e) => setVoteFilter({...voteFilter, min: e.target.value})}
                    />
                    <span>to</span>
                    <input
                      type="number"
                      placeholder="Max"
                      value={voteFilter.max}
                      onChange={(e) => setVoteFilter({...voteFilter, max: e.target.value})}
                    />
                  </div>
                </div>

                <div className="filter-group">
                  <label>Date Range</label>
                  <div className="date-inputs">
                    <input
                      type="date"
                      value={dateFilter.from}
                      onChange={(e) => setDateFilter({...dateFilter, from: e.target.value})}
                    />
                    <span>to</span>
                    <input
                      type="date"
                      value={dateFilter.to}
                      onChange={(e) => setDateFilter({...dateFilter, to: e.target.value})}
                    />
                  </div>
                </div>
              </div>
              
              <div className="filter-actions">
                <button className="clear-filters-btn" onClick={clearFilters}>
                  Clear All Filters
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Stats */}
        <div className="gallery-stats">
          <div className="stat-item">
            <span className="stat-number">{filteredMemes.length}</span>
            <span className="stat-label">Memes Found</span>
          </div>
          <div className="stat-item">
            <span className="stat-number">
              {filteredMemes.reduce((sum, meme) => sum + (meme.voteCount || meme.votes || 0), 0)}
            </span>
            <span className="stat-label">Total Votes</span>
          </div>
        </div>
      </motion.div>

      {/* Memes Grid */}
      {filteredMemes.length === 0 ? (
        <motion.div 
          className="no-memes"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.5 }}
        >
          <div className="no-memes-icon">🎭</div>
          <h3>No memes found</h3>
          <p>Try adjusting your search or filters</p>
        </motion.div>
      ) : (
        <motion.div 
          className="memes-grid"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6, delay: 0.2 }}
        >
          <AnimatePresence>
            {filteredMemes.map((meme, index) => (
              <motion.div
                key={meme.id}
                className="meme-card"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.4, delay: index * 0.05 }}
                whileHover={{ 
                  scale: 1.03,
                  boxShadow: "0 10px 30px rgba(0, 255, 255, 0.3)"
                }}
              >
                <div className="meme-image-container">
                  <img 
                    src={(() => {
                      const imageUrl = meme.imageUrl || meme.filePath;
                      let finalUrl;
                      
                      if (!imageUrl) {
                        finalUrl = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KICA8cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMzMzIi8+CiAgPHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxOCIgZmlsbD0iIzAwZmZmZiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPk5vIEltYWdlPC90ZXh0Pgo8L3N2Zz4=';
                      } else if (imageUrl.startsWith('http')) {
                        finalUrl = imageUrl;
                      } else {
                        finalUrl = `${import.meta.env.VITE_API_URL || ''}${imageUrl}`;
                      }
                      
                      console.log(`🖼️ DEBUG: Image URL for "${meme.title}" (ID: ${meme.id}):`, {
                        original: imageUrl,
                        final: finalUrl
                      });
                      
                      return finalUrl;
                    })()}
                    alt={meme.title}
                    className="meme-image"
                    onError={(e) => {
                      console.log('❌ DEBUG: Image load error for:', e.target.src, 'Meme:', meme.title);
                      e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KICA8cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMzMzIi8+CiAgPHRleHQgeD0iNTAlIiB5PSIxMDAlIiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTgiIGZpbGw9IiMwMGZmZmYiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5JbWFnZSBOb3QgRm91bmQ8L3RleHQ+CiAgPC9zdmc+';
                    }}
                    onLoad={(e) => {
                      console.log('✅ DEBUG: Image loaded successfully:', e.target.src, 'for meme:', meme.title);
                    }}
                  />
                  <div className="image-overlay">
                    <div className="vote-buttons">
                      <button 
                        className="vote-btn upvote"
                        onClick={() => handleVote(meme.id, 'UPVOTE')}
                        title="Upvote"
                      >
                        ▲
                      </button>
                      <span className="vote-count">
                        {meme.voteCount || meme.votes || 0}
                      </span>
                      <button 
                        className="vote-btn downvote"
                        onClick={() => handleVote(meme.id, 'DOWNVOTE')}
                        title="Downvote"
                      >
                        ▼
                      </button>
                    </div>
                  </div>
                </div>
                
                <div className="meme-content">
                  <h3 className="meme-title">{meme.title}</h3>
                  {meme.description && (
                    <p className="meme-description">{meme.description}</p>
                  )}
                  
                  <div className="meme-meta">
                    <div className="meme-author">
                      <span className="author-label">By:</span>
                      <span className="author-name">{meme.uploadedBy || 'Anonymous'}</span>
                    </div>
                    <div className="meme-date">
                      {new Date(meme.createdAt || meme.uploadDate).toLocaleDateString()}
                    </div>
                  </div>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        </motion.div>
      )}
    </div>
  );
};

export default MemeGallery;
