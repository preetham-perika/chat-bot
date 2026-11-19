import React, { useState, useEffect } from 'react';
import { api } from '../services/api';

export default function Navbar({ activeTab, onTabChange }) {
  const [isHealthy, setIsHealthy] = useState(true);
  const [issueCount, setIssueCount] = useState(0);
  const [theme, setTheme] = useState(localStorage.getItem('vault_theme') || 'light');

  useEffect(() => {
    // Apply theme
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    const nextTheme = theme === 'light' ? 'dark' : 'light';
    setTheme(nextTheme);
    document.documentElement.setAttribute('data-theme', nextTheme);
    localStorage.setItem('vault_theme', nextTheme);
  };

  useEffect(() => {
    api.checkHealth().then(setIsHealthy);
    const checkIssues = () => {
      api.fetchIssues().then((list) => {
        if (Array.isArray(list)) setIssueCount(list.length);
      }).catch(() => {});
    };

    checkIssues();
    const interval = setInterval(() => {
      api.checkHealth().then(setIsHealthy);
      checkIssues();
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="top-navbar">
      <div className="brand">
        <div className="brand-icon">📁</div>
        <div className="brand-text">
          <h1>Family WhatsApp Vault</h1>
          <span>React.js Dashboard • Strict 'hii' Session • AES-256 Encrypted</span>
        </div>
      </div>

      <div className="nav-links">
        <button
          className={`nav-pill ${activeTab === 'chat' ? 'active' : ''}`}
          onClick={() => onTabChange('chat')}
        >
          <span>💬 WhatsApp Chat</span>
        </button>

        <button
          className={`nav-pill ${activeTab === 'vault' ? 'active' : ''}`}
          onClick={() => onTabChange('vault')}
        >
          <span>📁 User Documents Vault</span>
        </button>

        <button
          className={`nav-pill ${activeTab === 'whitelist' ? 'active' : ''}`}
          onClick={() => onTabChange('whitelist')}
        >
          <span>👥 Users & Services</span>
        </button>

        <button
          className={`nav-pill ${activeTab === 'issues' ? 'active' : ''}`}
          onClick={() => onTabChange('issues')}
          style={{
            background: issueCount > 0 ? '#ef444422' : undefined,
            borderColor: issueCount > 0 ? '#ef4444' : undefined,
            color: issueCount > 0 ? '#f87171' : undefined
          }}
        >
          <span>🚨 Issues Inbox {issueCount > 0 && `(${issueCount})`}</span>
        </button>

        <button
          className={`nav-pill ${activeTab === 'audit' ? 'active' : ''}`}
          onClick={() => onTabChange('audit')}
        >
          <span>📊 Audit Trail</span>
        </button>

        <button
          className="nav-pill"
          onClick={toggleTheme}
          title="Toggle Light / Dark Color Theme"
          style={{ cursor: 'pointer', fontWeight: 600 }}
        >
          <span>{theme === 'light' ? '🌙 Dark Mode' : '☀️ Light Mode'}</span>
        </button>

        <a
          href="https://wa.me/918790208445"
          target="_blank"
          rel="noopener noreferrer"
          className="nav-pill call-admin"
          title="Direct WhatsApp Call or Message to Master Admin"
        >
          <span>📞 Contact Admin</span>
        </a>

        <a
          href="/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          className="nav-pill"
          title="OpenAPI / Swagger UI Documentation"
        >
          <span>⚡ Swagger UI</span>
        </a>

        <span className="nav-pill" style={{ cursor: 'default' }}>
          <span style={{ color: isHealthy ? '#10b981' : '#ef4444' }}>●</span>
          <span>{isHealthy ? 'Backend UP (:8790)' : 'Backend DOWN'}</span>
        </span>
      </div>
    </header>
  );
}
