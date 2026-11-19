const API_KEY = 'chat-vault-secret-admin-key-2026';
const ADMIN_PHONE = '918790208445';

export const api = {
  getApiKey() {
    return API_KEY;
  },

  getAdminPhone() {
    return ADMIN_PHONE;
  },

  async sendMessage(senderPhone, message) {
    const response = await fetch('/api/test/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-KEY': API_KEY
      },
      body: JSON.stringify({ senderPhone, message })
    });
    return response.json();
  },

  async uploadDocument(file, caption, callerPhone) {
    return this.uploadDocumentEx({ file, caption, callerPhone });
  },

  async uploadDocumentEx({ file, caption, customFileName, targetPhone, targetFolder, callerPhone }) {
    const formData = new FormData();
    formData.append('file', file);
    if (caption) formData.append('caption', caption);
    if (customFileName) formData.append('customFileName', customFileName);
    if (targetPhone) formData.append('targetPhone', targetPhone);
    if (targetFolder) formData.append('targetFolder', targetFolder);

    const response = await fetch('/api/documents/upload', {
      method: 'POST',
      headers: {
        'X-Caller-Phone': callerPhone || ADMIN_PHONE,
        'X-API-KEY': API_KEY
      },
      body: formData
    });
    return response.json();
  },

  async fetchDocuments(userPhone, callerPhone) {
    let url = '/api/documents';
    const params = new URLSearchParams();
    if (userPhone) params.append('userPhone', userPhone);
    if (params.toString()) url += `?${params.toString()}`;

    const response = await fetch(url, {
      headers: {
        'X-Caller-Phone': callerPhone || ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    if (!response.ok) return [];
    const data = await response.json();
    return Array.isArray(data) ? data : (data.data || []);
  },

  getViewUrl(docId, callerPhone) {
    const phone = callerPhone || ADMIN_PHONE;
    return `/api/documents/${docId}/view?inline=true&callerPhone=${encodeURIComponent(phone)}&apiKey=${encodeURIComponent(API_KEY)}`;
  },

  getDownloadUrl(docId, callerPhone) {
    const phone = callerPhone || ADMIN_PHONE;
    return `/api/documents/${docId}/download?callerPhone=${encodeURIComponent(phone)}&apiKey=${encodeURIComponent(API_KEY)}`;
  },

  async deleteDocument(docId, callerPhone) {
    const response = await fetch(`/api/documents/${docId}`, {
      method: 'DELETE',
      headers: {
        'X-Caller-Phone': callerPhone || ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    return response.json();
  },

  async fetchAuditLogs() {
    const response = await fetch('/api/logs', {
      headers: {
        'X-Caller-Phone': ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    if (!response.ok) return [];
    const data = await response.json();
    return Array.isArray(data) ? data : (data.data || []);
  },

  async fetchIssues() {
    const response = await fetch('/api/issues', {
      headers: {
        'X-Caller-Phone': ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    if (!response.ok) return [];
    const data = await response.json();
    return Array.isArray(data) ? data : (data.data || []);
  },

  async fetchDynamicStats(callerPhone) {
    const response = await fetch('/api/stats/dynamic', {
      headers: {
        'X-Caller-Phone': callerPhone || ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    if (!response.ok) return null;
    return response.json();
  },

  async fetchWhitelist() {
    const response = await fetch('/api/whitelist', {
      headers: {
        'X-Caller-Phone': ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    if (!response.ok) return [];
    const data = await response.json();
    return data.data || [];
  },

  async addWhitelistMember(member) {
    const response = await fetch('/api/whitelist', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Caller-Phone': ADMIN_PHONE,
        'X-API-KEY': API_KEY
      },
      body: JSON.stringify(member)
    });
    return response.json();
  },

  async removeWhitelistMember(phone) {
    const response = await fetch(`/api/whitelist/${phone}`, {
      method: 'DELETE',
      headers: {
        'X-Caller-Phone': ADMIN_PHONE,
        'X-API-KEY': API_KEY
      }
    });
    return response.json();
  },

  async updateUserServices(phone, services) {
    const response = await fetch(`/api/whitelist/${phone}/services`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-Caller-Phone': ADMIN_PHONE,
        'X-API-KEY': API_KEY
      },
      body: JSON.stringify({ services })
    });
    return response.json();
  },

  async fetchServices() {
    const response = await fetch('/api/services');
    if (!response.ok) return [];
    const data = await response.json();
    return data.data || [];
  },

  async checkHealth() {
    try {
      const response = await fetch('/api/health');
      return response.ok;
    } catch {
      return false;
    }
  }
};
