import React, { useState, useEffect } from 'react';
import { api } from '../services/api';

export default function AuditTrail() {
  const [logs, setLogs] = useState([]);
  const [filter, setFilter] = useState('ALL');
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadLogs();
    const interval = setInterval(loadLogs, 2500); // 2.5s live polling
    return () => clearInterval(interval);
  }, []);

  const loadLogs = async () => {
    try {
      const data = await api.fetchAuditLogs();
      setLogs(data);
    } catch (e) {
      console.error('Failed to load audit logs:', e);
    } finally {
      setRefreshing(false);
    }
  };

  const handleManualRefresh = () => {
    setRefreshing(true);
    loadLogs();
  };

  const filteredLogs = logs.filter((log) => {
    if (filter === 'ALL') return true;
    if (filter === 'DENIED') return log.status === 'DENIED' || log.status === 'RESTRICTED';
    if (filter === 'SUCCESS') return log.status === 'SUCCESS';
    if (filter === 'ALERT') return log.eventType === 'SECURITY_ALERT' || log.eventType === 'UNAUTHORIZED_ACCESS';
    return true;
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: '16px', padding: '16px', background: 'var(--bg-body)', color: 'var(--text-primary)', overflowY: 'auto' }}>
      {/* Top Banner Card */}
      <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <h2 style={{ margin: 0, fontSize: '20px', color: 'var(--wa-teal)' }}>
              📊 Security & System Audit Trail
            </h2>
            <span style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '11px',
              background: '#00a88422',
              color: 'var(--wa-teal)',
              padding: '3px 8px',
              borderRadius: '12px',
              fontWeight: 'bold',
              border: '1px solid #00a88444'
            }}>
              <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--wa-teal)', animation: 'pulse 1.5s infinite' }} />
              LIVE (2.5s)
            </span>
          </div>
          <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: 'var(--text-secondary)' }}>
            Real-time compliance monitoring: command dispatch, AES-256 decryption, drive sync, and authorization events.
          </p>
        </div>

        {/* Filter & Refresh Buttons */}
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {['ALL', 'SUCCESS', 'DENIED', 'ALERT'].map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              style={{
                background: filter === f ? 'var(--wa-teal)' : 'var(--bg-card)',
                color: filter === f ? '#ffffff' : 'var(--text-primary)',
                fontWeight: filter === f ? 'bold' : 'normal',
                border: `1px solid ${filter === f ? 'var(--wa-teal)' : 'var(--border-subtle)'}`,
                padding: '6px 14px',
                borderRadius: '8px',
                cursor: 'pointer',
                fontSize: '12px',
                transition: 'all 0.2s',
                boxShadow: filter === f ? '0 2px 4px rgba(0, 128, 105, 0.2)' : 'none'
              }}
            >
              {f}
            </button>
          ))}

          <button
            onClick={handleManualRefresh}
            className={`common-refresh-btn ${refreshing ? 'refreshing' : ''}`}
            title="Refresh security audit log trail"
          >
            <span className={refreshing ? 'spin-icon' : ''}>🔄</span>
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {/* Audit Logs Table */}
      <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', flex: 1, boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '12px' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-secondary)', background: 'var(--bg-card-hover)' }}>
                <th style={{ padding: '10px 12px' }}>Timestamp</th>
                <th style={{ padding: '10px 12px' }}>Sender Phone</th>
                <th style={{ padding: '10px 12px' }}>Role</th>
                <th style={{ padding: '10px 12px' }}>Action</th>
                <th style={{ padding: '10px 12px' }}>Status</th>
                <th style={{ padding: '10px 12px' }}>Event Details</th>
              </tr>
            </thead>
            <tbody>
              {filteredLogs.map((log) => {
                const isDenied = log.status === 'DENIED' || log.status === 'RESTRICTED';
                return (
                  <tr key={log.id} style={{ borderBottom: '1px solid var(--border-subtle)', transition: 'background 0.2s' }}>
                    <td style={{ padding: '10px 12px', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                      {log.timestamp ? new Date(log.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '-'}
                    </td>
                    <td style={{ padding: '10px 12px', fontFamily: 'monospace', color: 'var(--text-link)' }}>
                      +{log.senderPhoneNumber}
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{
                        fontSize: '10px',
                        background: log.userRole === 'ADMIN' ? '#00a88422' : '#3b82f622',
                        color: log.userRole === 'ADMIN' ? 'var(--wa-teal)' : '#0284c7',
                        padding: '2px 6px',
                        borderRadius: '4px',
                        fontWeight: 'bold'
                      }}>
                        {log.userRole || 'UNKNOWN'}
                      </span>
                    </td>
                    <td style={{ padding: '10px 12px', fontWeight: '600', color: 'var(--text-primary)' }}>
                      {log.action}
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{
                        fontSize: '10px',
                        background: isDenied ? 'var(--danger-bg)' : '#00a88422',
                        color: isDenied ? 'var(--danger-text)' : 'var(--wa-teal)',
                        border: isDenied ? '1px solid var(--danger-border)' : '1px solid #00a88444',
                        padding: '2px 6px',
                        borderRadius: '4px',
                        fontWeight: 'bold'
                      }}>
                        {log.status}
                      </span>
                    </td>
                    <td style={{ padding: '10px 12px', color: 'var(--text-primary)' }}>
                      {log.details}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
