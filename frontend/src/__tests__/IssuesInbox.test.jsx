import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import IssuesInbox from '../components/IssuesInbox';
import { api } from '../services/api';

const mockIssues = [
  {
    id: 101,
    senderPhone: '919876543211',
    senderName: 'Dad',
    timestamp: '2026-11-18T18:36:22.96385',
    details: 'User reported issue: Unable to download driving license'
  }
];

vi.mock('../services/api', () => ({
  api: {
    fetchIssues: vi.fn()
  }
}));

describe('IssuesInbox Component Unit Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders reported issue cards with user phone and description', async () => {
    api.fetchIssues.mockResolvedValueOnce(mockIssues);

    render(<IssuesInbox activePersona={{ role: 'ADMIN' }} />);

    await waitFor(() => {
      expect(screen.getByText('Dad')).toBeInTheDocument();
      expect(screen.getByText('(+919876543211)')).toBeInTheDocument();
      expect(screen.getByText(/"Unable to download driving license"/)).toBeInTheDocument();
      expect(screen.getByText(/TICKET #1 • OPEN/)).toBeInTheDocument();
    });
  });

  it('renders WhatsApp reply button linking to member phone', async () => {
    api.fetchIssues.mockResolvedValueOnce(mockIssues);

    render(<IssuesInbox activePersona={{ role: 'ADMIN' }} />);

    await waitFor(() => {
      const waReplyLink = screen.getByText(/Reply to Dad on WhatsApp/);
      expect(waReplyLink).toBeInTheDocument();
      expect(waReplyLink.closest('a')).toHaveAttribute(
        'href',
        expect.stringContaining('https://wa.me/919876543211')
      );
    });
  });

  it('triggers manual refresh when Refresh button is clicked', async () => {
    api.fetchIssues.mockResolvedValue(mockIssues);

    render(<IssuesInbox activePersona={{ role: 'ADMIN' }} />);

    await waitFor(() => {
      expect(screen.getByText('Refresh')).toBeInTheDocument();
    });

    const refreshBtn = screen.getByRole('button', { name: /Refresh/i });
    fireEvent.click(refreshBtn);

    expect(api.fetchIssues).toHaveBeenCalled();
  });

  it('displays empty celebration banner when no issues exist', async () => {
    api.fetchIssues.mockResolvedValueOnce([]);

    render(<IssuesInbox activePersona={{ role: 'ADMIN' }} />);

    await waitFor(() => {
      expect(screen.getByText('No Reported Issues!')).toBeInTheDocument();
    });
  });
});
