import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import WhitelistManager from '../components/WhitelistManager';
import { api } from '../services/api';

const mockMembers = [
  {
    name: 'Dad',
    phoneNumber: '919876543211',
    role: 'MEMBER',
    folderName: 'Dad',
    allowedServices: ['AADHAAR', 'PAN', 'DRIVING_LICENSE']
  }
];

vi.mock('../services/api', () => ({
  api: {
    fetchWhitelist: vi.fn(),
    updateUserServices: vi.fn().mockResolvedValue({ status: 'SUCCESS', statusCode: 200 }),
    addWhitelistMember: vi.fn().mockResolvedValue({ status: 'SUCCESS', statusCode: 200 }),
    removeWhitelistMember: vi.fn().mockResolvedValue({ status: 'SUCCESS', statusCode: 200 })
  }
}));

describe('WhitelistManager Component Unit & Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders registered member list and Allowed Services toggle badges', async () => {
    api.fetchWhitelist.mockResolvedValueOnce(mockMembers);

    render(<WhitelistManager activePersona={{ role: 'ADMIN' }} />);

    await waitFor(() => {
      expect(screen.getByText('Dad')).toBeInTheDocument();
      expect(screen.getByText('+919876543211')).toBeInTheDocument();
      expect(screen.getByText(/Aadhaar Card/)).toBeInTheDocument();
      expect(screen.getByText(/PAN Card/)).toBeInTheDocument();
    });
  });

  it('toggles a document service when clicked by Admin', async () => {
    api.fetchWhitelist.mockResolvedValueOnce(mockMembers);

    render(<WhitelistManager activePersona={{ role: 'ADMIN' }} />);

    await waitFor(() => {
      expect(screen.getByText(/Aadhaar Card/)).toBeInTheDocument();
    });

    const aadhaarBtn = screen.getByTitle(/Revoke Aadhaar Card/);
    fireEvent.click(aadhaarBtn);

    await waitFor(() => {
      expect(api.updateUserServices).toHaveBeenCalledWith(
        '919876543211',
        expect.not.arrayContaining(['AADHAAR'])
      );
    });
  });

  it('allows Admin to register a new family member via form', async () => {
    api.fetchWhitelist.mockResolvedValue(mockMembers);

    render(<WhitelistManager activePersona={{ role: 'ADMIN' }} />);

    const nameInput = screen.getByPlaceholderText(/e\.g\. Sister, Uncle/);
    const phoneInput = screen.getByPlaceholderText(/e\.g\. \+91 9876543215/);
    const submitBtn = screen.getByText(/Add to Whitelist/);

    fireEvent.change(nameInput, { target: { value: 'Sister' } });
    fireEvent.change(phoneInput, { target: { value: '+91 9876543215' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(api.addWhitelistMember).toHaveBeenCalledWith(expect.objectContaining({
        name: 'Sister',
        phoneNumber: '+91 9876543215',
        role: 'MEMBER'
      }));
    });
  });
});
