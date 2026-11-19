import React from 'react';

export default function PersonaSidebar({ 
  personas, 
  activePersona, 
  onSelectPersona, 
  onQuickCommand 
}) {
  return (
    <aside className="sidebar">
      {/* 1. Active Family Caller Container */}
      <div className="fast-cmd-card">
        <div className="section-title">
          <span style={{ color: 'var(--wa-teal)', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span>👤</span> 1. Active Family Caller
          </span>
          <span className={`role-tag ${activePersona?.role?.toLowerCase() || 'admin'}`}>
            {activePersona?.role || 'ADMIN'}
          </span>
        </div>

        <div className="persona-cards">
          {personas.map((p) => {
            const isAdmin = p.role === 'ADMIN';
            const isActive = activePersona?.phoneNumber === p.phoneNumber;
            return (
              <div
                key={p.phoneNumber}
                className={`persona-card ${isActive ? 'active' : ''}`}
                onClick={() => onSelectPersona(p)}
                title={`Click to simulate caller: ${p.name}`}
              >
                <div className="persona-avatar">{isAdmin ? '👑' : '👤'}</div>
                <div className="persona-info">
                  <div className="persona-name">{p.name}</div>
                  <div className="persona-phone">+{p.phoneNumber}</div>
                </div>
                <span className={`role-tag ${p.role?.toLowerCase()}`}>{p.role}</span>
              </div>
            );
          })}

          {/* Stranger persona for security & access denied testing */}
          <div
            className={`persona-card ${activePersona?.phoneNumber === '919999999999' ? 'active' : ''}`}
            onClick={() => onSelectPersona({
              name: 'Stranger (Intruder)',
              phoneNumber: '919999999999',
              role: 'UNAUTHORIZED'
            })}
            title="Click to simulate an unauthorized intruder"
          >
            <div className="persona-avatar">🚨</div>
            <div className="persona-info">
              <div className="persona-name">Stranger (Intruder)</div>
              <div className="persona-phone">+91 9999999999</div>
            </div>
            <span className="role-tag stranger">Block</span>
          </div>
        </div>
      </div>

      {/* 2. Active Test Caller Simulated Phone Box */}
      <div className="custom-phone-box">
        <label>
          <span style={{ color: 'var(--wa-teal)', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span>📱</span> Active Test Caller
          </span>
          <span style={{ fontSize: '9px', background: '#00a88422', color: 'var(--wa-teal)', padding: '1px 6px', borderRadius: '4px', border: '1px solid #00a88444' }}>
            SIMULATED
          </span>
        </label>
        <div className="simulated-number-badge">
          <span style={{ color: 'var(--wa-teal)', fontWeight: 'bold' }}>+</span>
          <input 
            type="text" 
            value={activePersona?.phoneNumber || activePersona?.phone || ''} 
            readOnly 
            title="Active phone number being simulated in the chat"
          />
          <span style={{ 
            fontSize: '11px', 
            fontWeight: 600,
            color: activePersona?.role === 'ADMIN' ? 'var(--wa-teal)' : activePersona?.role === 'UNAUTHORIZED' ? '#f87171' : '#0284c7',
            whiteSpace: 'nowrap'
          }}>
            {activePersona?.role === 'ADMIN' ? '👑 Admin' : activePersona?.role === 'UNAUTHORIZED' ? '🚨 Block' : `👤 ${activePersona?.name?.split(' ')[0] || 'User'}`}
          </span>
        </div>
      </div>

      {/* 3. Fast Commands Section */}
      <div className="category-group">
        <div className="section-title" style={{ marginBottom: '8px' }}>
          <span>⚡ Fast Commands</span>
          <span style={{ fontSize: '10px', color: 'var(--wa-teal)' }}>Symmetric Grid</span>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {/* Card: Session Control */}
          <div className="fast-cmd-card">
            <div style={{ fontSize: '11px', color: 'var(--wa-teal)', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span>🔑</span> Session Control (Strict 'hii')
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
              <button 
                onClick={() => onQuickCommand('hii')}
                style={{
                  background: 'var(--wa-teal)',
                  border: 'none',
                  color: '#ffffff',
                  fontWeight: 'bold',
                  padding: '8px 6px',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  fontSize: '11px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '4px',
                  transition: 'all 0.15s'
                }}
              >
                👋 Start ('hii')
              </button>
              <button 
                onClick={() => onQuickCommand('exit')}
                style={{
                  background: 'var(--danger-bg)',
                  border: '1px solid var(--danger-border)',
                  color: 'var(--danger-text)',
                  fontWeight: 'bold',
                  padding: '8px 6px',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  fontSize: '11px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '4px',
                  transition: 'all 0.15s'
                }}
              >
                🚪 Exit ('bye')
              </button>
            </div>
          </div>

          {/* Card: Document Services */}
          <div className="fast-cmd-card">
            <div style={{ fontSize: '11px', color: 'var(--wa-teal)', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span>⚡</span> Document Services
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
              <button className="aligned-btn" onClick={() => onQuickCommand('1')}>1️⃣ Aadhaar</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('2')}>2️⃣ PAN Card</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('3')}>3️⃣ License</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('4')}>4️⃣ Passport</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('5')}>5️⃣ Health</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('6')}>6️⃣ Other Docs</button>
            </div>
          </div>

          {/* Card: Drive & Vault Access */}
          <div className="fast-cmd-card">
            <div style={{ fontSize: '11px', color: 'var(--wa-teal)', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span>📁</span> Drive & Vault Access
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
              <button className="aligned-btn" onClick={() => onQuickCommand('7')}>7️⃣ List Files</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('8')}>8️⃣ Stats</button>
            </div>
          </div>

          {/* Card: Admin Controls */}
          {activePersona?.role === 'ADMIN' && (
            <div className="fast-cmd-card">
              <div style={{ fontSize: '11px', color: 'var(--wa-teal)', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span>👑</span> Admin Controls
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                <button className="aligned-btn" onClick={() => onQuickCommand('/issues')}>🚨 Issues</button>
                <button className="aligned-btn" onClick={() => onQuickCommand('/pending')}>⏳ Pending</button>
                <button className="aligned-btn" onClick={() => onQuickCommand('/analytics')}>📈 Analytics</button>
                <button className="aligned-btn" onClick={() => onQuickCommand('/listusers')}>👥 Users</button>
                <button className="aligned-btn" onClick={() => onQuickCommand('/logs')}>🛡️ Logs</button>
                <button className="aligned-btn" onClick={() => onQuickCommand('8')}>📊 Stats</button>
              </div>
            </div>
          )}

          {/* Card: Help & Escalation */}
          <div className="fast-cmd-card">
            <div style={{ fontSize: '11px', color: 'var(--wa-teal)', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span>🆘</span> Help & Escalation
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
              <button className="aligned-btn" onClick={() => onQuickCommand('contact')} title="Get Master Admin contact details">📞 Contact</button>
              <button className="aligned-btn" onClick={() => onQuickCommand('report')} title="Report an issue or problem with your documents">🚨 Report</button>
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
}
