import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import PersonaSidebar from './components/PersonaSidebar';
import ChatWindow from './components/ChatWindow';
import UserDocumentsVault from './components/UserDocumentsVault';
import WhitelistManager from './components/WhitelistManager';
import AuditTrail from './components/AuditTrail';
import IssuesInbox from './components/IssuesInbox';
import { api } from './services/api';

export default function App() {
  const [activeTab, setActiveTab] = useState('chat');
  const [personas, setPersonas] = useState([]);
  const [activePersona, setActivePersona] = useState({
    name: 'Hruday (Master Admin)',
    phoneNumber: '918790208445',
    phone: '918790208445',
    role: 'ADMIN'
  });

  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: 'bot',
      text: '🙏 *Please start the session with \'hii\' only.*\n━━━━━━━━━━━━━━━━━━━━\nWelcome to the Family Document Vault!\n\nTo begin your session and view your documents, please reply with:\n👉 *hii*',
      time: '12:00 PM'
    }
  ]);

  useEffect(() => {
    loadPersonas();
  }, []);

  const loadPersonas = async () => {
    try {
      const list = await api.fetchWhitelist();
      if (list && list.length > 0) {
        // Normalize phoneNumber and phone properties
        const normalized = list.map((m) => ({
          ...m,
          phone: m.phoneNumber
        }));
        setPersonas(normalized);
        const admin = normalized.find((p) => p.role === 'ADMIN');
        if (admin) setActivePersona(admin);
      }
    } catch (e) {
      console.error('Failed to load personas:', e);
    }
  };

  const handleSelectPersona = (p) => {
    const selected = {
      ...p,
      phone: p.phoneNumber || p.phone
    };
    setActivePersona(selected);

    // Add info message in chat about persona switch
    setMessages((prev) => [
      ...prev,
      {
        id: Date.now(),
        sender: 'bot',
        text: `🔄 Switched simulated user to *${selected.name}* (+${selected.phone}).\nPlease reply with *hii* to begin session.`,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
    ]);
  };

  const handleQuickCommand = async (cmd) => {
    // Switch to chat tab if not already on it
    if (activeTab !== 'chat') {
      setActiveTab('chat');
    }

    const userMsg = {
      id: Date.now(),
      sender: 'user',
      text: cmd,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    setMessages((prev) => [...prev, userMsg]);

    try {
      const res = await api.sendMessage(activePersona.phone, cmd);
      const botReply = res.botReply || res.message || 'No response from server';

      const botMsg = {
        id: Date.now() + 1,
        sender: 'bot',
        text: botReply,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setMessages((prev) => [...prev, botMsg]);
    } catch (err) {
      const errMsg = {
        id: Date.now() + 2,
        sender: 'bot',
        text: `⚠️ *Error:* ${err.message}`,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setMessages((prev) => [...prev, errMsg]);
    }
  };

  return (
    <div className="app-container" style={{ display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw', overflow: 'hidden' }}>
      {/* Top Navbar */}
      <Navbar activeTab={activeTab} onTabChange={setActiveTab} />

      {/* Main Workspace */}
      <div className="main-content" style={{ display: 'flex', flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {activeTab === 'chat' && (
          <>
            <PersonaSidebar
              personas={personas}
              activePersona={activePersona}
              onSelectPersona={handleSelectPersona}
              onQuickCommand={handleQuickCommand}
            />
            <main className="chat-area" style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, height: '100%' }}>
              <ChatWindow
                activePersona={activePersona}
                messages={messages}
                setMessages={setMessages}
              />
            </main>
          </>
        )}

        {activeTab === 'vault' && (
          <div style={{ flex: 1, height: '100%', overflow: 'hidden' }}>
            <UserDocumentsVault activePersona={activePersona} />
          </div>
        )}

        {activeTab === 'whitelist' && (
          <div style={{ flex: 1, height: '100%', overflow: 'hidden' }}>
            <WhitelistManager activePersona={activePersona} />
          </div>
        )}

        {activeTab === 'audit' && (
          <div style={{ flex: 1, height: '100%', overflow: 'hidden' }}>
            <AuditTrail />
          </div>
        )}

        {activeTab === 'issues' && (
          <div style={{ flex: 1, height: '100%', overflow: 'hidden' }}>
            <IssuesInbox activePersona={activePersona} />
          </div>
        )}
      </div>
    </div>
  );
}
