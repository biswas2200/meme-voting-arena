import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { X, User, Mail, Lock, Eye, EyeOff, Save, Loader } from 'lucide-react';
import { useNotification } from '../../contexts/NotificationContext';
import api from '../../services/api';
import './EditProfileModal.css';

const EditProfileModal = ({ stats, onClose, onSaved }) => {
  const { error: notifyError } = useNotification();

  const [form, setForm] = useState({
    username: stats.username || '',
    email: stats.email || '',
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: ''
  });
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
    if (fieldErrors[name]) setFieldErrors(prev => ({ ...prev, [name]: '' }));
  };

  const validate = () => {
    const errs = {};
    if (!form.username.trim()) errs.username = 'Username is required';
    else if (form.username.length < 3) errs.username = 'Min 3 characters';
    else if (form.username.length > 20) errs.username = 'Max 20 characters';

    if (!form.email.trim()) errs.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = 'Invalid email';

    if (form.newPassword) {
      if (!form.currentPassword) errs.currentPassword = 'Enter current password to change it';
      if (form.newPassword.length < 6) errs.newPassword = 'Min 6 characters';
      if (form.newPassword !== form.confirmNewPassword) errs.confirmNewPassword = 'Passwords do not match';
    }
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setFieldErrors(errs); return; }

    setSaving(true);
    try {
      const payload = {
        username: form.username.trim(),
        email: form.email.trim()
      };
      if (form.newPassword) {
        payload.currentPassword = form.currentPassword;
        payload.newPassword = form.newPassword;
      }

      const res = await api.put('/api/auth/profile', payload);
      onSaved(res.data);
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update profile';
      notifyError(msg, 'Update Failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <motion.div
      className="modal-backdrop"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={e => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        className="edit-profile-modal"
        initial={{ opacity: 0, scale: 0.92, y: 30 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.92, y: 30 }}
        transition={{ duration: 0.3, ease: 'easeOut' }}
      >
        {/* header */}
        <div className="epm-header">
          <h2 className="epm-title"><User size={20} /> Edit Profile</h2>
          <button className="epm-close" onClick={onClose} aria-label="Close"><X size={20} /></button>
        </div>

        <form onSubmit={handleSubmit} className="epm-form" noValidate>
          {/* username */}
          <div className={`epm-field ${fieldErrors.username ? 'has-error' : ''}`}>
            <label htmlFor="ep-username"><User size={14} /> Username</label>
            <input
              id="ep-username"
              name="username"
              type="text"
              value={form.username}
              onChange={handleChange}
              placeholder="Your warrior name"
              autoComplete="username"
            />
            {fieldErrors.username && <span className="epm-error">{fieldErrors.username}</span>}
          </div>

          {/* email */}
          <div className={`epm-field ${fieldErrors.email ? 'has-error' : ''}`}>
            <label htmlFor="ep-email"><Mail size={14} /> Email</label>
            <input
              id="ep-email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              placeholder="your@email.com"
              autoComplete="email"
            />
            {fieldErrors.email && <span className="epm-error">{fieldErrors.email}</span>}
          </div>

          <div className="epm-divider">
            <span>Change Password <em>(optional)</em></span>
          </div>

          {/* current password */}
          <div className={`epm-field ${fieldErrors.currentPassword ? 'has-error' : ''}`}>
            <label htmlFor="ep-current"><Lock size={14} /> Current Password</label>
            <div className="epm-password-wrap">
              <input
                id="ep-current"
                name="currentPassword"
                type={showCurrent ? 'text' : 'password'}
                value={form.currentPassword}
                onChange={handleChange}
                placeholder="Required to change password"
                autoComplete="current-password"
              />
              <button type="button" className="epm-eye" onClick={() => setShowCurrent(v => !v)} tabIndex={-1}>
                {showCurrent ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {fieldErrors.currentPassword && <span className="epm-error">{fieldErrors.currentPassword}</span>}
          </div>

          {/* new password */}
          <div className={`epm-field ${fieldErrors.newPassword ? 'has-error' : ''}`}>
            <label htmlFor="ep-new"><Lock size={14} /> New Password</label>
            <div className="epm-password-wrap">
              <input
                id="ep-new"
                name="newPassword"
                type={showNew ? 'text' : 'password'}
                value={form.newPassword}
                onChange={handleChange}
                placeholder="Leave blank to keep current"
                autoComplete="new-password"
              />
              <button type="button" className="epm-eye" onClick={() => setShowNew(v => !v)} tabIndex={-1}>
                {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {fieldErrors.newPassword && <span className="epm-error">{fieldErrors.newPassword}</span>}
          </div>

          {/* confirm new password */}
          <div className={`epm-field ${fieldErrors.confirmNewPassword ? 'has-error' : ''}`}>
            <label htmlFor="ep-confirm"><Lock size={14} /> Confirm New Password</label>
            <div className="epm-password-wrap">
              <input
                id="ep-confirm"
                name="confirmNewPassword"
                type={showConfirm ? 'text' : 'password'}
                value={form.confirmNewPassword}
                onChange={handleChange}
                placeholder="Repeat new password"
                autoComplete="new-password"
              />
              <button type="button" className="epm-eye" onClick={() => setShowConfirm(v => !v)} tabIndex={-1}>
                {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {fieldErrors.confirmNewPassword && <span className="epm-error">{fieldErrors.confirmNewPassword}</span>}
          </div>

          {/* actions */}
          <div className="epm-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <motion.button
              type="submit"
              className="btn btn-primary"
              disabled={saving}
              whileHover={{ scale: saving ? 1 : 1.03 }}
              whileTap={{ scale: saving ? 1 : 0.97 }}
            >
              {saving ? <><Loader size={16} className="spin" /> Saving…</> : <><Save size={16} /> Save Changes</>}
            </motion.button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
};

export default EditProfileModal;
