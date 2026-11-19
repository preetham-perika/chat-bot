import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import ChatWindow from '../components/ChatWindow';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    sendMessage: vi.fn().mockResolvedValue({
      status: 'SUCCESS',
      botReply: 'Welcome to your Private Vault!'
    })
  }
}));

describe('ChatWindow Component Unit Tests', () => {
  const defaultPersona = {
    name: 'Dad',
    phone: '919876543211',
    role: 'MEMBER'
  };

  const initialMessages = [
    { id: 1, sender: 'bot', text: 'Please start session with *hii*', time: '12:00 PM' }
  ];

  it('renders chat banner, active persona badge, and messages', () => {
    render(
      <ChatWindow
        activePersona={defaultPersona}
        messages={initialMessages}
        setMessages={() => {}}
      />
    );

    expect(screen.getByText(/Family Vault Bot/)).toBeInTheDocument();
    expect(screen.getByText(/Active as:/)).toBeInTheDocument();
    expect(screen.getByText('Dad')).toBeInTheDocument();
    expect(screen.getByText(/Please start session with/)).toBeInTheDocument();
  });

  it('allows user to type and send messages via Enter key', async () => {
    const setMessages = vi.fn();
    render(
      <ChatWindow
        activePersona={defaultPersona}
        messages={initialMessages}
        setMessages={setMessages}
      />
    );

    const input = screen.getByPlaceholderText(/Type 'hii' to start vault session/);
    fireEvent.change(input, { target: { value: 'hii' } });
    fireEvent.keyDown(input, { key: 'Enter', shiftKey: false });

    await waitFor(() => {
      expect(api.sendMessage).toHaveBeenCalledWith('919876543211', 'hii');
    });
  });

  it('disables file attachment paperclip for unauthorized strangers', () => {
    const strangerPersona = {
      name: 'Stranger (Intruder)',
      phone: '919999999999',
      role: 'UNAUTHORIZED'
    };

    render(
      <ChatWindow
        activePersona={strangerPersona}
        messages={initialMessages}
        setMessages={() => {}}
      />
    );

    // Locked attachment button for strangers
    const lockBtn = screen.getByTitle(/File attachments are blocked for unauthorized callers/);
    expect(lockBtn).toBeInTheDocument();
    expect(lockBtn).toBeDisabled();
  });
});
