import React from 'react';
import ReactDOM from 'react-dom/client';
import './ui/theme/tokens.css';
import './ui/theme/global.css';
import { App } from './ui/App';

const rootEl = document.getElementById('root');
if (rootEl) {
  ReactDOM.createRoot(rootEl).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}
