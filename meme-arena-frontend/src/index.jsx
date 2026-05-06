import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

// Hide the tron loader when React app is ready
document.body.classList.add('app-loaded');

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
