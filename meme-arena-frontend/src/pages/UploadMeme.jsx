import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Upload, Image, Plus, Check, AlertCircle, Link, Eye, UploadCloud } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNotification } from '../contexts/NotificationContext';
import '../styles/CommonPages.css';
import '../styles/UploadMeme.css';

const UploadMeme = () => {
  const { user } = useAuth();
  const { showNotification } = useNotification();
  
  const [uploadMode, setUploadMode] = useState('url'); // 'url' or 'file'
  const [formData, setFormData] = useState({
    title: '',
    imageUrl: ''
  });
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [errors, setErrors] = useState({});

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

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) {
      // Validate file type
      if (!file.type.startsWith('image/')) {
        setErrors(prev => ({
          ...prev,
          file: 'Please select a valid image file'
        }));
        return;
      }
      
      // Validate file size (1MB max)
      if (file.size > 1024 * 1024) {
        setErrors(prev => ({
          ...prev,
          file: 'File size must be less than 1MB'
        }));
        return;
      }

      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
      
      // Clear any file errors
      if (errors.file) {
        setErrors(prev => ({
          ...prev,
          file: ''
        }));
      }
    }
  };

  const handleModeSwitch = (mode) => {
    setUploadMode(mode);
    setSelectedFile(null);
    setPreviewUrl('');
    setFormData(prev => ({ ...prev, imageUrl: '' }));
    setErrors({});
    setShowPreview(false);
  };

  const validateForm = () => {
    const newErrors = {};
    
    if (!formData.title.trim()) {
      newErrors.title = 'Title is required';
    } else if (formData.title.length > 255) {
      newErrors.title = 'Title must not exceed 255 characters';
    }
    
    if (uploadMode === 'url') {
      if (!formData.imageUrl.trim()) {
        newErrors.imageUrl = 'Image URL is required';
      } else if (formData.imageUrl.length > 500) {
        newErrors.imageUrl = 'Image URL must not exceed 500 characters';
      } else if (!isValidUrl(formData.imageUrl)) {
        newErrors.imageUrl = 'Please enter a valid URL';
      }
    } else {
      if (!selectedFile) {
        newErrors.file = 'Please select an image file';
      }
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const isValidUrl = (string) => {
    try {
      new URL(string);
      return true;
    } catch (_) {
      return false;
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    setIsLoading(true);
    
    try {
      const token = localStorage.getItem('token');
      let response;

      const apiBase = import.meta.env.VITE_API_URL || '';

      if (uploadMode === 'url') {
        // Submit URL-based meme
        response = await fetch(`${apiBase}/api/memes`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify(formData)
        });
      } else {
        // Submit file-based meme
        const fileFormData = new FormData();
        fileFormData.append('file', selectedFile);
        fileFormData.append('title', formData.title);

        response = await fetch(`${apiBase}/api/memes/upload`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`
          },
          body: fileFormData
        });
      }

      if (response.ok) {
        const result = await response.json();
        showNotification('Meme uploaded successfully! 🎉', 'success');
        setFormData({ title: '', imageUrl: '' });
        setSelectedFile(null);
        setPreviewUrl('');
        setShowPreview(false);
      } else {
        const error = await response.json();
        showNotification(error.message || 'Failed to upload meme', 'error');
      }
    } catch (error) {
      console.error('Upload error:', error);
      showNotification('Network error. Please try again.', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePreview = () => {
    if (uploadMode === 'url' && formData.imageUrl && isValidUrl(formData.imageUrl)) {
      setShowPreview(!showPreview);
    } else if (uploadMode === 'file' && previewUrl) {
      setShowPreview(!showPreview);
    }
  };

  if (!user) {
    return (
      <div className="page-container">
        <motion.div
          className="auth-required"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <AlertCircle size={48} />
          <h2>Authentication Required</h2>
          <p>Please log in to upload memes to the arena.</p>
        </motion.div>
      </div>
    );
  }

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
            <Upload size={40} />
            Upload Meme
          </h1>
          <p className="page-description">
            Share your finest creations with the arena. Upload and prepare for battle!
          </p>
        </motion.div>
      </motion.div>

      <motion.div
        className="upload-form-container"
        variants={itemVariants}
        initial="hidden"
        animate="visible"
      >
        <form onSubmit={handleSubmit} className="upload-form">
          <div className="form-group">
            <label htmlFor="title" className="form-label">
              <Plus size={18} />
              Meme Title
            </label>
            <input
              type="text"
              id="title"
              name="title"
              value={formData.title}
              onChange={handleInputChange}
              placeholder="Enter a catchy title for your meme..."
              className={`form-input ${errors.title ? 'error' : ''}`}
              maxLength="255"
            />
            {errors.title && <span className="error-message">{errors.title}</span>}
            <div className="character-count">
              {formData.title.length}/255
            </div>
          </div>

          {/* Mode Switcher */}
          <div className="form-group">
            <label className="form-label">Choose Upload Method</label>
            <div className="mode-switcher">
              <button
                type="button"
                onClick={() => handleModeSwitch('url')}
                className={`mode-btn ${uploadMode === 'url' ? 'active' : ''}`}
              >
                <Link size={18} />
                Image URL
              </button>
              <button
                type="button"
                onClick={() => handleModeSwitch('file')}
                className={`mode-btn ${uploadMode === 'file' ? 'active' : ''}`}
              >
                <UploadCloud size={18} />
                Upload File
              </button>
            </div>
          </div>

          {/* URL Input */}
          {uploadMode === 'url' && (
            <div className="form-group">
              <label htmlFor="imageUrl" className="form-label">
                <Link size={18} />
                Image URL
              </label>
              <div className="url-input-group">
                <input
                  type="url"
                  id="imageUrl"
                  name="imageUrl"
                  value={formData.imageUrl}
                  onChange={handleInputChange}
                  placeholder="https://example.com/your-meme-image.jpg"
                  className={`form-input ${errors.imageUrl ? 'error' : ''}`}
                  maxLength="500"
                />
                <button
                  type="button"
                  onClick={handlePreview}
                  className="preview-btn"
                  disabled={!formData.imageUrl || !isValidUrl(formData.imageUrl)}
                >
                  <Eye size={18} />
                </button>
              </div>
              {errors.imageUrl && <span className="error-message">{errors.imageUrl}</span>}
              <div className="character-count">
                {formData.imageUrl.length}/500
              </div>
            </div>
          )}

          {/* File Upload */}
          {uploadMode === 'file' && (
            <div className="form-group">
              <label className="form-label">
                <UploadCloud size={18} />
                Select Image File
              </label>
              <div className="file-upload-area">
                <input
                  type="file"
                  id="fileInput"
                  accept="image/*"
                  onChange={handleFileSelect}
                  className="file-input-hidden"
                />
                <label htmlFor="fileInput" className="file-upload-label">
                  <div className="file-upload-content">
                    {selectedFile ? (
                      <>
                        <Check size={32} />
                        <p>{selectedFile.name}</p>
                        <span className="file-size">
                          {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB
                        </span>
                      </>
                    ) : (
                      <>
                        <Upload size={32} />
                        <p>Click to select an image</p>
                        <span className="file-hint">JPG, PNG, GIF up to 1MB</span>
                      </>
                    )}
                  </div>
                </label>
                {selectedFile && (
                  <button
                    type="button"
                    onClick={handlePreview}
                    className="preview-btn file-preview-btn"
                  >
                    <Eye size={18} />
                    Preview
                  </button>
                )}
              </div>
              {errors.file && <span className="error-message">{errors.file}</span>}
            </div>
          )}

          {showPreview && (uploadMode === 'url' ? formData.imageUrl : previewUrl) && (
            <motion.div
              className="image-preview"
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.3 }}
            >
              <h4>Preview:</h4>
              <img
                src={uploadMode === 'url' ? formData.imageUrl : previewUrl}
                alt="Meme preview"
                onError={(e) => {
                  e.target.style.display = 'none';
                  if (uploadMode === 'url') {
                    setErrors(prev => ({
                      ...prev,
                      imageUrl: 'Could not load image from this URL'
                    }));
                  }
                }}
                onLoad={(e) => {
                  e.target.style.display = 'block';
                  if (uploadMode === 'url') {
                    setErrors(prev => ({
                      ...prev,
                      imageUrl: ''
                    }));
                  }
                }}
              />
            </motion.div>
          )}

          <motion.button
            type="submit"
            className="submit-btn"
            disabled={isLoading}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            {isLoading ? (
              <>
                <div className="loading-spinner"></div>
                Uploading...
              </>
            ) : (
              <>
                <Check size={18} />
                Upload to Arena
              </>
            )}
          </motion.button>
        </form>

        <div className="upload-tips">
          <h4>📋 Tips for Great Memes:</h4>
          <ul>
            <li>Use high-quality images for better visibility</li>
            <li>Keep titles short and catchy</li>
            <li>File uploads must be under 1MB in size</li>
            <li>Make sure your image URL is publicly accessible</li>
            <li>Avoid copyrighted content</li>
            <li>Have fun and be creative! 🎨</li>
          </ul>
        </div>
      </motion.div>
    </div>
  );
};

export default UploadMeme;
