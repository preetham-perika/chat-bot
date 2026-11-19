import React, { useState, useRef, useEffect } from 'react';
import { api } from '../services/api';

export default function ChatWindow({ activePersona, messages, setMessages }) {
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [attachment, setAttachment] = useState(null);
  const [customFileName, setCustomFileName] = useState('');
  const [caption, setCaption] = useState('');
  const [targetPhone, setTargetPhone] = useState('');

  const isUnauthorized = activePersona?.role === 'UNAUTHORIZED' || activePersona?.role === 'STRANGER';

  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // When switching to an unauthorized / stranger persona, immediately close and clear any attachments
  useEffect(() => {
    if (isUnauthorized) {
      setAttachment(null);
      setCustomFileName('');
      setCaption('');
      setTargetPhone('');
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }, [activePersona?.phoneNumber, activePersona?.phone, activePersona?.role, isUnauthorized]);

  // Adjust textarea height dynamically
  const handleInputChange = (e) => {
    setInputText(e.target.value);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 120) + 'px';
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileSelect = (e) => {
    if (isUnauthorized) {
      alert("⛔ Access Denied: Unauthorized users are blocked from attaching files to the vault.");
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    const file = e.target.files[0];
    if (file) {
      setAttachment(file);
      setCustomFileName(file.name);
    }
  };

  const handleSend = async (overrideText) => {
    const textToSend = overrideText !== undefined ? overrideText : inputText;
    if ((!textToSend || !textToSend.trim()) && !attachment) return;

    setSending(true);

    try {
      if (attachment) {
        // Upload flow
        const userMsg = {
          id: Date.now(),
          sender: 'user',
          text: `📎 [Attached: ${customFileName || attachment.name}] ${caption ? `\n"${caption}"` : ''}`,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages((prev) => [...prev, userMsg]);

        let targetFileName = customFileName.trim();
        if (targetFileName) {
          if (!targetFileName.includes('.') && attachment.name.includes('.')) {
            targetFileName += attachment.name.substring(attachment.name.lastIndexOf('.'));
          }
        } else {
          targetFileName = attachment.name;
        }

        const uploadRes = await api.uploadDocumentEx({
          file: attachment,
          caption: caption || targetFileName || attachment.name,
          customFileName: targetFileName,
          targetPhone: targetPhone || activePersona.phone,
          callerPhone: activePersona.phone
        });

        // Reset attachment
        setAttachment(null);
        setCustomFileName('');
        setCaption('');
        setTargetPhone('');
        if (fileInputRef.current) fileInputRef.current.value = '';

        let botReply = '';
        if (uploadRes.status === 'SUCCESS' || uploadRes.statusCode === 201) {
          botReply = `✅ *Document Uploaded & AES-256 Encrypted!*\n• Doc ID: \`${uploadRes.data?.docId || 'DOC-NEW'}\`\n• File Name: \`${uploadRes.data?.originalFileName}\`\n• Category: *${uploadRes.data?.categoryTag}*\n• Cloud Sync: Synced to Google Drive folder: \`${uploadRes.data?.ownerDriveFolder || 'drive'}\``;
        } else {
          botReply = `❌ *Upload Failed:* ${uploadRes.message || 'Unable to store document'}`;
        }

        const botMsg = {
          id: Date.now() + 1,
          sender: 'bot',
          text: botReply,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages((prev) => [...prev, botMsg]);

      } else {
        // Text command flow
        const userMsg = {
          id: Date.now(),
          sender: 'user',
          text: textToSend.trim(),
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages((prev) => [...prev, userMsg]);
        setInputText('');
        if (textareaRef.current) textareaRef.current.style.height = 'auto';

        const res = await api.sendMessage(activePersona.phone, textToSend.trim());
        const botReply = res.botReply || res.message || 'No response from server';

        const botMsg = {
          id: Date.now() + 1,
          sender: 'bot',
          text: botReply,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages((prev) => [...prev, botMsg]);
      }
    } catch (err) {
      const errMsg = {
        id: Date.now() + 2,
        sender: 'bot',
        text: `⚠️ *Error:* Failed to connect to server backend (${err.message})`,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setMessages((prev) => [...prev, errMsg]);
    } finally {
      setSending(false);
    }
  };

  // Helper to format WhatsApp markdown (bold, monospace, links)
  const formatWhatsAppText = (text) => {
    if (!text) return '';
    const lines = text.split('\n');

    return lines.map((line, idx) => {
      // Bold *text*
      let formatted = line.replace(/\*([^*]+)\*/g, '<strong>$1</strong>');
      // Monospace `text`
      formatted = formatted.replace(/`([^`]+)`/g, '<code style="background: rgba(255,255,255,0.1); padding: 2px 4px; border-radius: 4px; font-family: monospace;">$1</code>');
      // URLs
      formatted = formatted.replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" rel="noopener noreferrer" style="color: #53bdeb; text-decoration: underline;">$1</a>');

      return (
        <span key={idx} style={{ display: 'block', minHeight: line.trim() ? 'auto' : '10px' }} dangerouslySetInnerHTML={{ __html: formatted }} />
      );
    });
  };

  const quickChips = [
    { label: '👋 hii', cmd: 'hii' },
    { label: '1️⃣ Aadhaar', cmd: '1' },
    { label: '2️⃣ PAN', cmd: '2' },
    { label: '3️⃣ License', cmd: '3' },
    { label: '4️⃣ Passport', cmd: '4' },
    { label: '5️⃣ Health', cmd: '5' },
    { label: '6️⃣ Other', cmd: '6' },
    { label: '7️⃣ List All', cmd: '7' },
    { label: '8️⃣ Stats', cmd: '8' },
    { label: '👋 bye / exit', cmd: 'exit' }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--bg-body)' }}>
      {/* Chat Top Banner */}
      <div style={{
        padding: '12px 20px',
        background: 'var(--bg-chat-header)',
        borderBottom: '1px solid var(--border-subtle)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '50%',
            background: 'var(--wa-teal)',
            color: '#ffffff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '22px'
          }}>
            🤖
          </div>
          <div>
            <div style={{ fontWeight: 'bold', fontSize: '15px', color: 'var(--text-primary)' }}>
              Family Vault Bot (+91 8790208445)
            </div>
            <div style={{ fontSize: '12px', color: 'var(--wa-teal)', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%', background: 'var(--wa-teal)' }} />
              Live Bot Active • Strict 'hii' Initiated
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '12px', background: 'var(--bg-card)', color: 'var(--text-secondary)', padding: '4px 10px', borderRadius: '12px', border: '1px solid var(--border-subtle)' }}>
            Active as: <strong style={{ color: 'var(--wa-teal)' }}>{activePersona.name}</strong> ({activePersona.role})
          </span>
        </div>
      </div>

      {/* Messages Scroll Area */}
      <div style={{
        flex: 1,
        overflowY: 'auto',
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        background: 'var(--bg-body)'
      }}>
        {messages.map((msg) => {
          const isUser = msg.sender === 'user';
          return (
            <div
              key={msg.id}
              style={{
                alignSelf: isUser ? 'flex-end' : 'flex-start',
                maxWidth: '75%',
                background: isUser ? 'var(--wa-bubble-out)' : 'var(--wa-bubble-in)',
                border: isUser ? 'none' : '1px solid var(--wa-bubble-in-border)',
                color: 'var(--text-primary)',
                padding: '10px 14px',
                borderRadius: isUser ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
                boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
                fontSize: '14px',
                lineHeight: '1.45',
                wordBreak: 'break-word'
              }}
            >
              {formatWhatsAppText(msg.text)}
              <div style={{ fontSize: '10px', color: 'var(--text-secondary)', textAlign: 'right', marginTop: '4px' }}>
                {msg.time} {isUser && '✓✓'}
              </div>
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Quick Action Command Chips */}
      <div style={{
        padding: '8px 16px',
        background: 'var(--bg-chat-header)',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        gap: '8px',
        overflowX: 'auto',
        whiteSpace: 'nowrap'
      }}>
        {quickChips.map((chip) => (
          <button
            key={chip.label}
            onClick={() => handleSend(chip.cmd)}
            style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--wa-teal)',
              padding: '6px 14px',
              borderRadius: '16px',
              cursor: 'pointer',
              fontSize: '12px',
              fontWeight: '600',
              transition: 'all 0.2s',
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
            }}
          >
            {chip.label}
          </button>
        ))}
      </div>

      {/* Attachment Upload Preview Banner */}
      {attachment && !isUnauthorized && (
        <div style={{
          padding: '12px 16px',
          background: 'var(--bg-chat-header)',
          borderTop: '1px solid var(--border-subtle)',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '13px', color: 'var(--wa-teal)', fontWeight: 'bold' }}>
              📎 Attached: {attachment.name} ({(attachment.size / 1024).toFixed(1)} KB)
            </span>
            <button
              onClick={() => {
                setAttachment(null);
                if (fileInputRef.current) fileInputRef.current.value = '';
              }}
              style={{ background: 'transparent', border: 'none', color: '#f15c6d', cursor: 'pointer', fontSize: '16px' }}
            >
              ✕
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px' }}>
            <input
              type="text"
              placeholder="File Name in Drive (e.g. aadhar, pan)"
              value={customFileName}
              onChange={(e) => setCustomFileName(e.target.value)}
              style={{
                padding: '6px 10px',
                background: 'var(--bg-input)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '6px',
                color: 'var(--text-primary)',
                fontSize: '12px'
              }}
            />
            <input
              type="text"
              placeholder="Caption / Description"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              style={{
                padding: '6px 10px',
                background: 'var(--bg-input)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '6px',
                color: 'var(--text-primary)',
                fontSize: '12px'
              }}
            />
            {activePersona.role === 'ADMIN' && (
              <input
                type="text"
                placeholder="Target User Phone (Admin Superpower)"
                value={targetPhone}
                onChange={(e) => setTargetPhone(e.target.value)}
                style={{
                  padding: '6px 10px',
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '6px',
                  color: 'var(--text-primary)',
                  fontSize: '12px'
                }}
              />
            )}
          </div>
        </div>
      )}

      {/* Input Bar */}
      <div style={{
        padding: '12px 16px',
        background: 'var(--bg-chat-header)',
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex',
        alignItems: 'flex-end',
        gap: '10px'
      }}>
        {/* Hidden File Input */}
        <input
          type="file"
          ref={fileInputRef}
          accept="*/*"
          onChange={handleFileSelect}
          style={{ display: 'none' }}
        />

        {/* Paperclip Button or Locked State for Unauthorized Users */}
        {isUnauthorized ? (
          <button
            disabled
            title="⛔ File attachments are blocked for unauthorized callers"
            style={{
              background: 'transparent',
              border: 'none',
              fontSize: '20px',
              color: '#f87171',
              cursor: 'not-allowed',
              padding: '4px',
              opacity: 0.6
            }}
          >
            🔒
          </button>
        ) : (
          <button
            onClick={() => fileInputRef.current?.click()}
            title="Attach document or photo from computer / phone"
            style={{
              background: 'transparent',
              border: 'none',
              fontSize: '22px',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              padding: '4px',
              transition: 'color 0.2s'
            }}
          >
            📎
          </button>
        )}

        {/* Auto-expanding, Scrollable Textarea */}
        <textarea
          ref={textareaRef}
          rows={1}
          placeholder={
            isUnauthorized
              ? "⛔ Unauthorized Caller: Type any message to test access denial..."
              : "Type 'hii' to start vault session, or select options..."
          }
          value={inputText}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          style={{
            flex: 1,
            background: 'var(--bg-input)',
            border: '1px solid var(--border-subtle)',
            borderRadius: '8px',
            color: 'var(--text-primary)',
            fontSize: '14px',
            padding: '10px 14px',
            resize: 'none',
            maxHeight: '120px',
            minHeight: '40px',
            outline: 'none',
            fontFamily: 'inherit',
            lineHeight: '1.4',
            overflowY: 'auto'
          }}
        />

        {/* Send Button */}
        <button
          onClick={() => handleSend()}
          disabled={sending || (!inputText.trim() && !attachment)}
          style={{
            width: '42px',
            height: '42px',
            borderRadius: '50%',
            background: sending || (!inputText.trim() && !attachment) ? 'var(--border-subtle)' : 'var(--wa-teal)',
            color: '#ffffff',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: sending || (!inputText.trim() && !attachment) ? 'not-allowed' : 'pointer',
            fontSize: '18px',
            transition: 'all 0.2s'
          }}
        >
          {sending ? '⏳' : '➤'}
        </button>
      </div>
    </div>
  );
}
