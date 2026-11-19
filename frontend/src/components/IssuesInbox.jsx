import React, { useState, useEffect } from 'react';
import { api } from '../services/api';

export default function IssuesInbox({ activePersona }) {
  const [issues, setIssues] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [lastRefreshed, setLastRefreshed] = useState(null);

  const loadIssues = async () => {
    try {
      const list = await api.fetchIssues();
      setIssues(Array.isArray(list) ? list : []);
      setLastRefreshed(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    } catch (e) {
      console.error('Failed to load issues:', e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleManualRefresh = () => {
    setRefreshing(true);
    loadIssues();
  };

  useEffect(() => {
    loadIssues();
    const timer = setInterval(loadIssues, 3000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div style={{ padding: '20px', height: '100%', overflowY: 'auto', background: 'var(--bg-body)', color: 'var(--text-primary)' }}>
      {/* Header Banner */}
      <div style={{
        background: 'var(--bg-card)',
        padding: '20px',
        borderRadius: '12px',
        border: '1px solid var(--border-subtle)',
        marginBottom: '20px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '12px',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
      }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '20px', color: '#f87171', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span>🚨</span> Master Admin - Reported User Issues Inbox
          </h2>
          <p style={{ margin: '6px 0 0 0', fontSize: '13px', color: 'var(--text-secondary)' }}>
            Real-time feed of issues reported by family members via WhatsApp (Option 9 or /issue) or Web Simulator
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span style={{
            fontSize: '13px',
            background: issues.length > 0 ? 'var(--danger-bg)' : '#00a88422',
            color: issues.length > 0 ? 'var(--danger-text)' : 'var(--wa-teal)',
            padding: '6px 14px',
            borderRadius: '20px',
            border: `1px solid ${issues.length > 0 ? 'var(--danger-border)' : '#00a88444'}`,
            fontWeight: 'bold'
          }}>
            {issues.length} {issues.length === 1 ? 'Issue' : 'Issues'} Logged
          </span>

          <button
            onClick={handleManualRefresh}
            className={`common-refresh-btn ${refreshing ? 'refreshing' : ''}`}
            title="Click to immediately refresh reported issues list"
          >
            <span className={refreshing ? 'spin-icon' : ''}>🔄</span>
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {lastRefreshed && (
        <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '12px', textAlign: 'right' }}>
          <span>Live 3s polling active • Last refreshed: {lastRefreshed}</span>
        </div>
      )}

      {/* Issues List */}
      {loading ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-secondary)' }}>
          ⏳ Loading reported issues...
        </div>
      ) : issues.length === 0 ? (
        <div style={{
          padding: '60px 20px',
          textAlign: 'center',
          background: 'var(--bg-card)',
          borderRadius: '12px',
          border: '1px dashed var(--border-subtle)',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
        }}>
          <div style={{ fontSize: '48px', marginBottom: '12px' }}>🎉</div>
          <h3 style={{ margin: 0, fontSize: '18px', color: 'var(--wa-teal)' }}>No Reported Issues!</h3>
          <p style={{ margin: '8px 0 0 0', fontSize: '13px', color: 'var(--text-secondary)' }}>
            All family members are able to access their documents smoothly. When any user reports an issue via WhatsApp (Option 9 or /issue), it will appear here in real time.
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {issues.map((item, index) => {
            const cleanText = (item.details || '').replace(/^User reported issue:\s*/i, '');
            const timeStr = item.timestamp ? new Date(item.timestamp).toLocaleString() : 'Recent';
            const waLink = `https://wa.me/${item.senderPhone}?text=${encodeURIComponent(`Hi ${item.senderName}, regarding your document vault issue: "${cleanText}"...`)}`;

            return (
              <div
                key={item.id || index}
                style={{
                  background: 'var(--bg-card)',
                  borderRadius: '12px',
                  border: '1px solid var(--border-subtle)',
                  padding: '18px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '12px',
                  transition: 'border-color 0.2s',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.06)'
                }}
              >
                {/* Header: Member info & timestamp */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div style={{
                      width: '36px',
                      height: '36px',
                      borderRadius: '50%',
                      background: 'var(--danger-bg)',
                      color: 'var(--danger-text)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 'bold',
                      fontSize: '15px'
                    }}>
                      🚨
                    </div>
                    <div>
                      <div style={{ fontWeight: 'bold', fontSize: '15px', color: 'var(--text-primary)' }}>
                        {item.senderName} <span style={{ color: 'var(--text-link)', fontWeight: 'normal', fontSize: '13px' }}>(+{item.senderPhone})</span>
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                        Logged at: {timeStr}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{
                      fontSize: '11px',
                      background: 'var(--danger-bg)',
                      color: 'var(--danger-text)',
                      padding: '3px 10px',
                      borderRadius: '12px',
                      border: '1px solid var(--danger-border)',
                      fontWeight: 600
                    }}>
                      TICKET #{issues.length - index} • OPEN
                    </span>
                  </div>
                </div>

                {/* Issue Description Box */}
                <div style={{
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  padding: '14px 16px',
                  color: 'var(--text-primary)',
                  fontSize: '14px',
                  lineHeight: '1.5'
                }}>
                  <div style={{ fontSize: '11px', color: '#f87171', fontWeight: 'bold', marginBottom: '4px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    Reported Problem Description:
                  </div>
                  "{cleanText}"
                </div>

                {/* Action Toolbar */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px', paddingTop: '6px' }}>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                    Alerts dispatched to: <strong style={{ color: 'var(--wa-teal)' }}>hrudaypreetham10@gmail.com</strong> & Server Console
                  </div>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <a
                      href={waLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        background: 'var(--wa-teal)',
                        color: '#ffffff',
                        padding: '6px 14px',
                        borderRadius: '6px',
                        fontSize: '12px',
                        fontWeight: 'bold',
                        textDecoration: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        boxShadow: '0 2px 4px rgba(0, 128, 105, 0.2)'
                      }}
                    >
                      💬 Reply to {item.senderName?.split(' ')[0]} on WhatsApp
                    </a>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
