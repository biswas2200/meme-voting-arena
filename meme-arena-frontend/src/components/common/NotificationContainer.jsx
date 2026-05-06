import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNotification } from '../../contexts/NotificationContext';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';
import './NotificationContainer.css';

const NotificationContainer = () => {
  const { notifications, removeNotification } = useNotification();

  const getIcon = (type) => {
    switch (type) {
      case 'success':
        return <CheckCircle size={20} />;
      case 'error':
        return <XCircle size={20} />;
      case 'warning':
        return <AlertTriangle size={20} />;
      case 'info':
      default:
        return <Info size={20} />;
    }
  };

  const containerVariants = {
    initial: {},
    animate: {
      transition: {
        staggerChildren: 0.1
      }
    }
  };

  const notificationVariants = {
    initial: {
      opacity: 0,
      y: -50,
      scale: 0.8
    },
    animate: {
      opacity: 1,
      y: 0,
      scale: 1,
      transition: {
        type: 'spring',
        stiffness: 300,
        damping: 25
      }
    },
    exit: {
      opacity: 0,
      x: 300,
      scale: 0.8,
      transition: {
        duration: 0.3,
        ease: 'easeInOut'
      }
    }
  };

  return (
    <div className="notification-container">
      <AnimatePresence>
        <motion.div
          className="notification-list"
          variants={containerVariants}
          initial="initial"
          animate="animate"
        >
          {notifications.map((notification) => (
            <motion.div
              key={notification.id}
              className={`notification notification-${notification.type}`}
              variants={notificationVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              layout
            >
              <div className="notification-icon">
                {getIcon(notification.type)}
              </div>
              
              <div className="notification-content">
                {notification.title && (
                  <div className="notification-title">
                    {notification.title}
                  </div>
                )}
                <div className="notification-message">
                  {notification.message}
                </div>
              </div>
              
              <motion.button
                className="notification-close"
                onClick={() => removeNotification(notification.id)}
                whileHover={{ scale: 1.1 }}
                whileTap={{ scale: 0.9 }}
              >
                <X size={16} />
              </motion.button>
              
              <div className={`notification-progress notification-progress-${notification.type}`} />
            </motion.div>
          ))}
        </motion.div>
      </AnimatePresence>
    </div>
  );
};

export default NotificationContainer;
