import React, { useState, useEffect } from 'react';
import { api } from '../services/api';

export default function UserDocumentsVault({ activePersona }) {
  const [users, setUsers] = useState([]);
  const [selectedUser, setSelectedUser] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadStatus, setUploadStatus] = useState(null);
  const [previewDoc, setPreviewDoc] = useState(null);

  // Form states for admin upload to user's folder
  const [uploadFile, setUploadFile] = useState(null);
  const [customFileName, setCustomFileName] = useState('');
  const [selectedService, setSelectedService] = useState('AADHAAR');
  const [caption, setCaption] = useState('');

  useEffect(() => {
    loadUsers();
  }, []);

  useEffect(() => {
    if (selectedUser) {
      loadDocuments(selectedUser.phoneNumber);
    }
  }, [selectedUser]);

  const loadUsers = async () => {
    try {
      const data = await api.fetchWhitelist();
      setUsers(data);
      if (data && data.length > 0 && !selectedUser) {
        // Default to active persona or first member
        const matched = data.find((u) => u.phoneNumber === activePersona.phone) || data[0];
        setSelectedUser(matched);
      }
    } catch (e) {
      console.error('Failed to load users for vault:', e);
    }
  };

  const loadDocuments = async (phone) => {
    setLoading(true);
    try {
      const docs = await api.listDocuments(phone);
      setDocuments(docs);
    } catch (e) {
      console.error('Failed to load user documents:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleManualRefresh = async () => {
    if (!selectedUser) return;
    setRefreshing(true);
    await loadDocuments(selectedUser.phoneNumber);
    setRefreshing(false);
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!uploadFile || !selectedUser) return;

    setUploading(true);
    setUploadStatus(null);

    try {
      let finalName = customFileName.trim();
      if (!finalName) {
        finalName = selectedService.toLowerCase().replace('_', '-');
      }

      if (!finalName.includes('.') && uploadFile.name.includes('.')) {
        const ext = uploadFile.name.substring(uploadFile.name.lastIndexOf('.'));
        finalName += ext;
      }

      const res = await api.uploadDocumentEx({
        file: uploadFile,
        caption: caption || `${selectedService} document for ${selectedUser.name}`,
        customFileName: finalName,
        targetPhone: selectedUser.phoneNumber,
        callerPhone: activePersona.phone
      });

      if (res.status === 'SUCCESS' || res.statusCode === 201) {
        setUploadStatus({
          type: 'success',
          message: `✅ Stored as '${finalName}' in ${selectedUser.name}'s Google Drive folder: '${selectedUser.folderName}'`
        });
        setUploadFile(null);
        setCustomFileName('');
        setCaption('');
        loadDocuments(selectedUser.phoneNumber);
      } else {
        setUploadStatus({
          type: 'error',
          message: `❌ Upload failed: ${res.message || 'Error uploading'}`
        });
      }
    } catch (err) {
      setUploadStatus({
        type: 'error',
        message: `❌ Upload error: ${err.message}`
      });
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (docId) => {
    if (!window.confirm(`Are you sure you want to delete document [${docId}]? This will remove it from disk, drive sync, and H2 database.`)) {
      return;
    }

    try {
      const res = await api.deleteDocument(docId, activePersona.phone);
      if (res.status === 'SUCCESS' || res.statusCode === 200) {
        alert(`Document ${docId} deleted successfully.`);
        loadDocuments(selectedUser.phoneNumber);
      } else {
        alert(`Delete failed: ${res.message}`);
      }
    } catch (e) {
      alert(`Delete error: ${e.message}`);
    }
  };

  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const isImage = (mime) => mime && mime.startsWith('image/');
  const isPdf = (mime) => mime === 'application/pdf';

  return (
    <div style={{ display: 'flex', height: '100%', gap: '16px', padding: '16px', background: 'var(--bg-body)', color: 'var(--text-primary)' }}>
      {/* Left Sidebar: Users List */}
      <div style={{ width: '280px', background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <div style={{ padding: '16px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-card-hover)', borderRadius: '12px 12px 0 0' }}>
          <h3 style={{ margin: 0, fontSize: '16px', color: 'var(--wa-teal)', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>👥</span> Family Members Vault
          </h3>
          <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: 'var(--text-secondary)' }}>
            Select any user to view/manage their docs
          </p>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: '8px' }}>
          {users.map((u) => {
            const isSelected = selectedUser && selectedUser.phoneNumber === u.phoneNumber;
            return (
              <div
                key={u.phoneNumber}
                onClick={() => setSelectedUser(u)}
                style={{
                  padding: '12px',
                  borderRadius: '8px',
                  marginBottom: '6px',
                  cursor: 'pointer',
                  background: isSelected ? 'var(--bg-card-active)' : 'transparent',
                  border: isSelected ? '1.5px solid var(--wa-teal)' : '1px solid transparent',
                  transition: 'all 0.2s ease',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px'
                }}
              >
                <div style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '50%',
                  background: u.role === 'ADMIN' ? 'var(--wa-teal)' : '#0284c7',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 'bold',
                  fontSize: '16px'
                }}>
                  {u.name.charAt(0)}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ fontWeight: '600', fontSize: '14px', color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {u.name}
                    </div>
                    {u.role === 'ADMIN' && (
                      <span style={{ fontSize: '10px', background: '#00a88422', color: 'var(--wa-teal)', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold' }}>
                        ADMIN
                      </span>
                    )}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                    +{u.phoneNumber}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-link)', marginTop: '2px' }}>
                    📁 {u.folderName}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Right Content: Selected User Vault */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '16px', overflowY: 'auto' }}>
        {selectedUser ? (
          <>
            {/* User Profile Header Card */}
            <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                <div>
                  <h2 style={{ margin: 0, fontSize: '20px', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span>📁</span> {selectedUser.name}'s Document Vault
                  </h2>
                  <div style={{ display: 'flex', gap: '16px', marginTop: '6px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                    <span>📞 Phone: <strong style={{ color: 'var(--text-primary)' }}>+{selectedUser.phoneNumber}</strong></span>
                    <span>☁️ Google Drive Folder: <strong style={{ color: 'var(--wa-teal)' }}>drive/chatbot_documents/{selectedUser.folderName}/</strong></span>
                    <span>🔒 Status: <strong style={{ color: 'var(--wa-teal)' }}>AES-256 Encrypted</strong></span>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    onClick={handleManualRefresh}
                    className={`common-refresh-btn ${refreshing ? 'refreshing' : ''}`}
                    title="Refresh this member's document vault"
                  >
                    <span className={refreshing ? 'spin-icon' : ''}>🔄</span>
                    <span>{refreshing ? 'Refreshing...' : 'Refresh Docs'}</span>
                  </button>
                </div>
              </div>

              {/* Allowed Services Pills */}
              <div style={{ marginTop: '12px', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Allowed Services:</span>
                {(() => {
                  const servicesList = Array.isArray(selectedUser.allowedServices)
                    ? selectedUser.allowedServices
                    : typeof selectedUser.allowedServices === 'string'
                      ? selectedUser.allowedServices.split(',').map(s => s.trim()).filter(Boolean)
                      : ['AADHAAR', 'PAN', 'DRIVING_LICENSE', 'PASSPORT', 'HEALTH', 'OTHER'];
                  return servicesList.length > 0 ? (
                    servicesList.map((svc) => (
                      <span key={svc} style={{ fontSize: '11px', background: 'var(--bg-input)', color: 'var(--text-link)', padding: '3px 8px', borderRadius: '12px', border: '1px solid var(--border-subtle)' }}>
                        ✓ {svc}
                      </span>
                    ))
                  ) : (
                    <span style={{ fontSize: '11px', color: 'var(--danger-text)' }}>No services assigned</span>
                  );
                })()}
              </div>
            </div>

            {/* Quick Admin Action Toolbar */}
            <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '12px 16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
              <div style={{ fontSize: '11px', color: 'var(--wa-teal)', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span>⚡</span> Admin Fast Commands & Quick Nav
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '8px' }}>
                <a
                  href="/swagger-ui.html"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="aligned-btn"
                  style={{ textDecoration: 'none' }}
                >
                  ⚡ Swagger REST API
                </a>
                <a
                  href="https://wa.me/918790208445"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="aligned-btn"
                  style={{ textDecoration: 'none', color: 'var(--wa-teal)' }}
                >
                  📞 Contact Admin (+91 8790208445)
                </a>
                <button
                  className="aligned-btn"
                  onClick={() => loadDocuments(selectedUser.phoneNumber)}
                >
                  🔄 Resync User Vault
                </button>
              </div>
            </div>

            {/* Upload to this User's Folder Card */}
            <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
              <h3 style={{ margin: '0 0 12px 0', fontSize: '15px', color: 'var(--wa-teal)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span>⬆️</span> Upload Document to {selectedUser.name}'s Folder
              </h3>

              <form onSubmit={handleUpload} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', alignItems: 'end' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    Select File (JPG, PNG, PDF, Any File) *
                  </label>
                  <input
                    type="file"
                    required
                    accept="*/*"
                    onChange={(e) => {
                      const f = e.target.files[0];
                      setUploadFile(f);
                      if (f && !customFileName) {
                        const defaultName = selectedService.toLowerCase().replace('_', '-');
                        setCustomFileName(defaultName);
                      }
                    }}
                    style={{
                      width: '100%',
                      padding: '8px',
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
                    Document File Name in Drive (e.g. aadhar, pan)
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. aadhar or pan (stored in user's folder)"
                    value={customFileName}
                    onChange={(e) => setCustomFileName(e.target.value)}
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
                    Document Category
                  </label>
                  <select
                    value={selectedService}
                    onChange={(e) => setSelectedService(e.target.value)}
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
                    <option value="AADHAAR">Aadhaar Card</option>
                    <option value="PAN">PAN Card</option>
                    <option value="DRIVING_LICENSE">Driving License</option>
                    <option value="PASSPORT">Passport</option>
                    <option value="HEALTH">Health / Insurance</option>
                    <option value="OTHER">Other Document</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    Caption / Description
                  </label>
                  <input
                    type="text"
                    placeholder="Optional notes or details"
                    value={caption}
                    onChange={(e) => setCaption(e.target.value)}
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
                  <button
                    type="submit"
                    disabled={uploading || !uploadFile}
                    style={{
                      width: '100%',
                      padding: '10px 16px',
                      background: uploading ? 'var(--border-subtle)' : 'var(--wa-teal)',
                      color: '#ffffff',
                      fontWeight: 'bold',
                      border: 'none',
                      borderRadius: '8px',
                      cursor: uploading ? 'not-allowed' : 'pointer',
                      fontSize: '13px',
                      transition: 'all 0.2s'
                    }}
                  >
                    {uploading ? '⏳ Encrypting & Syncing...' : `🚀 Upload to ${selectedUser.name}'s Vault`}
                  </button>
                </div>
              </form>

              {uploadStatus && (
                <div style={{
                  marginTop: '12px',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  fontSize: '13px',
                  background: uploadStatus.type === 'success' ? '#00a88422' : 'var(--danger-bg)',
                  color: uploadStatus.type === 'success' ? 'var(--wa-teal)' : 'var(--danger-text)',
                  border: `1px solid ${uploadStatus.type === 'success' ? 'var(--wa-teal)' : 'var(--danger-border)'}`
                }}>
                  {uploadStatus.message}
                </div>
              )}
            </div>

            {/* Documents List Table */}
            <div style={{ background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border-subtle)', padding: '16px', flex: 1, boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <h3 style={{ margin: 0, fontSize: '16px', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span>📑</span> Documents Present ({documents.length})
                </h3>
                <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                  Live cloud sync enabled with Google Drive
                </span>
              </div>

              {loading ? (
                <div style={{ padding: '32px', textAlign: 'center', color: 'var(--text-secondary)' }}>
                  ⏳ Loading documents from secure vault...
                </div>
              ) : documents.length === 0 ? (
                <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-secondary)', background: 'var(--bg-input)', borderRadius: '8px', border: '1px dashed var(--border-subtle)' }}>
                  <div style={{ fontSize: '32px', marginBottom: '8px' }}>📭</div>
                  <div style={{ fontSize: '15px', color: 'var(--text-primary)', fontWeight: 'bold' }}>No documents uploaded yet for {selectedUser.name}</div>
                  <div style={{ fontSize: '13px', marginTop: '4px' }}>Use the form above to upload their first Aadhaar, PAN, or Passport document.</div>
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-secondary)', background: 'var(--bg-card-hover)' }}>
                        <th style={{ padding: '10px 12px' }}>Doc ID</th>
                        <th style={{ padding: '10px 12px' }}>File Name</th>
                        <th style={{ padding: '10px 12px' }}>Category</th>
                        <th style={{ padding: '10px 12px' }}>Size</th>
                        <th style={{ padding: '10px 12px' }}>Uploaded</th>
                        <th style={{ padding: '10px 12px' }}>Encryption</th>
                        <th style={{ padding: '10px 12px', textAlign: 'center' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {documents.map((doc) => {
                        const downloadUrl = api.getDownloadUrl(doc.docId, activePersona.phone);

                        return (
                          <tr key={doc.docId} style={{ borderBottom: '1px solid var(--border-subtle)', transition: 'background 0.2s' }}>
                            <td style={{ padding: '12px', fontFamily: 'monospace', color: 'var(--wa-teal)', fontWeight: 'bold' }}>
                              {doc.docId}
                            </td>
                            <td style={{ padding: '12px' }}>
                              <div style={{ fontWeight: '600', color: 'var(--text-primary)' }}>
                                {doc.originalFileName || 'document'}
                              </div>
                              {doc.captionOrNote && (
                                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                                  {doc.captionOrNote}
                                </div>
                              )}
                            </td>
                            <td style={{ padding: '12px' }}>
                              <span style={{
                                fontSize: '11px',
                                background: 'var(--bg-input)',
                                color: 'var(--text-link)',
                                padding: '3px 8px',
                                borderRadius: '6px',
                                border: '1px solid var(--border-subtle)'
                              }}>
                                {doc.categoryTag || 'DOCUMENT'}
                              </span>
                            </td>
                            <td style={{ padding: '12px', color: 'var(--text-secondary)' }}>
                              {formatBytes(doc.fileSizeBytes)}
                            </td>
                            <td style={{ padding: '12px', color: 'var(--text-secondary)', fontSize: '12px' }}>
                              {doc.createdAt ? new Date(doc.createdAt).toLocaleString() : 'Recent'}
                            </td>
                            <td style={{ padding: '12px' }}>
                              <span style={{ fontSize: '11px', background: '#00a88422', color: 'var(--wa-teal)', padding: '3px 8px', borderRadius: '6px', border: '1px solid #00a88444' }}>
                                🔒 AES-256
                              </span>
                            </td>
                            <td style={{ padding: '12px', textAlign: 'center' }}>
                              <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
                                {/* View Button */}
                                <button
                                  onClick={() => setPreviewDoc(doc)}
                                  title="View Decrypted Document"
                                  style={{
                                    background: 'var(--bg-input)',
                                    border: '1px solid var(--text-link)',
                                    color: 'var(--text-link)',
                                    padding: '6px 10px',
                                    borderRadius: '6px',
                                    cursor: 'pointer',
                                    fontSize: '12px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                  }}
                                >
                                  <span>👁️</span> View
                                </button>

                                {/* Download Button */}
                                <a
                                  href={downloadUrl}
                                  download={doc.originalFileName}
                                  title="Download Decrypted File"
                                  style={{
                                    background: 'var(--wa-teal)',
                                    color: '#ffffff',
                                    fontWeight: 'bold',
                                    padding: '6px 10px',
                                    borderRadius: '6px',
                                    textDecoration: 'none',
                                    fontSize: '12px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                  }}
                                >
                                  <span>⬇️</span> Download
                                </a>

                                {/* Delete Button (Admin only) */}
                                {activePersona.role === 'ADMIN' && (
                                  <button
                                    onClick={() => handleDelete(doc.docId)}
                                    title="Permanently Delete Document"
                                    style={{
                                      background: 'var(--bg-input)',
                                      border: '1px solid var(--danger-border)',
                                      color: 'var(--danger-text)',
                                      padding: '6px 8px',
                                      borderRadius: '6px',
                                      cursor: 'pointer',
                                      fontSize: '12px'
                                    }}
                                  >
                                    🗑️
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        ) : (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-secondary)' }}>
            Please select a family member from the left sidebar.
          </div>
        )}
      </div>

      {/* Decrypted Document View / Preview Modal */}
      {previewDoc && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.75)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: '24px'
        }}>
          <div style={{
            background: 'var(--bg-card)',
            borderRadius: '12px',
            border: '1px solid var(--border-subtle)',
            width: '90%',
            maxWidth: '900px',
            height: '85vh',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)'
          }}>
            {/* Modal Header */}
            <div style={{
              padding: '14px 20px',
              borderBottom: '1px solid var(--border-subtle)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              background: 'var(--bg-chat-header)'
            }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '16px', color: 'var(--text-primary)' }}>
                  👁️ Viewing: {previewDoc.originalFileName}
                </h3>
                <span style={{ fontSize: '12px', color: 'var(--wa-teal)' }}>
                  {previewDoc.docId} • 🔒 Decrypted Live on Server
                </span>
              </div>
              <div style={{ display: 'flex', gap: '8px' }}>
                <a
                  href={api.getViewUrl(previewDoc.docId, activePersona.phone)}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    background: 'var(--bg-input)',
                    border: '1px solid var(--border-subtle)',
                    color: 'var(--text-link)',
                    padding: '6px 12px',
                    borderRadius: '6px',
                    fontSize: '12px',
                    textDecoration: 'none'
                  }}
                >
                  🔗 Open in New Tab
                </a>
                <button
                  onClick={() => setPreviewDoc(null)}
                  style={{
                    background: 'var(--danger-bg)',
                    color: 'var(--danger-text)',
                    border: '1px solid var(--danger-border)',
                    padding: '6px 12px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontWeight: 'bold'
                  }}
                >
                  ✕ Close
                </button>
              </div>
            </div>

            {/* Modal Content Preview */}
            <div style={{ flex: 1, background: 'var(--bg-body)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px', overflow: 'hidden' }}>
              {isImage(previewDoc.mimeType) ? (
                <img
                  src={api.getViewUrl(previewDoc.docId, activePersona.phone)}
                  alt={previewDoc.originalFileName}
                  style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', borderRadius: '8px' }}
                />
              ) : isPdf(previewDoc.mimeType) ? (
                <iframe
                  src={api.getViewUrl(previewDoc.docId, activePersona.phone)}
                  title={previewDoc.originalFileName}
                  style={{ width: '100%', height: '100%', border: 'none', borderRadius: '8px' }}
                />
              ) : (
                <div style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
                  <div style={{ fontSize: '48px', marginBottom: '12px' }}>📄</div>
                  <div style={{ fontSize: '16px', color: 'var(--text-primary)' }}>{previewDoc.originalFileName}</div>
                  <div style={{ fontSize: '13px', marginTop: '6px' }}>MIME Type: {previewDoc.mimeType || 'Unknown'}</div>
                  <div style={{ marginTop: '16px' }}>
                    <a
                      href={api.getDownloadUrl(previewDoc.docId, activePersona.phone)}
                      style={{
                        background: 'var(--wa-teal)',
                        color: '#ffffff',
                        padding: '10px 18px',
                        borderRadius: '8px',
                        textDecoration: 'none',
                        fontWeight: 'bold',
                        display: 'inline-block'
                      }}
                    >
                      ⬇️ Download Decrypted File
                    </a>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
