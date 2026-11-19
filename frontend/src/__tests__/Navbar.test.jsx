import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Navbar from '../components/Navbar';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    checkHealth: vi.fn().mockResolvedValue(true),
    fetchIssues: vi.fn().mockResolvedValue([
      { id: 1, senderPhone: '919876543211', senderName: 'Dad', details: 'Need update' }
    ])
  }
}));

describe('Navbar Component Unit Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders application brand title and navigation tabs', async () => {
    render(<Navbar activeTab="chat" onTabChange={() => {}} />);

    expect(screen.getByText('Family WhatsApp Vault')).toBeInTheDocument();
    expect(screen.getByText(/WhatsApp Chat/)).toBeInTheDocument();
    expect(screen.getByText(/User Documents Vault/)).toBeInTheDocument();
    expect(screen.getByText(/Users & Services/)).toBeInTheDocument();
    expect(screen.getByText(/Audit Trail/)).toBeInTheDocument();
  });

  it('triggers onTabChange when a nav tab is clicked', () => {
    const handleTabChange = vi.fn();
    render(<Navbar activeTab="chat" onTabChange={handleTabChange} />);

    const vaultBtn = screen.getByText(/User Documents Vault/);
    fireEvent.click(vaultBtn);

    expect(handleTabChange).toHaveBeenCalledWith('vault');
  });

  it('displays issue count badge when issues are fetched', async () => {
    render(<Navbar activeTab="chat" onTabChange={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText(/Issues Inbox \(1\)/)).toBeInTheDocument();
    });
  });

  it('toggles theme between light and dark mode', () => {
    render(<Navbar activeTab="chat" onTabChange={() => {}} />);

    const themeToggleBtn = screen.getByTitle(/Toggle Light \/ Dark Color Theme/);
    expect(themeToggleBtn).toBeInTheDocument();

    fireEvent.click(themeToggleBtn);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');

    fireEvent.click(themeToggleBtn);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });
});
