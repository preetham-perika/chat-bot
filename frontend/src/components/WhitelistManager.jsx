import React, { useState, useEffect } from 'react';
import { api } from '../services/api';

const ALL_SERVICES = [
  { key: 'AADHAAR', label: 'Aadhaar Card' },
  { key: 'PAN', label: 'PAN Card' },
  { key: 'DRIVING_LICENSE', label: 'Driving License' },
  { key: 'PASSPORT', label: 'Passport' },
  { key: 'HEALTH', label: 'Health / Insurance' },
  { key: 'OTHER', label: 'Other Docs' }
];

export default function WhitelistManager({ activePersona }) {
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [statusMsg, setStatusMsg] = useState(null);

  // New member form state
  const [newName, setNewName] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newRole, setNewRole] = useState('MEMBER');
  const [selectedServices, setSelectedServices] = useState(['AADHAAR', 'PAN', 'DRIVING_LICENSE', 'PASSPORT', 'HEALTH', 'OTHER']);

  useEffect(() => {
    loadMembers();
  }, []);

  const loadMembers = async () => {
    setLoading(true);
    try {
      const data = await api.fetchWhitelist();
      setMembers(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to load whitelist members:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleManualRefresh = async () => {
    setRefreshing(true);
    await loadMembers();
    setRefreshing(false);
  };

  const handleToggleService = async (member, serviceKey) => {
    if (activePersona.role !== 'ADMIN') {
      alert('Only Master Admin can modify user document services');
      return;
    }

    const currentServices = new Set(member.allowedServices || []);
    if (currentServices.has(serviceKey)) {
      currentServices.delete(serviceKey);
    } else {
      currentServices.add(serviceKey);
    }

    const updatedList = Array.from(currentServices);

    try {
      const res = await api.updateUserServices(member.phoneNumber, updatedList);
      if (res.status === 'SUCCESS' || res.statusCode === 200) {
        setStatusMsg({ type: 'success', text: `Updated services for ${member.name}` });
        await loadMembers();
      } else {
        setStatusMsg({ type: 'error', text: res.message || 'Failed to update services' });
      }
    } catch (err) {
      setStatusMsg({ type: 'error', text: err.message || 'Network error' });
    }
  };

  const handleAddMember = async (e) => {
    e.preventDefault();
    if (!newName.trim() || !newPhone.trim()) return;

    try {
      const res = await api.addWhitelistMember({
        name: newName.trim(),
        phoneNumber: newPhone.trim(),
        role: newRole,
        allowedServices: selectedServices
      });

      if (res.status === 'SUCCESS' || res.statusCode === 200) {
        setStatusMsg({ type: 'success', text: `Added ${newName} to family whitelist` });
        setNewName('');
        setNewPhone('');
        await loadMembers();
      } else {
        setStatusMsg({ type: 'error', text: res.message || 'Failed to add member' });
      }
    } catch (err) {
      setStatusMsg({ type: 'error', text: err.message || 'Network error' });
    }
  };

  const handleRemoveMember = async (phone, name) => {
    if (!window.confirm(`Are you sure you want to remove ${name} (+${phone}) from the whitelist?`)) return;

    try {
      const res = await api.removeWhitelistMember(phone);
      if (res.status === 'SUCCESS' || res.statusCode === 200) {
        setStatusMsg({ type: 'success', text: `Removed ${name} from whitelist` });
        await loadMembers();
      } else {
        setStatusMsg({ type: 'error', text: res.message || 'Failed to remove member' });
      }
    } catch (err) {
      setStatusMsg({ type: 'error', text: err.message || 'Network error' });
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: '16px', padding: '16px', background: 'var(--bg-body)', color: 'var(--text-primary)', overflowY: 'auto' }}>
      {/* Header Card */}
      <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: '20px', color: 'var(--wa-teal)', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span>👥</span> Family Members & Document Services Management
            </h2>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: 'var(--text-secondary)' }}>
              Admin can dynamically add/remove members and customize which document services each member can access.
            </p>
          </div>
          <button
            onClick={handleManualRefresh}
            className={`common-refresh-btn ${refreshing ? 'refreshing' : ''}`}
            title="Refresh whitelist members and services"
          >
            <span className={refreshing ? 'spin-icon' : ''}>🔄</span>
            <span>{refreshing ? 'Refreshing...' : 'Refresh List'}</span>
          </button>
        </div>

        {statusMsg && (
          <div style={{
            marginTop: '12px',
            padding: '10px 14px',
            borderRadius: '8px',
            fontSize: '13px',
            background: statusMsg.type === 'success' ? '#00a88422' : 'var(--danger-bg)',
            color: statusMsg.type === 'success' ? 'var(--wa-teal)' : 'var(--danger-text)',
            border: `1px solid ${statusMsg.type === 'success' ? 'var(--wa-teal)' : 'var(--danger-border)'}`
          }}>
            {statusMsg.text}
          </div>
        )}
      </div>

      {/* Add New Member Form (Admin Superpower) */}
      {activePersona.role === 'ADMIN' && (
        <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <h3 style={{ margin: '0 0 12px 0', fontSize: '15px', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>➕</span> Register New Family Member
          </h3>

          <form onSubmit={handleAddMember} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', alignItems: 'end' }}>
            <div>
              <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                Full Name *
              </label>
              <input
                type="text"
                required
                placeholder="e.g. Sister, Uncle"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  color: 'var(--text-primary)',
                  fontSize: '13px'
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                WhatsApp Phone Number *
              </label>
              <input
                type="text"
                required
                placeholder="e.g. +91 9876543215"
                value={newPhone}
                onChange={(e) => setNewPhone(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  color: 'var(--text-primary)',
                  fontSize: '13px'
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                Role
              </label>
              <select
                value={newRole}
                onChange={(e) => setNewRole(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  color: 'var(--text-primary)',
                  fontSize: '13px'
                }}
              >
                <option value="MEMBER">Family Member</option>
                <option value="ADMIN">Master Admin</option>
              </select>
            </div>

            <div>
              <button
                type="submit"
                style={{
                  width: '100%',
                  padding: '10px 16px',
                  background: 'var(--wa-teal)',
                  color: '#ffffff',
                  fontWeight: 'bold',
                  border: 'none',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  fontSize: '13px'
                }}
              >
                ➕ Add to Whitelist
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Members & Allowed Services Table */}
      <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', flex: 1, boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <h3 style={{ margin: '0 0 14px 0', fontSize: '16px', color: 'var(--text-primary)' }}>
          📋 Whitelisted Members & Accessible Services
        </h3>

        {loading ? (
          <div style={{ padding: '32px', textAlign: 'center', color: 'var(--text-secondary)' }}>
            ⏳ Loading whitelist...
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-secondary)', background: 'var(--bg-card-hover)' }}>
                  <th style={{ padding: '10px 12px' }}>Member</th>
                  <th style={{ padding: '10px 12px' }}>Phone Number</th>
                  <th style={{ padding: '10px 12px' }}>Role</th>
                  <th style={{ padding: '10px 12px' }}>Drive Folder</th>
                  <th style={{ padding: '10px 12px' }}>Document Services (Click to Toggle)</th>
                  {activePersona.role === 'ADMIN' && (
                    <th style={{ padding: '10px 12px', textAlign: 'center' }}>Action</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {(members || []).map((m) => {
                  const userServices = new Set(m.allowedServices || []);

                  return (
                    <tr key={m.phoneNumber} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                      <td style={{ padding: '12px', fontWeight: '600', color: 'var(--text-primary)' }}>
                        {m.name}
                      </td>
                      <td style={{ padding: '12px', color: 'var(--text-secondary)', fontFamily: 'monospace' }}>
                        +{m.phoneNumber}
                      </td>
                      <td style={{ padding: '12px' }}>
                        <span style={{
                          fontSize: '11px',
                          background: m.role === 'ADMIN' ? '#00a88422' : '#3b82f622',
                          color: m.role === 'ADMIN' ? 'var(--wa-teal)' : '#0284c7',
                          padding: '3px 8px',
                          borderRadius: '4px',
                          fontWeight: 'bold'
                        }}>
                          {m.role}
                        </span>
                      </td>
                      <td style={{ padding: '12px', color: 'var(--text-secondary)', fontSize: '12px' }}>
                        📁 {m.folderName}
                      </td>
                      <td style={{ padding: '12px' }}>
                        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                          {ALL_SERVICES.map((s) => {
                            const isAssigned = userServices.has(s.key);
                            return (
                              <button
                                key={s.key}
                                onClick={() => handleToggleService(m, s.key)}
                                title={isAssigned ? `Revoke ${s.label}` : `Assign ${s.label}`}
                                style={{
                                  background: isAssigned ? '#00a88422' : 'var(--bg-input)',
                                  color: isAssigned ? 'var(--wa-teal)' : 'var(--text-secondary)',
                                  border: `1px solid ${isAssigned ? 'var(--wa-teal)' : 'var(--border-subtle)'}`,
                                  borderRadius: '6px',
                                  padding: '4px 8px',
                                  fontSize: '11px',
                                  cursor: activePersona.role === 'ADMIN' ? 'pointer' : 'default',
                                  transition: 'all 0.2s',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '4px'
                                }}
                              >
                                <span>{isAssigned ? '✓' : '✗'}</span>
                                {s.label}
                              </button>
                            );
                          })}
                        </div>
                      </td>
                      {activePersona.role === 'ADMIN' && (
                        <td style={{ padding: '12px', textAlign: 'center' }}>
                          {m.role !== 'ADMIN' && (
                            <button
                              onClick={() => handleRemoveMember(m.phoneNumber, m.name)}
                              style={{
                                background: 'transparent',
                                border: '1px solid var(--danger-border)',
                                color: 'var(--danger-text)',
                                padding: '4px 8px',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                fontSize: '11px'
                              }}
                            >
                              Remove
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
